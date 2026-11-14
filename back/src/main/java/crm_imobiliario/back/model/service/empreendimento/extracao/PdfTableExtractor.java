package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/**
 * Extração de texto e tabelas de PDFs nativos (com camada de texto), por
 * código — sem OCR e sem IA generativa (§4.1 e §4.4 do spec).
 *
 * Estratégia (calibrada com documentos reais do CVCRM/Pride): lê a posição de
 * cada caractere da página, agrupa em "linhas" (bandas) por proximidade
 * vertical, encontra a linha "âncora" do cabeçalho (contém "bloco"/"torre" e
 * "unidade") e reúne as bandas vizinhas próximas dela — cabeçalhos reais têm
 * rótulos de uma linha (ex.: "SITUAÇÃO") e de duas linhas empilhadas (ex.:
 * "ÁREA" sobre "PRIVATIVA", "ATO (1x)" sobre a data) simultaneamente. Os
 * limites de coluna são calculados a partir de todos os caracteres desse
 * bloco de cabeçalho (não só da linha âncora), pelo maior salto de
 * espaçamento horizontal — o que naturalmente agrupa rótulos de duas linhas
 * na coluna correta sem depender de posição fixa. As linhas de dados
 * seguintes são então recortadas pelos mesmos limites, o que remonta
 * corretamente células com múltiplas palavras (ex.: "2Q - Garden",
 * "R$ 244.200,00").
 *
 * PDFs sem camada de texto (digitalizados) não são suportados nesta versão —
 * são sinalizados como tal para revisão manual, nunca processados como se
 * fossem confiáveis.
 */
@Component
public class PdfTableExtractor {

    private final UnidadeHeaderNormalizer headerNormalizer;

    public PdfTableExtractor(UnidadeHeaderNormalizer headerNormalizer) {
        this.headerNormalizer = headerNormalizer;
    }

    private static final Pattern IDENTIFICADOR_UNIDADE = Pattern.compile("^[A-Za-z]{1,4}\\d{1,3}[.\\-]\\d{1,4}$");
    private static final float TOLERANCIA_LINHA = 2.0f;
    private static final float DISTANCIA_MAX_BLOCO_CABECALHO = 20.0f;
    private static final float DISTANCIA_MAX_FRAGMENTO = 8.0f;

    //Guarda o resultado da extração: textos, tabelas, alertas e quantidade de páginas.
    public static class Resultado {//funciona como uma caixa que guarda tudo o que foi encontrado durante a extração.
        public String textoCompleto = "";
        /** Texto de cada página (índice 0 = página 1) — preserva a origem por página para rastreabilidade (§15 do spec). */
        public final List<String> textoPorPagina = new ArrayList<>();
        public final List<TabelaBruta> tabelas = new ArrayList<>();
        public final List<String> alertas = new ArrayList<>();
        public int totalPaginas;
    }

    //Método principal. Abre o PDF, verifica as páginas, extrai caracteres e identifica tabelas.
    public Resultado extrair(byte[] bytes, //o conteúdo do PDF convertido em bytes.
                            int maxPaginas //quantidade máxima de páginas que devem ser processadas.
                        ) throws IOException {
        Resultado resultado = new Resultado();
        try (PDDocument doc = Loader.loadPDF(bytes)) {//o PDFBox abre o arquivo para que o programa consiga acessar seu conteúdo.
            resultado.totalPaginas = doc.getNumberOfPages();//quantas páginas o documento possui.
            int limite = maxPaginas > 0 ? Math.min(maxPaginas, resultado.totalPaginas) : resultado.totalPaginas;
            //Se maxPaginas for maior que zero, utiliza o menor valor entre maxPaginas e o total de páginas. Caso contrário, processa todas as páginas.
            if (limite < resultado.totalPaginas) {
                //Se o PDF tiver 30 páginas, mas o sistema estiver configurado para processar somente 10, ele adiciona um alerta.
                resultado.alertas.add("Documento possui " + resultado.totalPaginas + " páginas; processadas apenas as primeiras " + limite + ".");
            }

            CharColetor coletor = new CharColetor();
            coletor.setStartPage(1);
            coletor.setEndPage(limite);
            coletor.writeText(doc, new StringWriter());
            extrairTextoPorPagina(doc, limite, resultado);
            //além de extrair o texto, ela registra informações sobre cada caractere
            //para tentar reconstruir as linhas e colunas da tabela.

            if (coletor.chars.isEmpty()) {//PDF digitalizado
                resultado.alertas.add("Não foi possível extrair texto deste PDF — pode ser um documento digitalizado (imagem). OCR não é suportado nesta versão; documento marcado para revisão manual.");
                return resultado;
            }

            List<Banda> bandas = agruparEmBandas(coletor.chars);//recebe todos os caracteres coletados e tenta agrupá-los em linhas visuais.
            resultado.tabelas.addAll(detectarTabelas(bandas));//recebe as bandas e procura aquelas que parecem representar cabeçalhos de tabelas.
        }
        return resultado;
    }

    //Extrai e armazena o texto separadamente para cada página.
    private void extrairTextoPorPagina(PDDocument doc, int limite, Resultado resultado) throws IOException {
        StringBuilder completo = new StringBuilder();//para juntar todo o texto extraído.
        for (int pagina = 1; pagina <= limite; pagina++) {//Vai percorrer as páginas do PDF.
            PDFTextStripper stripper = new PDFTextStripper();//Cria um extrator de texto do PDFBox.
            stripper.setStartPage(pagina);
            stripper.setEndPage(pagina);
            //Configura o extrator para trabalhar somente com a página atual.
            String texto = stripper.getText(doc);//Extrai o texto daquela página.
            resultado.textoPorPagina.add(texto);//Armazena o texto na lista de páginas.
            completo.append(texto);//Acrescenta o texto ao conteúdo completo.
        }
        resultado.textoCompleto = completo.toString();
    }

    // ------------------------------------------------------------------ bandas (linhas visuais)

    //	Guarda o caractere e suas coordenadas no PDF.
    private static class Caractere {
        int pagina;
        float x;
        float y;
        float largura;
        String glifo;//O caractere propriamente dito
    }

    //	Representa um agrupamento de caracteres de uma mesma linha visual.
    private static class Banda {
        int pagina;
        float y;
        List<Caractere> caracteres;
        String textoSimples; // concatenação simples em ordem X — usada só para classificar a banda
    }

    //	Agrupa caracteres que estão aproximadamente na mesma linha horizontal.
    private List<Banda> agruparEmBandas(List<Caractere> caracteres) {
        Map<Integer, List<Caractere>> porPagina = new LinkedHashMap<>();//Cria um mapa para separar os caracteres por página.
        for (Caractere c : caracteres) porPagina.computeIfAbsent(c.pagina, k -> new ArrayList<>()).add(c);
        //verifica se já existe uma lista para aquela página e adiciona

        List<Banda> bandas = new ArrayList<>();
        for (Map.Entry<Integer, List<Caractere>> entry : porPagina.entrySet()) {
            List<Caractere> pagina = new ArrayList<>(entry.getValue());
            pagina.sort(Comparator.comparing((Caractere c) -> c.y).thenComparing(c -> c.x));
            //organiza os caracteres para percorrer primeiro as linhas e, dentro delas, da esquerda para a direita.

            List<Caractere> atual = new ArrayList<>();
            float yBanda = 0;
            //Cria uma lista para armazenar os caracteres da banda atual.
            for (Caractere c : pagina) {
                if (!atual.isEmpty() && Math.abs(c.y - yBanda) > TOLERANCIA_LINHA) {
                    //Verifica se já existe uma banda sendo construída.
                    //Compara a posição Y do caractere com a posição Y da banda.
                    //Se a diferença for maior que 2.0, entende que começou outra linha.
                    bandas.add(construirBanda(entry.getKey(), yBanda, atual));
                    atual = new ArrayList<>();
                    //Finaliza a banda anterior.
                    //Cria uma nova banda.
                }
                if (atual.isEmpty()) yBanda = c.y;
                atual.add(c);
            }
            if (!atual.isEmpty()) bandas.add(construirBanda(entry.getKey(), yBanda, atual));//precisa adicionar a última banda:
        }
        return bandas;
    }

    //	Junta os caracteres de uma banda e monta seu texto.
    private Banda construirBanda(int pagina, float y, List<Caractere> caracteres) {
        List<Caractere> ordenados = new ArrayList<>(caracteres);
        ordenados.sort(Comparator.comparing(c -> c.x));
        //Cria uma cópia da lista e organiza os caracteres da esquerda para a direita.

        Banda b = new Banda();
        b.pagina = pagina;
        b.y = y;
        b.caracteres = ordenados;
        //Preenche as informações da banda.

        StringBuilder sb = new StringBuilder();
        for (Caractere c : ordenados) sb.append(c.glifo);
        b.textoSimples = sb.toString();
        //Percorre os caracteres e junta os símbolos em uma única String. Ex: ["B", "L", "O", "C", "O"]
        return b;
    }

    // ------------------------------------------------------------------ tabelas

    //Procura cabeçalhos e identifica possíveis tabelas no documento.
    private List<TabelaBruta> detectarTabelas(List<Banda> bandas) {
        List<TabelaBruta> tabelas = new ArrayList<>();
        int i = 0;
        //Cria uma lista de tabelas encontradas e começa a percorrer as bandas.
        while (i < bandas.size()) {//continua enquanto ainda houver bandas para analisar.
            //Quando encontra uma banda que parece ser cabeçalho, começa a tentativa de identificar uma tabela.
            if (pareceCabecalho(bandas.get(i).textoSimples)) {
                int inicioBloco = i;
                int fimBloco = i;
                //indicam onde começa e termina o bloco de cabeçalho.
                // expande para cima e para baixo enquanto a banda vizinha estiver perto verticalmente (mesma página)
                while (inicioBloco > 0
                        && bandas.get(inicioBloco - 1).pagina == bandas.get(i).pagina
                        && Math.abs(bandas.get(inicioBloco - 1).y - bandas.get(i).y) <= DISTANCIA_MAX_BLOCO_CABECALHO) {
                    //Enquanto Não estiver na primeira banda, A banda anterior pertencer à mesma página e A distância vertical for de até 20 pontos.
                    inicioBloco--;//O código retrocede uma posição.
                }
                while (fimBloco + 1 < bandas.size()
                        && bandas.get(fimBloco + 1).pagina == bandas.get(i).pagina
                        && Math.abs(bandas.get(fimBloco + 1).y - bandas.get(i).y) <= DISTANCIA_MAX_BLOCO_CABECALHO) {
                    //agora procura as bandas seguintes.
                    fimBloco++;
                }

                List<Caractere> todosDoBloco = new ArrayList<>();
                for (int k = inicioBloco; k <= fimBloco; k++) todosDoBloco.addAll(bandas.get(k).caracteres);

                List<Coluna> colunas = calcularColunas(todosDoBloco);
                //junta todos os caracteres do bloco de cabeçalho e envia para calcularColunas().
                //A ideia é descobrir quais regiões horizontais representam as colunas.

                TabelaBruta tabela = new TabelaBruta();
                tabela.pagina = bandas.get(i).pagina;
                for (Coluna c : colunas) tabela.cabecalhos.add(c.texto);

                int linhaOrigem = 0;
                TabelaBruta.Linha ultimaLinhaValida = null;
                float ultimaLinhaValidaY = Float.NaN;
                List<Banda> fragmentosPendentes = new ArrayList<>();
                int j = fimBloco + 1;
                for (; j < bandas.size(); j++) {
                    Banda candidata = bandas.get(j);
                    if (pareceCabecalho(candidata.textoSimples)) break; // nova tabela começa
                    List<String> celulas = bucketizar(candidata.caracteres, colunas);
                    if (linhaValida(celulas, colunas)) {
                        TabelaBruta.Linha l = new TabelaBruta.Linha();
                        l.celulas = celulas;
                        l.linhaOrigem = ++linhaOrigem;
                        // uma célula de valor (ex.: "R$") às vezes é renderizada em uma banda Y
                        // levemente diferente do resto da linha (fonte/baseline distinto) — funde
                        // fragmentos próximos vistos antes desta linha ser reconhecida
                        for (Banda pendente : fragmentosPendentes) {
                            if (Math.abs(pendente.y - candidata.y) <= DISTANCIA_MAX_FRAGMENTO) {
                                mesclarFragmento(l.celulas, bucketizar(pendente.caracteres, colunas));
                            }
                        }
                        fragmentosPendentes.clear();
                        tabela.linhas.add(l);
                        ultimaLinhaValida = l;
                        ultimaLinhaValidaY = candidata.y;
                    } else if (ultimaLinhaValida != null && Math.abs(candidata.y - ultimaLinhaValidaY) <= DISTANCIA_MAX_FRAGMENTO) {
                        // fragmento à direita/abaixo da última linha reconhecida — funde agora
                        mesclarFragmento(ultimaLinhaValida.celulas, celulas);
                    } else {
                        // pode ser um fragmento da PRÓXIMA linha (ainda não vista) — guarda para checar depois
                        fragmentosPendentes.add(candidata);
                        if (fragmentosPendentes.size() > 5) fragmentosPendentes.remove(0);
                    }
                }
                if (!tabela.linhas.isEmpty()) tabelas.add(tabela);
                i = j;
            } else {
                i++;
            }
        }
        return tabelas;
    }

    //Verifica se um texto parece ser um cabeçalho de tabela.
    private boolean pareceCabecalho(String texto) {
        String n = headerNormalizer.normalizar(texto);//normaliza o texto.
        boolean temIdentificacao = n.contains("bloco") || n.contains("torre");//Existe a palavra bloco ou torre?
        boolean temUnidade = n.contains("unidade") || n.contains("unid");//Existe a palavra unidade ou unid?
        return temIdentificacao && temUnidade;//Somente se as duas condições forem verdadeiras, retorna true.
    }

    /**
     * Completa {@code destino} com {@code fragmento}: preenche células vazias e, quando a célula já
     * tem conteúdo parcial (ex.: só o prefixo "R$" veio de um fragmento anterior), concatena — a
     * ordem de chegada dos fragmentos (antes/depois da linha principal) já reflete a ordem de leitura.
     */
    //Junta fragmentos de texto às células de uma linha existente.
    private void mesclarFragmento(List<String> destino, List<String> fragmento) {
        for (int k = 0; k < destino.size() && k < fragmento.size(); k++) {//Percorre as posições das duas listas.
            String frag = fragmento.get(k);
            if (frag.isBlank()) continue;//Ignora fragmentos vazios.
            destino.set(k, destino.get(k).isBlank() ? frag : destino.get(k) + frag);
            //Se a célula principal estiver vazia, coloca o fragmento nela.
            //Se já tiver conteúdo, acrescenta o fragmento ao final.
        }
    }

    //	Verifica se uma linha possui um identificador de unidade válido.
    private boolean linhaValida(List<String> celulas, List<Coluna> colunas) {//Esse método verifica se as células extraídas representam uma linha que pode ser considerada uma unidade.
        int idxUnidade = -1;//Começa considerando que não encontrou a coluna de unidade.
        for (int k = 0; k < colunas.size(); k++) {
            if (colunas.get(k).campo == CampoUnidade.UNIDADE) { idxUnidade = k; break; }
        }//Percorre as colunas até encontrar aquela que foi reconhecida como CampoUnidade.UNIDADE.
        if (idxUnidade < 0 || idxUnidade >= celulas.size()) return false;//Verifica se a coluna foi encontrada e se existe uma célula correspondente.
        String valor = celulas.get(idxUnidade).trim();
        return IDENTIFICADOR_UNIDADE.matcher(valor).matches();
        //Pega o conteúdo da célula de unidade, remove espaços das extremidades e verifica se ele corresponde à Regex que vimos anteriormente.
    }

    //	Guarda informações de uma coluna, como cabeçalho e limites horizontais.
    private static class Coluna {
        String texto;
        CampoUnidade campo;
        float inicio;
        float fim;
    }

    /**
     * Calcula colunas a partir de TODOS os caracteres do bloco de cabeçalho
     * (podendo vir de mais de uma banda/sub-linha empilhada). Primeiro agrupa
     * por proximidade horizontal (maior salto adaptativo de espaçamento) para
     * achar as faixas de X de cada coluna; depois monta o rótulo de cada
     * coluna reunindo os caracteres daquela faixa em ordem (linha, depois X),
     * inserindo um espaço sintético só na troca de sub-linha (dentro de uma
     * mesma sub-linha os espaços já são caracteres reais do PDF).
     */

    //Identifica as colunas analisando a posição dos caracteres e os espaços entre eles.
    private List<Coluna> calcularColunas(List<Caractere> caracteresDoBloco) {
        List<Caractere> ordenadosPorX = new ArrayList<>(caracteresDoBloco);
        ordenadosPorX.sort(Comparator.comparing(c -> c.x));
        //Organiza todos os caracteres pela posição horizontal. Porque consegue descobrir onde existe espaço entre os caracteres.

        List<Float> gaps = new ArrayList<>();
        for (int k = 0; k < ordenadosPorX.size() - 1; k++) {
            float fimAtual = ordenadosPorX.get(k).x + ordenadosPorX.get(k).largura;
            float inicioProximo = ordenadosPorX.get(k + 1).x;
            gaps.add(Math.max(0f, inicioProximo - fimAtual));
        }
        //calcula o espaço entre um caractere e o próximo.
        //Entre letras da mesma palavra, geralmente temos espaços pequenos. Entre colunas, geralmente temos espaços maiores. O gap representa esse espaço.
        //O objetivo é encontrar um limite que ajude a separar as colunas.

        float limiar = calcularLimiarDeColuna(gaps);//será responsável por determinar qual distância representa uma possível separação entre colunas.

        List<List<Caractere>> grupos = new ArrayList<>();
        List<Caractere> atual = new ArrayList<>();
        for (int k = 0; k < ordenadosPorX.size(); k++) {
            atual.add(ordenadosPorX.get(k));
            boolean fimDeColuna = k == ordenadosPorX.size() - 1 || gaps.get(k) > limiar;
            if (fimDeColuna) { grupos.add(atual); atual = new ArrayList<>(); }
        }
        //O código vai adicionando caracteres ao grupo atual.
        //Quando encontra um espaço maior que o limite, finaliza o grupo e começa outro.
        //Dessa forma, cada grupo representa uma possível coluna.

        List<Coluna> colunas = new ArrayList<>();
        for (List<Caractere> grupo : grupos) {
            Coluna c = new Coluna();//Cria uma coluna.
            c.inicio = grupo.stream().map(ch -> ch.x).min(Float::compareTo).orElse(0f);//Descobre sua posição inicial.
            c.fim = grupo.stream().map(ch -> ch.x + ch.largura).max(Float::compareTo).orElse(0f);//Descobre sua posição final.
            c.texto = montarRotulo(grupo);//Monta o texto do cabeçalho.
            headerNormalizer.reconhecer(c.texto).ifPresent(r -> c.campo = r.campo);//Tenta reconhecer a qual campo do sistema ele corresponde.
            colunas.add(c);//Adiciona a coluna à lista.
        }
        colunas.sort(Comparator.comparing(c -> c.inicio));//ordenar as colunas:

        
        float[] meio = new float[Math.max(0, colunas.size() - 1)];
        for (int k = 0; k < meio.length; k++) {
            meio[k] = (colunas.get(k).fim + colunas.get(k + 1).inicio) / 2f;
        }
        //O código calcula o ponto médio entre uma coluna e a seguinte.
        //EX: Coluna BLOCO termina em X = 100
        //    Coluna UNIDADE começa em X = 120
        //Ponto médio: (100+120)/2 = 110 Isso evita deixar espaços sem classificação entre as colunas.

        for (int k = 0; k < colunas.size(); k++) {
            if (k > 0) colunas.get(k).inicio = meio[k - 1];
            if (k < colunas.size() - 1) colunas.get(k).fim = meio[k];
        }
        if (!colunas.isEmpty()) {
            colunas.get(0).inicio = Float.NEGATIVE_INFINITY;
            colunas.get(colunas.size() - 1).fim = Float.POSITIVE_INFINITY;
        }
        //significa que qualquer caractere antes da primeira fronteira ou depois da última fronteira também poderá ser associado a uma coluna.
        return colunas;
    }

    /** Reúne os caracteres de um grupo (possivelmente de sub-linhas empilhadas) em ordem (Y, X). */
    //Junta os caracteres de uma coluna para formar o nome do cabeçalho.
    private String montarRotulo(List<Caractere> grupo) {
        List<Caractere> ordenado = new ArrayList<>(grupo);
        ordenado.sort(Comparator.comparing((Caractere c) -> c.y).thenComparing(c -> c.x));//ordena por posição Y e depois por X
        StringBuilder sb = new StringBuilder();
        Float yAnterior = null;
        //Cria o texto que será montado e guarda a posição vertical anterior.
        for (Caractere c : ordenado) {
            if (yAnterior != null && Math.abs(c.y - yAnterior) > TOLERANCIA_LINHA && sb.length() > 0
                    && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
            //Se o caractere atual estiver em outra sublinha, acrescenta um espaço, desde que ainda não exista um espaço no final. Isso ajuda a montar cabeçalhos empilhados.
            sb.append(c.glifo);
            yAnterior = c.y;
            //Adiciona o caractere e atualiza a posição vertical.
        }
        return sb.toString().trim().replaceAll("\\s+", " ");//Remove espaços no início e no fim e substitui sequências de espaços por apenas um.
    }

    /** Maior salto entre espaçamentos ordenados — separa espaço interno de palavra de separação entre colunas. */
    //	Calcula o limite de distância utilizado para separar grupos de caracteres em colunas.
    private float calcularLimiarDeColuna(List<Float> gaps) {
        if (gaps.isEmpty()) return Float.MAX_VALUE;
        List<Float> ordenados = new ArrayList<>(gaps);
        Collections.sort(ordenados);
        int melhorIndice = -1;
        float maiorSalto = -1;
        for (int k = 0; k < ordenados.size() - 1; k++) {
            float salto = ordenados.get(k + 1) - ordenados.get(k);
            if (salto > maiorSalto) { maiorSalto = salto; melhorIndice = k; }
        }
        if (melhorIndice < 0) return Float.MAX_VALUE;
        float mediana = ordenados.get(ordenados.size() / 2);
        if (maiorSalto < Math.max(mediana, 3.0f)) return Float.MAX_VALUE;//impede que o algoritmo divida os caracteres em grupos por diferenças pouco relevantes.
        return (ordenados.get(melhorIndice) + ordenados.get(melhorIndice + 1)) / 2f;
    }

    //Distribui os caracteres encontrados nas respectivas células da tabela.
    private List<String> bucketizar(List<Caractere> caracteres, List<Coluna> colunas) {//O método cria uma célula para cada coluna.
        List<StringBuilder> celulas = new ArrayList<>();
        for (int k = 0; k < colunas.size(); k++) celulas.add(new StringBuilder());
        List<Caractere> ordenados = new ArrayList<>(caracteres);
        ordenados.sort(Comparator.comparing(c -> c.x));//ordena os caracteres pela posição X:
        for (Caractere c : ordenados) {//percorre cada caractere
            int idx = indiceDaColuna(c.x, colunas);
            if (idx >= 0) celulas.get(idx).append(c.glifo);
        }//O método indiceDaColuna() descobre em qual coluna o caractere deve entrar.
        List<String> out = new ArrayList<>();
        for (StringBuilder sb : celulas) out.add(sb.toString().trim());
        return out;
        //Converte cada célula em String e devolve uma lista.
    }

    //Identifica a coluna à qual determinado caractere pertence.
    private int indiceDaColuna(float x, List<Coluna> colunas) {
        for (int k = 0; k < colunas.size(); k++) {
            if (x >= colunas.get(k).inicio && x < colunas.get(k).fim) return k;
        }
        return colunas.isEmpty() ? -1 : colunas.size() - 1;
    }

    //	Personaliza o PDFBox para capturar caracteres e suas posições.
    private static class CharColetor extends PDFTextStripper {
        final List<Caractere> chars = new ArrayList<>();
        int paginaAtual = 0;

        CharColetor() throws IOException { super(); }

        @Override
        protected void startPage(PDPage page) throws IOException {
            paginaAtual++;
            super.startPage(page);
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            Caractere c = new Caractere();
            c.pagina = paginaAtual;
            c.x = text.getX();
            c.y = text.getY();
            c.largura = text.getWidth();
            c.glifo = text.getUnicode();
            chars.add(c);
            super.processTextPosition(text);
        }
    }
}

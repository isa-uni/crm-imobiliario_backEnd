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

    public static class Resultado {
        public String textoCompleto = "";
        /** Texto de cada página (índice 0 = página 1) — preserva a origem por página para rastreabilidade (§15 do spec). */
        public final List<String> textoPorPagina = new ArrayList<>();
        public final List<TabelaBruta> tabelas = new ArrayList<>();
        public final List<String> alertas = new ArrayList<>();
        public int totalPaginas;
    }

    public Resultado extrair(byte[] bytes, int maxPaginas) throws IOException {
        Resultado resultado = new Resultado();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            resultado.totalPaginas = doc.getNumberOfPages();
            int limite = maxPaginas > 0 ? Math.min(maxPaginas, resultado.totalPaginas) : resultado.totalPaginas;
            if (limite < resultado.totalPaginas) {
                resultado.alertas.add("Documento possui " + resultado.totalPaginas + " páginas; processadas apenas as primeiras " + limite + ".");
            }

            CharColetor coletor = new CharColetor();
            coletor.setStartPage(1);
            coletor.setEndPage(limite);
            coletor.writeText(doc, new StringWriter());
            extrairTextoPorPagina(doc, limite, resultado);

            if (coletor.chars.isEmpty()) {
                resultado.alertas.add("Não foi possível extrair texto deste PDF — pode ser um documento digitalizado (imagem). OCR não é suportado nesta versão; documento marcado para revisão manual.");
                return resultado;
            }

            List<Banda> bandas = agruparEmBandas(coletor.chars);
            resultado.tabelas.addAll(detectarTabelas(bandas));
        }
        return resultado;
    }

    private void extrairTextoPorPagina(PDDocument doc, int limite, Resultado resultado) throws IOException {
        StringBuilder completo = new StringBuilder();
        for (int pagina = 1; pagina <= limite; pagina++) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pagina);
            stripper.setEndPage(pagina);
            String texto = stripper.getText(doc);
            resultado.textoPorPagina.add(texto);
            completo.append(texto);
        }
        resultado.textoCompleto = completo.toString();
    }

    // ------------------------------------------------------------------ bandas (linhas visuais)

    private static class Caractere {
        int pagina;
        float x;
        float y;
        float largura;
        String glifo;
    }

    private static class Banda {
        int pagina;
        float y;
        List<Caractere> caracteres;
        String textoSimples; // concatenação simples em ordem X — usada só para classificar a banda
    }

    private List<Banda> agruparEmBandas(List<Caractere> caracteres) {
        Map<Integer, List<Caractere>> porPagina = new LinkedHashMap<>();
        for (Caractere c : caracteres) porPagina.computeIfAbsent(c.pagina, k -> new ArrayList<>()).add(c);

        List<Banda> bandas = new ArrayList<>();
        for (Map.Entry<Integer, List<Caractere>> entry : porPagina.entrySet()) {
            List<Caractere> pagina = new ArrayList<>(entry.getValue());
            pagina.sort(Comparator.comparing((Caractere c) -> c.y).thenComparing(c -> c.x));

            List<Caractere> atual = new ArrayList<>();
            float yBanda = 0;
            for (Caractere c : pagina) {
                if (!atual.isEmpty() && Math.abs(c.y - yBanda) > TOLERANCIA_LINHA) {
                    bandas.add(construirBanda(entry.getKey(), yBanda, atual));
                    atual = new ArrayList<>();
                }
                if (atual.isEmpty()) yBanda = c.y;
                atual.add(c);
            }
            if (!atual.isEmpty()) bandas.add(construirBanda(entry.getKey(), yBanda, atual));
        }
        return bandas;
    }

    private Banda construirBanda(int pagina, float y, List<Caractere> caracteres) {
        List<Caractere> ordenados = new ArrayList<>(caracteres);
        ordenados.sort(Comparator.comparing(c -> c.x));
        Banda b = new Banda();
        b.pagina = pagina;
        b.y = y;
        b.caracteres = ordenados;
        StringBuilder sb = new StringBuilder();
        for (Caractere c : ordenados) sb.append(c.glifo);
        b.textoSimples = sb.toString();
        return b;
    }

    // ------------------------------------------------------------------ tabelas

    private List<TabelaBruta> detectarTabelas(List<Banda> bandas) {
        List<TabelaBruta> tabelas = new ArrayList<>();
        int i = 0;
        while (i < bandas.size()) {
            if (pareceCabecalho(bandas.get(i).textoSimples)) {
                int inicioBloco = i;
                int fimBloco = i;
                // expande para cima e para baixo enquanto a banda vizinha estiver perto verticalmente (mesma página)
                while (inicioBloco > 0
                        && bandas.get(inicioBloco - 1).pagina == bandas.get(i).pagina
                        && Math.abs(bandas.get(inicioBloco - 1).y - bandas.get(i).y) <= DISTANCIA_MAX_BLOCO_CABECALHO) {
                    inicioBloco--;
                }
                while (fimBloco + 1 < bandas.size()
                        && bandas.get(fimBloco + 1).pagina == bandas.get(i).pagina
                        && Math.abs(bandas.get(fimBloco + 1).y - bandas.get(i).y) <= DISTANCIA_MAX_BLOCO_CABECALHO) {
                    fimBloco++;
                }

                List<Caractere> todosDoBloco = new ArrayList<>();
                for (int k = inicioBloco; k <= fimBloco; k++) todosDoBloco.addAll(bandas.get(k).caracteres);

                List<Coluna> colunas = calcularColunas(todosDoBloco);
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

    private boolean pareceCabecalho(String texto) {
        String n = headerNormalizer.normalizar(texto);
        boolean temIdentificacao = n.contains("bloco") || n.contains("torre");
        boolean temUnidade = n.contains("unidade") || n.contains("unid");
        return temIdentificacao && temUnidade;
    }

    /**
     * Completa {@code destino} com {@code fragmento}: preenche células vazias e, quando a célula já
     * tem conteúdo parcial (ex.: só o prefixo "R$" veio de um fragmento anterior), concatena — a
     * ordem de chegada dos fragmentos (antes/depois da linha principal) já reflete a ordem de leitura.
     */
    private void mesclarFragmento(List<String> destino, List<String> fragmento) {
        for (int k = 0; k < destino.size() && k < fragmento.size(); k++) {
            String frag = fragmento.get(k);
            if (frag.isBlank()) continue;
            destino.set(k, destino.get(k).isBlank() ? frag : destino.get(k) + frag);
        }
    }

    private boolean linhaValida(List<String> celulas, List<Coluna> colunas) {
        int idxUnidade = -1;
        for (int k = 0; k < colunas.size(); k++) {
            if (colunas.get(k).campo == CampoUnidade.UNIDADE) { idxUnidade = k; break; }
        }
        if (idxUnidade < 0 || idxUnidade >= celulas.size()) return false;
        String valor = celulas.get(idxUnidade).trim();
        return IDENTIFICADOR_UNIDADE.matcher(valor).matches();
    }

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
    private List<Coluna> calcularColunas(List<Caractere> caracteresDoBloco) {
        List<Caractere> ordenadosPorX = new ArrayList<>(caracteresDoBloco);
        ordenadosPorX.sort(Comparator.comparing(c -> c.x));

        List<Float> gaps = new ArrayList<>();
        for (int k = 0; k < ordenadosPorX.size() - 1; k++) {
            float fimAtual = ordenadosPorX.get(k).x + ordenadosPorX.get(k).largura;
            float inicioProximo = ordenadosPorX.get(k + 1).x;
            gaps.add(Math.max(0f, inicioProximo - fimAtual));
        }
        float limiar = calcularLimiarDeColuna(gaps);

        List<List<Caractere>> grupos = new ArrayList<>();
        List<Caractere> atual = new ArrayList<>();
        for (int k = 0; k < ordenadosPorX.size(); k++) {
            atual.add(ordenadosPorX.get(k));
            boolean fimDeColuna = k == ordenadosPorX.size() - 1 || gaps.get(k) > limiar;
            if (fimDeColuna) { grupos.add(atual); atual = new ArrayList<>(); }
        }

        List<Coluna> colunas = new ArrayList<>();
        for (List<Caractere> grupo : grupos) {
            Coluna c = new Coluna();
            c.inicio = grupo.stream().map(ch -> ch.x).min(Float::compareTo).orElse(0f);
            c.fim = grupo.stream().map(ch -> ch.x + ch.largura).max(Float::compareTo).orElse(0f);
            c.texto = montarRotulo(grupo);
            headerNormalizer.reconhecer(c.texto).ifPresent(r -> c.campo = r.campo);
            colunas.add(c);
        }
        colunas.sort(Comparator.comparing(c -> c.inicio));

        // limites finais = ponto médio entre o fim (original) de uma coluna e o início (original) da
        // próxima, calculados todos de uma vez a partir dos valores originais — nunca a partir de um
        // limite já reescrito, senão fim[k] e início[k+1] divergem e sobra uma fresta entre colunas
        // onde um caractere de fronteira (ex.: o "R" de "R$") cai fora de qualquer coluna.
        float[] meio = new float[Math.max(0, colunas.size() - 1)];
        for (int k = 0; k < meio.length; k++) {
            meio[k] = (colunas.get(k).fim + colunas.get(k + 1).inicio) / 2f;
        }
        for (int k = 0; k < colunas.size(); k++) {
            if (k > 0) colunas.get(k).inicio = meio[k - 1];
            if (k < colunas.size() - 1) colunas.get(k).fim = meio[k];
        }
        if (!colunas.isEmpty()) {
            colunas.get(0).inicio = Float.NEGATIVE_INFINITY;
            colunas.get(colunas.size() - 1).fim = Float.POSITIVE_INFINITY;
        }
        return colunas;
    }

    /** Reúne os caracteres de um grupo (possivelmente de sub-linhas empilhadas) em ordem (Y, X). */
    private String montarRotulo(List<Caractere> grupo) {
        List<Caractere> ordenado = new ArrayList<>(grupo);
        ordenado.sort(Comparator.comparing((Caractere c) -> c.y).thenComparing(c -> c.x));
        StringBuilder sb = new StringBuilder();
        Float yAnterior = null;
        for (Caractere c : ordenado) {
            if (yAnterior != null && Math.abs(c.y - yAnterior) > TOLERANCIA_LINHA && sb.length() > 0
                    && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
            sb.append(c.glifo);
            yAnterior = c.y;
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /** Maior salto entre espaçamentos ordenados — separa espaço interno de palavra de separação entre colunas. */
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
        if (maiorSalto < Math.max(mediana, 3.0f)) return Float.MAX_VALUE;
        return (ordenados.get(melhorIndice) + ordenados.get(melhorIndice + 1)) / 2f;
    }

    private List<String> bucketizar(List<Caractere> caracteres, List<Coluna> colunas) {
        List<StringBuilder> celulas = new ArrayList<>();
        for (int k = 0; k < colunas.size(); k++) celulas.add(new StringBuilder());
        List<Caractere> ordenados = new ArrayList<>(caracteres);
        ordenados.sort(Comparator.comparing(c -> c.x));
        for (Caractere c : ordenados) {
            int idx = indiceDaColuna(c.x, colunas);
            if (idx >= 0) celulas.get(idx).append(c.glifo);
        }
        List<String> out = new ArrayList<>();
        for (StringBuilder sb : celulas) out.add(sb.toString().trim());
        return out;
    }

    private int indiceDaColuna(float x, List<Coluna> colunas) {
        for (int k = 0; k < colunas.size(); k++) {
            if (x >= colunas.get(k).inicio && x < colunas.get(k).fim) return k;
        }
        return colunas.isEmpty() ? -1 : colunas.size() - 1;
    }

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

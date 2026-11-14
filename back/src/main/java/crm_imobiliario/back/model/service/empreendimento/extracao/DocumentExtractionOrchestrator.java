package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;
import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador de extração determinística: identifica o formato de cada
 * documento, encaminha para o extrator adequado, normaliza cabeçalhos e
 * monta o resultado estruturado para revisão — sem IA generativa (§5 do spec).
 *
 * Extração de unidades (tabelas de preço) é a parte confiável e testada desta
 * versão. Extração de campos gerais do empreendimento (identificação,
 * localização, características) a partir de texto livre é heurística e
 * conservadora: só preenche o que reconhece com um padrão explícito, marca
 * tudo mais como não encontrado para preenchimento manual — nunca inventa.
 */
@Slf4j
@Service("documentExtractionOrchestrator")
@RequiredArgsConstructor
public class DocumentExtractionOrchestrator implements EmpreendimentoExtractionService {

    private final PdfTableExtractor pdfTableExtractor; //Extrair tabelas e textos de PDFs.
    private final SpreadsheetTableExtractor spreadsheetTableExtractor; //Ler tabelas de planilhas.
    private final WordTextExtractor wordTextExtractor; //Ler textos e tabelas de documentos Word.
    private final UnidadeTableMapper unidadeTableMapper; //Transformar tabelas brutas em unidades estruturadas.
    private final MemorialTextExtractor memorialTextExtractor; //Procurar informações gerais no texto.

    @Value("${crm.extracao.max-pdf-pages:40}")
    private int maxPdfPages;

    private static final Set<String> EXT_PDF = Set.of("pdf");
    private static final Set<String> EXT_PLANILHA = Set.of("xlsx", "xls", "csv");
    private static final Set<String> EXT_WORD = Set.of("doc", "docx");

    // Classificação de "documento tipo Book" (§2 do spec de correção de endereço): o sistema não tem
    // uma categoria de documento cadastrada, então usa o nome do arquivo como sinal — convenção real já
    // usada pela equipe (ex.: "C-PRD - Book - London Plaza (Digital).pdf" vs.
    // "LONDON_PLAZA_2609_Tabela_de_precos_London_Plaza_set_26.pdf"). Aplicado só a PDFs: books de tabela
    // de preços (sem "book" no nome) e planilhas/Word não entram nessa regra.
    private static final Pattern NOME_BOOK = Pattern.compile("(?i)\\bbook\\b");

    private boolean isBook(EmpreendimentoDocumento doc) {
        String nome = doc.getNomeOriginal();
        if (nome == null) return false;
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return NOME_BOOK.matcher(semAcento).find();
    }

    @Override
    public ExtractionResult extract(List<EmpreendimentoDocumento> documentos) {
        List<EmpreendimentoFonteData> fontes = new ArrayList<>();//origem das informações encontradas.
        List<String> alertas = new ArrayList<>();//mensagens de atenção.
        List<UnidadeExtraida> todasUnidades = new ArrayList<>();//unidades encontradas nos documentos.
        Map<String, Object> camposGerais = new LinkedHashMap<>();//dados gerais do empreendimento.
        //quatro estruturas vazias para guardar os resultados.

        for (EmpreendimentoDocumento doc : documentos) {//Percorrendo os documentos
            String ext = doc.getTipo() != null ? doc.getTipo().toLowerCase(Locale.ROOT) : "";//Identificando a extensão
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(doc.getCaminho()));//pega o caminho registrado no banco e lê o arquivo que está armazenado no servidor.
                if (EXT_PDF.contains(ext)) {//se for pdf
                    processarPdf(doc, bytes, todasUnidades, camposGerais, alertas);
                } else if (EXT_PLANILHA.contains(ext)) {//planilha
                    processarPlanilha(doc, bytes, ext, todasUnidades, alertas);
                } else if (EXT_WORD.contains(ext)) {//word
                    processarWord(doc, bytes, ext, todasUnidades, camposGerais, alertas);
                } else {
                    alertas.add("Formato \"" + ext + "\" (" + doc.getNomeOriginal() + ") não é suportado para extração automática — envie os dados manualmente.");
                    marcarRevisao(doc);
                }
            } catch (Exception e) {
                log.warn("Falha ao processar documento {} ({}): {}", doc.getId(), doc.getNomeOriginal(), e.getMessage(), e);
                alertas.add("Não foi possível ler o arquivo \"" + doc.getNomeOriginal() + "\": " + e.getMessage());
                //O sistema registra um alerta
                marcarRevisao(doc);//O documento é marcado para revisão.
            }
        }

        Map<String, List<UnidadeExtraida>> porUnidade = new LinkedHashMap<>();//agrupar as ocorrencias por unidade
        for (UnidadeExtraida u : todasUnidades) {
            porUnidade.computeIfAbsent(u.chaveIdentidade(), k -> new ArrayList<>()).add(u);//identificação da unidade.
            //Se essa chave ainda não existir, crie uma lista vazia para ela e adiciona no map
            //EX: porUnidade
            //        │
            //        ├── apartamento-101
            //        │   ├── Ocorrência do Memorial.pdf
            //        │   └── Ocorrência da planilha
            //        │
            //        └── apartamento-102
            //            └── Ocorrência da planilha
        }
        //Ele está agrupando as ocorrências para conseguir reunir informações de diferentes documentos.

        //Criando o JSON das unidades
        List<Map<String, Object>> unidadesJson = new ArrayList<>();
        for (Map.Entry<String, List<UnidadeExtraida>> entry : porUnidade.entrySet()) {//percorre cada grupo de unidades.
            Map<String, Object> unidadeJson = new LinkedHashMap<>();
            unidadeJson.put("chave", entry.getKey());
            //Cria um mapa vazio e adiciona a chave de identificação. Ex:  "chave": "apartamento-101"
           
            UnidadeExtraida primeiraOcorrencia = entry.getValue().get(0);//Pega a primeira ocorrência da lista.
            unidadeJson.put("documentoOrigemId", primeiraOcorrencia.documentoId);
            //Qual documento originou a primeira ocorrência.
            unidadeJson.put("linhaOrigem", primeiraOcorrencia.linhaOrigem);
            //Qual linha da tabela originou essa unidade.

            //EX:{
            //    "chave": "apartamento-101",
            //    "documentoOrigemId": 50,
            //    "linhaOrigem": 15
            //   }

            for (CampoUnidade campo : CampoUnidade.values()) {//Percorrendo os campos de cada unidade
               //percorre todos os valores definidos no enum CampoUnidade.

                CampoEvidencia primeira = null;//variável que começará vazia.
                for (UnidadeExtraida u : entry.getValue()) {//todas as ocorrências daquela unidade.(documentos encontradas)
                    CampoEvidencia ev = u.campos.get(campo);//ão entendi
                    //consulta o mapa campos da ocorrência atual e procura o campo que está sendo analisado.
                    if (ev == null || ev.valor == null) continue;//Ignorando valores vazios
                    if (primeira == null) primeira = ev;//verifica se ainda não encontrou nenhum valor para aquele campo.
                    //está escolhendo o primeiro valor não nulo encontrado, conforme a ordem das ocorrências.

                    EmpreendimentoFonteData f = new EmpreendimentoFonteData();
                    f.campo = "unidades[" + entry.getKey() + "]." + campo.name();
                    f.valorExtraido = ev.valor;
                    f.confianca = ev.confianca;
                    f.pagina = ev.pagina;
                    f.trecho = ev.trechoOriginal;
                    f.documentoNome = ev.documentoNome;
                    f.documentoId = u.documentoId;
                    fontes.add(f);

                    //registra as fontes de todas as ocorrências que possuem valor.
                }
                if (primeira != null) unidadeJson.put(campo.name(), campoJson(primeira));
                //Se encontrou algum valor, adiciona esse campo ao JSON.
            }
            for (UnidadeExtraida u : entry.getValue()) alertas.addAll(u.alertas);
            //percorre as ocorrências daquela unidade e adiciona todos os alertas encontrados.
            unidadesJson.add(unidadeJson);
        }
        camposGerais.put("unidades", unidadesJson);//adiciona nele uma chave chamada unidades.

        for (Map.Entry<String, Object> topo : camposGerais.entrySet()) {
            if ("unidades".equals(topo.getKey())) continue;//ignora para não duplicar
            coletarFontes(topo.getValue(), topo.getKey(), fontes);
        }

        for (String alerta : alertas) {
            EmpreendimentoFonteData f = new EmpreendimentoFonteData();
            f.campo = "_alerta";
            f.valorExtraido = alerta;
            f.confianca = 100;
            f.documentoNome = "sistema";
            fontes.add(f);
        }//Cada alerta é transformado em uma fonte especial.

        ExtractionResult r = new ExtractionResult();
        r.resultadoJson = camposGerais;//Todos os campos gerais e unidades extraídas
        r.fontes = fontes;//Origem das informações encontradas e alertas
        r.modelo = "regras-deterministicas";
        r.avisosTruncamento = alertas;//Lista de alertas
        return r;
    }

    //Coordena a extração de um PDF: identifica tabelas, mapeia unidades, extrai textos e registra alertas
    private void processarPdf(EmpreendimentoDocumento doc, //Dados do documento, como nome, ID e caminho do arquivo.
                                byte[] bytes, //Conteúdo do PDF em formato de bytes.
                                List<UnidadeExtraida> todasUnidades,//Lista onde serão armazenados os apartamentos encontrados.
                                Map<String, Object> camposGerais, //Mapa onde serão armazenados dados gerais do empreendimento.
                                List<String> alertas //	Lista de problemas ou avisos encontrados durante a extração.
                            ) throws IOException {
        PdfTableExtractor.Resultado extraido = pdfTableExtractor.extrair(bytes, maxPdfPages);
        //responsável por tentar identificar e extrair as tabelas existentes no PDF.

        alertas.addAll(prefixarAlertas(extraido.alertas, doc.getNomeOriginal()));
        //recebe os alertas e o nome do arquivo e adiciona todos esses alertas à lista geral.

        for (TabelaBruta tabela : extraido.tabelas) {
            UnidadeTableMapper.Resultado mapeado = unidadeTableMapper.mapear(tabela, doc.getId(), doc.getNomeOriginal());
            todasUnidades.addAll(mapeado.unidades);
            alertas.addAll(prefixarAlertas(mapeado.alertas, doc.getNomeOriginal()));//Adiciona os problemas encontrados durante o mapeamento.
        }
        //O mapper tenta transformar essa tabela bruta em informações reconhecidas pelo sistema e adiciona todas as unidades identificadas à lista geral.


        // Extrair textos que não estão em tabelas
        //descobrir quais páginas do PDF possuem tabelas.
        java.util.Set<Integer> paginasComTabela = extraido.tabelas.stream()
                .map(t -> t.pagina)//Pega somente o número da página de cada tabela.
                .filter(java.util.Objects::nonNull)//Remove valores nulos.
                .collect(java.util.stream.Collectors.toSet());//removendo duplicidades.

        //o sistema não quer processar essas páginas novamente como se fossem textos livres.

        int totalPaginas = extraido.textoPorPagina.size();//total de paginas
        boolean isBook = isBook(doc);//descobrir se aquele documento é um Book.
        if (isBook) {
            //Quando o documento for um Book, a última página não poderá ser utilizada como fonte do endereço do empreendimento.
            log.info("Documento \"{}\" classificado como Book. Total de páginas: {}. Página {} ignorada para "
                            + "extração do endereço institucional do empreendimento. Páginas elegíveis para endereço: 1 a {}.",
                    doc.getNomeOriginal(), totalPaginas, totalPaginas, Math.max(0, totalPaginas - 1));
        }
        for (int i = 0; i < totalPaginas; i++) {//Percorrer as páginas e extrair os textos
            int paginaAtual = i + 1;//transforma o índice da lista no número real da página.
            if (paginasComTabela.contains(paginaAtual)) continue;//Essa página está dentro da lista de páginas que possuem tabela?Se sim, pula para a proxima

            String textoDaPagina = extraido.textoPorPagina.get(i);//Recuperar o texto da página
            if (textoDaPagina == null || textoDaPagina.isBlank()) continue;//Verificar se existe texto
            // A página pode fornecer o endereço se o documento não for um Book OU se for uma página anterior à última.
            boolean elegivelParaEndereco = !isBook || paginaAtual < totalPaginas;//Definir se a página pode fornecer o endereço
            memorialTextExtractor.extrair(textoDaPagina, doc, paginaAtual, elegivelParaEndereco, camposGerais, alertas);
            //trabalha com textos livres.
        }
        if (isBook) extrairSecoesDoBookPelaPosicao(doc, extraido.textoPorPagina, paginasComTabela, camposGerais);
        //tenta encontrar informações do Book considerando a posição em que elas aparecem, mesmo quando não estão identificadas por títulos específicos.
    }   

    // "Final 01" / "FINAL 03" — rótulos dos apartamentos no mapa geral dos blocos (implantação)
    private static final Pattern FINAL_APARTAMENTO = Pattern.compile("(?i)\\bfinal\\s*0\\d");
    
    private static final int MAX_PAGINAS_AREAS_COMUNS = 4;
    //procura áreas comuns somente nas quatro primeiras páginas do documento.

    /**
      Procura informações de áreas comuns e diferenciais do empreendimento com base na posição das páginas do Book.
     */
    private void extrairSecoesDoBookPelaPosicao(EmpreendimentoDocumento doc, List<String> paginas,
                                                 Set<Integer> paginasComTabela, Map<String, Object> camposGerais) {
        for (int i = 0; i < Math.min(MAX_PAGINAS_AREAS_COMUNS, paginas.size()) && !camposGerais.containsKey("areas_comuns"); i++) {
            //Continue procurando enquanto ainda não existir o campo areas_comuns no mapa.
            String texto = paginas.get(i);//Recupera o texto da página.
            if (texto != null && !paginasComTabela.contains(i + 1)) memorialTextExtractor.extrairAreasComunsSemRotulo(texto, doc, i + 1, camposGerais);
            //método tenta extrair as áreas comuns mesmo sem encontrar um título ou rótulo específico.
        }
        if (camposGerais.containsKey("diferenciais")) return;//Se o campo diferenciais já foi encontrado, o método termina.
        for (int i = 0; i < paginas.size(); i++) {//começa a procurar a página do mapa geral dos blocos.
            String texto = paginas.get(i);
            //percorre as páginas procurando aquela que contenha pelo menos seis ocorrências de rótulos como:'FINAL 01', 'FINAL 02', 'FINAL 03'
            if (texto == null || FINAL_APARTAMENTO.matcher(texto).results().count() < 6) continue;
            // a página de diferenciais é a seguinte ao mapa dos blocos (às vezes a próxima vem só com imagem)

            for (int j = i + 1; j <= Math.min(i + 2, paginas.size() - 1) && !camposGerais.containsKey("diferenciais"); j++) {
                //Depois de encontrar uma possível página com o mapa dos blocos, o sistema procura os diferenciais nas páginas seguintes
                if (paginas.get(j) != null && !paginasComTabela.contains(j + 1)) {
                    //Se a página seguinte possui texto e não tem tabela, o sistema tenta extrair os diferenciais dela.
                    memorialTextExtractor.extrairDiferenciaisSemRotulo(paginas.get(j), doc, j + 1, camposGerais);
                }
            }
            return;
        }
    }

    private void processarPlanilha(EmpreendimentoDocumento doc, byte[] bytes, String ext,
                                    List<UnidadeExtraida> todasUnidades, List<String> alertas) throws IOException {
        SpreadsheetTableExtractor.Resultado extraido = spreadsheetTableExtractor.extrair(bytes, ext);
        alertas.addAll(prefixarAlertas(extraido.alertas, doc.getNomeOriginal()));
        for (TabelaBruta tabela : extraido.tabelas) {
            UnidadeTableMapper.Resultado mapeado = unidadeTableMapper.mapear(tabela, doc.getId(), doc.getNomeOriginal());
            todasUnidades.addAll(mapeado.unidades);
            alertas.addAll(prefixarAlertas(mapeado.alertas, doc.getNomeOriginal()));
        }
    }

    private void processarWord(EmpreendimentoDocumento doc, byte[] bytes, String ext, List<UnidadeExtraida> todasUnidades,
                                Map<String, Object> camposGerais, List<String> alertas) throws IOException {
        WordTextExtractor.Resultado extraido = wordTextExtractor.extrair(bytes, ext);
        alertas.addAll(prefixarAlertas(extraido.alertas, doc.getNomeOriginal()));
        for (TabelaBruta tabela : extraido.tabelas) {
            UnidadeTableMapper.Resultado mapeado = unidadeTableMapper.mapear(tabela, doc.getId(), doc.getNomeOriginal());
            todasUnidades.addAll(mapeado.unidades);
            alertas.addAll(prefixarAlertas(mapeado.alertas, doc.getNomeOriginal()));
        }
        if (extraido.texto != null && !extraido.texto.isBlank()) {
            // DOCX/DOC não têm paginação confiável via POI — página fica nula (nunca inventada) e a
            // rastreabilidade continua garantida pelo nome do documento e pelo trecho original.
            memorialTextExtractor.extrair(extraido.texto, doc, null, camposGerais, alertas);
        }
    }

    //	Acrescenta o nome do documento no início de cada alerta.
    private List<String> prefixarAlertas(List<String> alertas, //Uma lista de mensagens de alerta.
                                        String nomeDocumento //Nome do arquivo que gerou esses alertas.
                                        ) {
        return alertas.stream().map(a -> "[" + nomeDocumento + "] " + a).toList();
        //acrescenta o nome do arquivo no início de cada mensagem e reúne os resultados em uma nova lista.
    }

    //Altera o status de processamento do documento para revisao.
    private void marcarRevisao(EmpreendimentoDocumento doc) {
        //Ele altera o status de processamento do documento para revisão
        doc.setStatusProcessamento("revisao");
    }

    //Percorre o resultado da extração e coleta as evidências dos dados encontrados, como valor, confiança, página e documento.
    @SuppressWarnings("unchecked")
    private void coletarFontes(Object node, String prefixo, List<EmpreendimentoFonteData> out) {
        if (node instanceof Map) {//Verificar se é um Map
            Map<String, Object> mapa = (Map<String, Object>) node;
            if (mapa.containsKey("valor") && mapa.containsKey("confianca")) {//verifica se o mapa contém essas duas chaves
                Object valor = mapa.get("valor");//pega o conteúdo da chave valor.
                if (valor == null || String.valueOf(valor).isBlank()) return;//verifica se está vazio ou nulo.
                EmpreendimentoFonteData f = new EmpreendimentoFonteData();//Cria um objeto que representa a origem de uma informação.
                f.campo = prefixo;//Depois, preenche seus atributos
                //Identifica qual campo está sendo registrado.
                f.valorExtraido = String.valueOf(valor);
                //Guarda o valor encontrado.
                Object conf = mapa.get("confianca");
                f.confianca = conf instanceof Number n ? n.intValue() : null;
                //Se conf for um número, ele será convertido para inteiro.
                Object pag = mapa.get("pagina");
                f.pagina = pag instanceof Number n ? n.intValue() : null;

                Object trecho = mapa.get("trecho_original");
                f.trecho = trecho != null ? String.valueOf(trecho) : null;

                Object docNome = mapa.get("documento_nome");//Recupera o nome do documento.
                f.documentoNome = docNome != null ? String.valueOf(docNome) : null;

                out.add(f);//Adiciona a fonte à lista e encerra a execução para aquele nó.
                return;
            }
            for (Map.Entry<String, Object> e : mapa.entrySet()) {
                //Se o mapa não tiver a estrutura de um campo, o método percorre seus elementos.
                coletarFontes(e.getValue(), prefixo + "." + e.getKey(), out);//recursividade
                //Ainda não encontrei um campo extraído aqui. Vou entrar nos objetos que estão dentro deste objeto e procurar novamente.
            }
        } else if (node instanceof List<?> lista) {
            //Se o objeto for uma lista, o método percorre cada elemento e também chama a si mesmo.
            for (int i = 0; i < lista.size(); i++) coletarFontes(lista.get(i), prefixo + "[" + i + "]", out);
        }
    }

    //	Transforma um objeto CampoEvidencia em um Map, facilitando sua conversão para JSON.
    private Map<String, Object> campoJson(CampoEvidencia ev) {
        //recebe um objeto CampoEvidencia e transforma seus atributos em um Map.
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valor", ev.valor);
        m.put("confianca", ev.confianca);
        m.put("pagina", ev.pagina);
        m.put("trecho_original", ev.trechoOriginal);
        m.put("documento_nome", ev.documentoNome);
        return m;
    }
}

package crm_imobiliario.back.model.service.empreendimento.extracao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;

/**
 * Cobre os padrões de texto livre reconhecidos em memoriais/books reais
 * (§9/§17 do spec de melhoria de extração). Os trechos usados como fixture
 * abaixo foram copiados literalmente do Book "C-PRD - Book - London Plaza
 * (Digital).pdf" (com "\r\n", exatamente como o PDFBox extrai de um PDF real
 * no Windows) — não uma versão simplificada — para que os testes continuem
 * valendo como regressão do problema real encontrado: os padrões antigos
 * usavam "\n" e "\b" e nunca casavam com esse documento.
 */
class MemorialTextExtractorTest {

    private final MemorialTextExtractor extractor = new MemorialTextExtractor();

    private EmpreendimentoDocumento doc(String nome) {
        EmpreendimentoDocumento d = new EmpreendimentoDocumento();
        d.setNomeOriginal(nome);
        return d;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sub(Map<String, Object> campos, String chave) {
        return (Map<String, Object>) campos.get(chave);
    }

    @SuppressWarnings("unchecked")
    private String valor(Map<String, Object> campo) {
        return (String) campo.get("valor");
    }

    @Test
    void enderecoEmRodapeInstitucionalComQuebraDeLinhaEBarra() {
        // formato real do rodapé de books Pride: rua+número numa linha, cidade/UF na seguinte, sem "|"
        String texto = "construtorapride.com.br @meuapepride /meuapepride\r\n" +
                "Av. Saul Elkind,3439 \r\n" +
                "Londrina/PR \r\n" +
                "Central de Vendas:\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 21, campos, new java.util.ArrayList<>());

        Map<String, Object> loc = sub(campos, "localizacao");
        assertNotNull(loc, "deveria reconhecer o endereço do rodapé institucional");
        assertEquals("Av. Saul Elkind", valor(sub(loc, "endereco")));
        assertEquals("3439", valor(sub(loc, "numero")));
        assertEquals("Londrina", valor(sub(loc, "cidade")));
        assertEquals("PR", valor(sub(loc, "estado")));
        assertEquals(21, sub(loc, "endereco").get("pagina"), "página de origem deve ser preservada");
    }

    @Test
    void enderecoComPipeEHifenContinuaFuncionando() {
        // formato de planilha/tabela já suportado antes desta melhoria — não pode regredir
        String texto = "Avenida 01 Residencial Alvorada, 570 | Londrina - PR";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("planilha.xlsx"), null, campos, new java.util.ArrayList<>());

        Map<String, Object> loc = sub(campos, "localizacao");
        assertEquals("Avenida 01 Residencial Alvorada", valor(sub(loc, "endereco")));
        assertEquals("570", valor(sub(loc, "numero")));
        assertEquals("Londrina", valor(sub(loc, "cidade")));
        assertEquals("PR", valor(sub(loc, "estado")));
    }

    @Test
    void enderecoDeMapaDeLocalizacaoNaoEConfundidoComOEnderecoReal() {
        // página de mapa: uma rua com número aparece perto de uma lista de pontos numerados,
        // NÃO deve ser confundida com o endereço oficial do empreendimento (§16 do spec: não inventar).
        String texto = "Av. Rosalvo Marques Bonfim, 1433\r\n1\r\n2\r\n3\r\n4\r\n5\r\n6\r\n7\r\n" +
                "Colégio Estadual Cívico Militar (2 min)\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 3, campos, new java.util.ArrayList<>());

        assertNull(campos.get("localizacao"), "endereço de página de mapa não deve virar o endereço do empreendimento");
    }

    @Test
    void diferenciaisComRotuloEDoisPontosContinuaFuncionando() {
        String texto = "Diferenciais: Piscina, Academia, Salão de festas\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("memorial.pdf"), 1, campos, new java.util.ArrayList<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> difs = (List<Map<String, Object>>) campos.get("diferenciais");
        assertEquals(3, difs.size());
        assertEquals("Piscina", valor(sub(difs.get(0), "titulo")));
    }

    @Test
    void diferenciaisEmBlocoSemRotuloDeDoisPontosUmItemPorLinha() {
        // formato real do Book: "DIFERENCIAIS" sozinho numa linha, um item por linha, sem vírgulas
        String texto = "Com opções de jardim privativo\r\n" +
                "DIFERENCIAIS\r\n" +
                " Torres de 7 pavimentos com elevador\r\n" +
                "4 unidades por andar \r\n" +
                "1 vaga de garagem descoberta por apartamento \r\n" +
                "Piso cerâmico nas áreas molhadas\r\n" +
                "\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> difs = (List<Map<String, Object>>) campos.get("diferenciais");
        assertNotNull(difs, "bloco DIFERENCIAIS sem dois-pontos deveria ser reconhecido");
        assertEquals(4, difs.size());
        assertEquals("Torres de 7 pavimentos com elevador", valor(sub(difs.get(0), "titulo")));
        assertEquals("4 unidades por andar", valor(sub(difs.get(1), "titulo")));
        assertEquals("1 vaga de garagem descoberta por apartamento", valor(sub(difs.get(2), "titulo")));
        assertEquals("Piso cerâmico nas áreas molhadas", valor(sub(difs.get(3), "titulo")));
        assertEquals(14, sub(difs.get(0), "titulo").get("pagina"));
    }

    @Test
    void quartosReconhecePadraoNQuartos() {
        String texto = "2 QUARTOS COM SACADA 2 QUARTOS COM SACADA\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("2", valor(sub(car, "quartos_min")));
        assertEquals("2", valor(sub(car, "quartos_max")));
    }

    @Test
    void metragemComSimboloDeSuperescritoEReconhecidaQuandoConsistente() {
        // "²" (U+00B2) não é caractere de palavra no regex Java — o padrão antigo com \b no final
        // nunca casava com metragens reais de PDF, mesmo aparecendo várias vezes na página.
        String texto = "39,50m² \r\n" +
                "●  39,50m² + 9,09m² \r\n" +
                "●  39,50m² + 9,22m² \r\n" +
                "2 QUARTOS COM SACADA 2 QUARTOS COM SACADA\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("39,50", valor(sub(car, "metragem_min")));
        assertEquals("39,50", valor(sub(car, "metragem_max")));
        // as variações "+ 9,09m²"/"+ 9,22m²" não devem ser somadas nem viram um campo de área —
        // ambíguo demais para decidir sozinho (garden? varanda?), fica para revisão manual.
        assertFalse(car.containsKey("area_comum"));
        assertFalse(car.containsKey("outras_areas"));
    }

    @Test
    void metragemAmbiguaComDuasTipologiasNaoEExtraidaAutomaticamente() {
        // duas metragens distintas na mesma página: não dá para saber qual é "a" do empreendimento —
        // melhor não preencher do que preencher errado (§16 do spec).
        String texto = "39,50m² \r\n55,80m² \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertTrue(car == null || !car.containsKey("metragem_min"), "não deve adivinhar entre duas metragens diferentes");
    }

    @Test
    void pavimentosETorresComElevador() {
        String texto = " Torres de 7 pavimentos com elevador\r\n4 unidades por andar \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("7", valor(sub(car, "pavimentos")));
        assertEquals("true", valor(sub(car, "possui_elevador")));
        assertEquals("4", valor(sub(car, "unidades_por_andar")));
    }

    @Test
    void pavimentosSemElevadorNaoMarcaPossuiElevador() {
        String texto = "Torres de 4 pavimentos sem elevador\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 1, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("4", valor(sub(car, "pavimentos")));
        assertFalse(car.containsKey("possui_elevador"));
    }

    @Test
    void vagaDeGaragemPorApartamentoViraVagasMinEMax() {
        String texto = "1 vaga de garagem descoberta por apartamento \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("1", valor(sub(car, "vagas_min")));
        assertEquals("1", valor(sub(car, "vagas_max")));
    }

    @Test
    void areasComunsListaSobORotuloAteAsteriscoDeRodape() {
        String texto = "* Todos os ambientes são entregues equipados e decorados. \r\n" +
                "ÁREAS COMUNS \r\n" +
                "Guarita  \r\n" +
                "Bicicletário\r\n" +
                "Salão de Festas\r\n" +
                "Piscina  \r\n" +
                "Playground\r\n" +
                "Quiosque Duplo com Churrasqueira\r\n" +
                "Quadra de Areia\r\n" +
                "Pet Place \r\n" +
                "Fitness Externo \r\n" +
                "*Imagem meramente ilustrativa.\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 2, campos, new java.util.ArrayList<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> areas = (List<Map<String, Object>>) campos.get("areas_comuns");
        assertNotNull(areas);
        assertEquals(9, areas.size());
        assertEquals("Guarita", valor(sub(areas.get(0), "nome")));
        assertEquals("Fitness Externo", valor(sub(areas.get(8), "nome")));
        assertEquals(2, sub(areas.get(0), "nome").get("pagina"));
        // a nota de rodapé com "*" não pode virar um item de lazer
        assertTrue(areas.stream().noneMatch(a -> valor(sub(a, "nome")).contains("Imagem meramente ilustrativa")));
    }

    @Test
    void pontosDeReferenciaComTempoEmMinutos() {
        String texto = "Colégio Estadual Cívico Militar (2 min) \r\n" +
                "Posto de Saúde Chefe Newton Guimarães (4 min) \r\n" +
                "Supermercado Tonhão Maxi (5 min) \r\n" +
                "Terminal de Ônibus Vivi Xavier (8 min)\r\n" +
                "Shopping Norte (15 min)  \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 3, campos, new java.util.ArrayList<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pontos = (List<Map<String, Object>>) campos.get("pontos_referencia");
        assertNotNull(pontos);
        assertEquals(5, pontos.size());
        assertEquals("Colégio Estadual Cívico Militar", valor(sub(pontos.get(0), "nome")));
        assertEquals("2", valor(sub(pontos.get(0), "tempo")));
        assertEquals("Shopping Norte", valor(sub(pontos.get(4), "nome")));
        assertEquals("15", valor(sub(pontos.get(4), "tempo")));
    }

    @Test
    void pontosDeReferenciaAcumulamEntreChamadasDePaginasDiferentes() {
        Map<String, Object> campos = new LinkedHashMap<>();
        extractor.extrair("Colégio Estadual Cívico Militar (2 min) \r\n", doc("book.pdf"), 3, campos, new java.util.ArrayList<>());
        extractor.extrair("Shopping Norte (15 min)  \r\n", doc("book.pdf"), 3, campos, new java.util.ArrayList<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pontos = (List<Map<String, Object>>) campos.get("pontos_referencia");
        assertEquals(2, pontos.size(), "pontos de referência de chamadas diferentes (páginas diferentes) devem se acumular, não se sobrescrever");
    }

    @Test
    void naoSobrescreveValorJaEncontradoEmPaginaAnterior() {
        Map<String, Object> campos = new LinkedHashMap<>();
        extractor.extrair("2 QUARTOS\r\n", doc("book.pdf"), 5, campos, new java.util.ArrayList<>());
        extractor.extrair("3 QUARTOS\r\n", doc("book.pdf"), 9, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("2", valor(sub(car, "quartos_min")), "o primeiro valor encontrado (página 5) deve prevalecer");
        assertEquals(5, sub(car, "quartos_min").get("pagina"));
    }

    @Test
    void textoSemNenhumPadraoReconhecidoNaoPreencheNada() {
        String texto = "Seu sonho de morar bem começa aqui.\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 1, campos, new java.util.ArrayList<>());

        assertTrue(campos.isEmpty(), "texto puramente publicitário não deve gerar nenhum campo inventado");
    }
}

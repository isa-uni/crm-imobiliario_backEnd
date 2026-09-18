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
    void enderecoNoRodapeInstitucionalDaPrideNuncaEUsadoComoEnderecoDoEmpreendimento() {
        // formato real da última página dos books Pride: rua+número numa linha, cidade/UF na seguinte —
        // mas é o endereço da CONSTRUTORA (site institucional, redes sociais, "Central de Vendas"), não
        // do empreendimento. Antes desta correção este texto era (incorretamente) aceito como o endereço
        // do empreendimento; agora o filtro de contexto do §5 do spec deve rejeitá-lo, mesmo numa página
        // ainda elegível.
        String texto = "construtorapride.com.br @meuapepride /meuapepride\r\n" +
                "Av. Saul Elkind,3439 \r\n" +
                "Londrina/PR \r\n" +
                "Central de Vendas:\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();
        List<String> alertas = new java.util.ArrayList<>();

        extractor.extrair(texto, doc("book.pdf"), 21, true, campos, alertas);

        assertNull(campos.get("localizacao"), "endereço junto a dados institucionais da Pride não pode virar o endereço do empreendimento");
        assertTrue(alertas.stream().anyMatch(a -> a.contains("Pride")), "deve registrar por que o candidato foi rejeitado");
    }

    @Test
    void enderecoValidoNaoElegivelPorSerUltimaPaginaDeBookNaoEExtraido() {
        // mesmo um endereço bem formado, sem nenhum sinal institucional da Pride, não deve ser usado
        // quando a página não é elegível (última página de um Book, decidida pelo orquestrador) — §1/§3.
        String texto = "Rua das Palmeiras, 250\r\nLondrina/PR\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 21, false, campos, new java.util.ArrayList<>());

        assertNull(campos.get("localizacao"), "página não elegível não deve gerar endereço do empreendimento, mesmo sem sinal de Pride");
    }

    @Test
    void mencaoDistanteAPrideNaoBloqueiaEnderecoLegitimoDoEmpreendimento() {
        // a palavra "Pride" aparece longe do candidato de endereço, numa frase descritiva comum do
        // empreendimento (não do rodapé institucional) — o filtro de contexto é por proximidade, não
        // pela página inteira, então não pode rejeitar um endereço legítimo só por isso (§5 do spec:
        // "não remover um endereço apenas por conter uma palavra genérica").
        String enchimento = "x".repeat(200);
        String texto = "O Residencial Exemplo é mais um empreendimento da Construtora Pride na Zona Norte de Londrina.\r\n"
                + enchimento + "\r\n"
                + "Avenida das Nações, 800 | Londrina - PR\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 2, true, campos, new java.util.ArrayList<>());

        Map<String, Object> loc = sub(campos, "localizacao");
        assertNotNull(loc, "menção distante à Pride não pode impedir a extração de um endereço legítimo");
        assertEquals("Avenida das Nações", valor(sub(loc, "endereco")));
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
    void enderecoIsoladoNaLegendaDoMapaEExtraidoComBaixaConfianca() {
        // esse trecho é exatamente o texto real da página 3 do Book. A renderização visual da página
        // (via PDFBox PDFRenderer) confirmou que "Av. Rosalvo Marques Bonfim, 1433" é o endereço oficial
        // do empreendimento — aparece destacado com um ícone de pino na legenda do mapa, e não é o
        // endereço de outro empreendimento: a lista "Bliss/Sonne/London Palace/..." vista antes dele no
        // texto simples é, na verdade, a legenda de cores dos BLOCOS internos do próprio London Plaza,
        // não de empreendimentos vizinhos (confirmado visualmente, não apenas pela ordem do texto — §16
        // do spec: a ordem do texto extraído não é a ordem visual). Por não ter cidade/UF na mesma linha
        // para corroborar, a confiança fica abaixo do limiar de revisão (60%) para exigir confirmação humana.
        String texto = "Av. Rosalvo Marques Bonfim, 1433\r\n1\r\n2\r\n3\r\n4\r\n5\r\n6\r\n7\r\n" +
                "Colégio Estadual Cívico Militar (2 min)\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 3, true, campos, new java.util.ArrayList<>());

        Map<String, Object> loc = sub(campos, "localizacao");
        assertNotNull(loc, "endereço isolado na legenda do mapa deveria ser reconhecido");
        assertEquals("Av. Rosalvo Marques Bonfim", valor(sub(loc, "endereco")));
        assertEquals("1433", valor(sub(loc, "numero")));
        assertTrue((Integer) sub(loc, "endereco").get("confianca") < 60,
                "sem cidade/UF corroborando na mesma linha, deve ficar marcado para revisão humana (★ <60%)");
    }

    @Test
    void nomeDeRuaDoMapaSemNumeroNaoGeraEnderecoNenhum() {
        // ruas do mapa citadas sem número associado (só o nome, como aparecem nos rótulos das vias) não
        // devem virar um endereço — não há como inventar um número que não está no documento.
        String texto = "Av. Saul Elkind\r\nAv. Giocondo Maturi\r\nAv. Café Rubiácea\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 3, true, campos, new java.util.ArrayList<>());

        assertNull(campos.get("localizacao"), "nome de rua sem número não deve virar endereço do empreendimento");
    }

    @Test
    void regiaoECidadeSaoExtraidasDaPaginaDeMapaLogoAposAreasComuns() {
        // trecho real da página 3 do Book (mapa de localização, logo após ÁREAS COMUNS na página 2):
        // o empreendimento raramente tem endereço completo aqui, mas sempre descreve região/cidade.
        String texto = "A Zona Norte de Londrina concentra mais de 25% da população total da cidade e não para de crescer. \r\n" +
                "O Residencial London Plaza, localizado estrategicamente na Zona Norte da cidade, oferece a conveniência\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 3, true, campos, new java.util.ArrayList<>());

        Map<String, Object> loc = sub(campos, "localizacao");
        assertNotNull(loc, "deveria reconhecer região e cidade na página de mapa/localização");
        assertEquals("Zona Norte", valor(sub(loc, "regiao")));
        assertEquals("Londrina", valor(sub(loc, "cidade")));
        assertEquals(3, sub(loc, "cidade").get("pagina"));
    }

    @Test
    void regiaoComNomeDeCidadeComposto() {
        String texto = "Localizado na Região Sul de Rio de Janeiro, o empreendimento oferece fácil acesso.\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 3, true, campos, new java.util.ArrayList<>());

        Map<String, Object> loc = sub(campos, "localizacao");
        assertEquals("Região Sul", valor(sub(loc, "regiao")));
        assertEquals("Rio de Janeiro", valor(sub(loc, "cidade")));
    }

    @Test
    void regiaoCidadeJuntoADadosInstitucionaisDaPrideNaoEUsada() {
        String texto = "Central de Vendas Pride: fale conosco. Zona Norte de Londrina, atendimento ao cliente.\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();
        List<String> alertas = new java.util.ArrayList<>();

        extractor.extrair(texto, doc("book.pdf"), 21, true, campos, alertas);

        assertNull(campos.get("localizacao"), "região/cidade junto a dados institucionais da Pride não deve ser usada");
    }

    @Test
    void regiaoCidadeNaoElegivelPorSerUltimaPaginaDeBookNaoEExtraida() {
        String texto = "A Zona Norte de Londrina concentra boa infraestrutura.\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 21, false, campos, new java.util.ArrayList<>());

        assertNull(campos.get("localizacao"), "página não elegível não deve gerar região/cidade, mesmo bem formada");
    }

    // ---- Testes de reforço de assertividade contra 4 books reais (Felicce, London Plaza, Marbella,
    // Solare) — cada um usa uma variação real de formatação para o mesmo tipo de dado, e os bugs abaixo
    // só apareceram ao rodar a extração de verdade contra os 4 arquivos, não contra o London Plaza sozinho.

    @Test
    void nomeReconhecidoNaAberturaSejaBemVindoComResidencial() {
        // trecho real da página 2 do Book da Felicce
        String texto = "Seja bem-vindo ao Residencial Felicce, novo \r\n" +
                "empreendimento da Pride Construtora  na Zona Norte de \r\n" +
                "Londrina, 100% enquadrado no programa Minha Casa,\r\n" +
                "Minha Vida.\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 2, true, campos, new java.util.ArrayList<>());

        Map<String, Object> id = sub(campos, "identificacao");
        assertNotNull(id, "deveria reconhecer o nome na abertura padrão dos books");
        assertEquals("Residencial Felicce", valor(sub(id, "nome")));
    }

    @Test
    void nomeReconhecidoNaAberturaSejaBemVindoSemResidencial() {
        // trecho real da página 2 do Book da Solare — a linha Solare não usa o prefixo "Residencial"
        String texto = "Seja bem-vindo ao Solare Essenza, o primeiro empreendimen-\r\n" +
                "to da Pride Construtora na Zona Sul de Londrina, 100% en-\r\n" +
                "quadrado no Minha Casa Minha Vida.\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 2, true, campos, new java.util.ArrayList<>());

        Map<String, Object> id = sub(campos, "identificacao");
        assertNotNull(id, "deveria reconhecer o nome mesmo sem o prefixo 'Residencial'");
        assertEquals("Solare Essenza", valor(sub(id, "nome")));
    }

    @Test
    void nomeReconhecidoPeloFallbackResidencialQuandoNaoHaSejaBemVindo() {
        // trecho real da página 2 do Book da Marbella — abertura totalmente diferente, sem "Seja bem-vindo"
        String texto = "A Construtora Pride tem orgulho em apresentar seu primeiro \r\n" +
                "lançamento em Campo Mourão: o Residencial Marbella, \r\n" +
                "empreendimento 100% enquadrado no programa Minha \r\n" +
                "Casa, Minha Vida. \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 2, true, campos, new java.util.ArrayList<>());

        Map<String, Object> id = sub(campos, "identificacao");
        assertNotNull(id, "deveria reconhecer o nome pelo padrão de fallback 'Residencial X'");
        assertEquals("Residencial Marbella", valor(sub(id, "nome")));
        assertTrue((Integer) sub(id, "nome").get("confianca") < 75,
                "fallback sem a abertura padrão deve ter confiança menor que a abertura 'Seja bem-vindo'");
    }

    @Test
    void enderecoIsoladoComPrefixoDeNumeroAbreviado() {
        // trecho real da página 3 do Book da Felicce: número prefixado com "nº", não só vírgula+dígitos
        String texto = "London Plaza\r\nRua Giocondo Maturi, nº1400\r\nDescubra os motivos \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 3, true, campos, new java.util.ArrayList<>());

        Map<String, Object> loc = sub(campos, "localizacao");
        assertNotNull(loc, "endereço com número no formato 'nº1400' deveria ser reconhecido");
        assertEquals("Rua Giocondo Maturi", valor(sub(loc, "endereco")));
        assertEquals("1400", valor(sub(loc, "numero")));
    }

    @Test
    void enderecoIsoladoComBairroAposHifenEReconhecidoSeparadamente() {
        // trecho real da página 3 do Book da Marbella: "Rua X, NÚMERO - BAIRRO" numa linha só
        String texto = "para criar belas histórias ao lado de quem você ama. \r\n" +
                "Rua das Araucárias, 200 - Jardim Araucária \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 3, true, campos, new java.util.ArrayList<>());

        Map<String, Object> loc = sub(campos, "localizacao");
        assertNotNull(loc);
        assertEquals("Rua das Araucárias", valor(sub(loc, "endereco")));
        assertEquals("200", valor(sub(loc, "numero")));
        assertEquals("Jardim Araucária", valor(sub(loc, "bairro")), "bairro após o hífen deve virar campo próprio, não ficar colado no logradouro");
    }

    @Test
    void mencaoAPrideConstrutoraBemPertoDaRegiaoNaoBloqueiaMaisAExtracao() {
        // BUG REAL encontrado ao testar contra o Book da Felicce: a introdução de TODO book real diz
        // "novo empreendimento da Pride Construtora na Zona X de Cidade" — usar a palavra solta "pride"
        // como sinal institucional rejeitava essa frase legítima, que está a poucos caracteres da região/
        // cidade reais. O filtro agora exige um sinal mais específico (site, @handle, central de vendas).
        String texto = "Seja bem-vindo ao Residencial Felicce, novo \r\n" +
                "empreendimento da Pride Construtora  na Zona Norte de \r\n" +
                "Londrina, 100% enquadrado no programa Minha Casa,\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();
        List<String> alertas = new java.util.ArrayList<>();

        extractor.extrair(texto, doc("book.pdf"), 2, true, campos, alertas);

        Map<String, Object> loc = sub(campos, "localizacao");
        assertNotNull(loc, "menção a 'Pride Construtora' na frase de apresentação não pode bloquear região/cidade reais");
        assertEquals("Zona Norte", valor(sub(loc, "regiao")));
        assertEquals("Londrina", valor(sub(loc, "cidade")));
        assertTrue(alertas.isEmpty(), "não deveria gerar alerta de rejeição para este trecho legítimo");
    }

    @Test
    void elevadorEReconhecidoMesmoSemAQuantidadeDePavimentos() {
        // BUG REAL do Book da Felicce: "Torres com elevador" sem "de N pavimentos" — o código antigo só
        // procurava "elevador" logo depois de um match de PAVIMENTOS, então perdia essa informação real
        // por completo quando o book não informa a quantidade de andares.
        String texto = "DIFERENCIAIS\r\n 4 unidades por andar\r\nTorres com elevador  \r\n" +
                "1 vaga de garagem por apartamento  \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 12, true, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertNotNull(car);
        assertEquals("true", valor(sub(car, "possui_elevador")));
        assertFalse(car.containsKey("pavimentos"), "não deve inventar uma quantidade de pavimentos que não está no texto");
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
        // As variações "+ 9,09m²"/"+ 9,22m²" são a MESMA unidade com uma área adicional (garden/varanda)
        // somada — min é a área base sozinha (variante sem adicional) e max é a base + a maior soma real
        // encontrada (39,50 + 9,22 = 48,72), nunca uma combinação que não apareceu no texto.
        String texto = "39,50m² \r\n" +
                "●  39,50m² + 9,09m² \r\n" +
                "●  39,50m² + 9,22m² \r\n" +
                "2 QUARTOS COM SACADA 2 QUARTOS COM SACADA\r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("39,50", valor(sub(car, "metragem_min")));
        assertEquals("48,72", valor(sub(car, "metragem_max")));
        assertFalse(car.containsKey("area_comum"));
        assertFalse(car.containsKey("outras_areas"));
    }

    @Test
    void metragemSemNenhumaAreaAdicionalContinuaComMinEMaxIguais() {
        String texto = "39,50m² \r\n39,50m² \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("39,50", valor(sub(car, "metragem_min")));
        assertEquals("39,50", valor(sub(car, "metragem_max")));
    }

    @Test
    void metragemComApenasVarianteComAreaAdicionalSomaCorretamente() {
        // se a única ocorrência já vem com área adicional, min e max devem ser os dois totais reais:
        // a base sozinha nunca apareceu no texto, então ela não vira o "min" — só o que está escrito.
        String texto = "39,50m² + 9,22m² \r\n";
        Map<String, Object> campos = new LinkedHashMap<>();

        extractor.extrair(texto, doc("book.pdf"), 14, campos, new java.util.ArrayList<>());

        Map<String, Object> car = sub(campos, "caracteristicas");
        assertEquals("48,72", valor(sub(car, "metragem_min")));
        assertEquals("48,72", valor(sub(car, "metragem_max")));
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

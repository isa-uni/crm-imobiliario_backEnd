package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;

/**
 * Extração heurística e deliberadamente conservadora de campos gerais do
 * empreendimento (endereço, diferenciais, características, lazer, pontos de
 * referência) a partir de texto livre de memoriais/books (§9 do spec). Só
 * preenche o que casa com um padrão explícito e rastreável; nunca infere
 * metragem, preço ou nome a partir de frases de marketing genéricas — esses
 * ficam para o usuário confirmar na revisão.
 *
 * Chamado uma vez por página (não uma vez para o documento inteiro): livros
 * comerciais reais (ex.: Book Pride) espalham cada informação numa página
 * diferente — "Diferenciais" numa página de planta, endereço no rodapé
 * institucional, lazer numa lista isolada — e o número da página é a única
 * forma confiável de rastrear de onde cada campo veio (§15 do spec).
 */
@Component
public class MemorialTextExtractor {

    // "Avenida 01 Residencial Alvorada, 570 | Londrina - PR" (tabela/planilha com endereço em uma linha só)
    private static final Pattern ENDERECO_LINHA_UNICA = Pattern.compile(
            "([A-ZÀ-Ú][^,|\\n]{4,80}),\\s*(\\d+)\\s*\\|\\s*([^-|\\n]{2,40}?)\\s*-\\s*([A-Z]{2})\\b");

    // "Av. Saul Elkind,3439 \nLondrina/PR" (rodapé institucional de books: rua+número numa linha,
    // cidade/UF na linha seguinte, sem separador "|") — exige a barra "/UF" na linha seguinte para não
    // casar com qualquer trecho "Rua X, 123" solto no meio de um texto de marketing ou mapa.
    private static final Pattern ENDERECO_DUAS_LINHAS = Pattern.compile(
            "((?:Av\\.?|Avenida|Rua|R\\.)\\s*[^,\\n]{2,60}),\\s*(\\d+)\\s*\\n\\s*([^\\n/]{2,40})/([A-Z]{2})\\b");

    // "Av. Rosalvo Marques Bonfim, 1433" ou "Rua Giocondo Maturi, nº1400" ou "Rua das Araucárias, 200 -
    // Jardim Araucária" sozinho na própria linha — formato real do "pin" de endereço em legendas de mapa
    // de localização (confirmado por renderização visual da página real, em múltiplos books: PDFBox
    // reproduz o texto da legenda numa linha isolada, mas nada no texto simples indica que é um pino de
    // mapa — só o fato de a linha conter EXATAMENTE rua+número [+bairro opcional] e nada mais permite
    // diferenciar de uma rua qualquer citada dentro de uma frase corrida, que sempre continua na mesma
    // linha). O número aceita tanto ", 123" quanto ", nº123"/", n°123" (as duas grafias reais vistas nos
    // books). O bairro após um hífen é opcional e vira um campo à parte, nunca concatenado ao logradouro.
    // Confiança mais baixa que os outros dois padrões (não há cidade/UF junto para corroborar).
    private static final Pattern ENDERECO_LINHA_ISOLADA = Pattern.compile(
            "(?m)^[ \\t]*((?:Av\\.?|Avenida|Rua|R\\.)\\s+[^,\\n]{2,60}),\\s*(?:n[º°o]\\s*)?(\\d+)"
                    + "(?:\\s*-\\s*([^\\n]{2,40}?))?[ \\t]*$");

    // Forma antiga com rótulo explícito: "Diferenciais: A, B, C" (uma linha, itens separados por vírgula)
    private static final Pattern DIFERENCIAIS_ROTULO_LINHA = Pattern.compile("Diferenciais:\\s*([^\\n]+)");

    // Forma de book real: rótulo "DIFERENCIAIS" sozinho numa linha, seguido de um item por linha até a
    // primeira linha em branco (ex.: página de planta com "Torres de 7 pavimentos com elevador",
    // "4 unidades por andar", "1 vaga de garagem descoberta por apartamento", "Piso cerâmico...").
    // "\R" (não "\n"): books reais vêm com quebra de linha "\r\n" — usar "\n" literal deixaria de
    // casar todo o bloco silenciosamente, porque o "$" do fim da linha do rótulo já consome o "\r".
    private static final Pattern DIFERENCIAIS_BLOCO = Pattern.compile(
            "(?im)^\\s*DIFERENCIAIS\\s*$((?:\\R(?!\\s*$).+)*)");

    private static final Pattern QUARTOS = Pattern.compile("(\\d)\\s*QUARTOS?", Pattern.CASE_INSENSITIVE);

    // "39,50m²" sozinho, ou "39,50m² + 9,22m²" — área privativa base, com uma área adicional (garden/
    // varanda/etc.) opcionalmente somada NA MESMA unidade. Os dois grupos ficam juntos num só match para
    // que "+ 9,22m²" nunca seja contado como uma área/tipologia independente. Sem "\b" no final: "²"
    // (U+00B2) não é considerado caractere de palavra pelo \w/\b do regex Java em modo ASCII, então uma
    // fronteira de palavra logo após "²" nunca é satisfeita e o casamento falha sempre — foi assim que o
    // valor real "39,50m²" deixou de ser reconhecido durante os testes.
    private static final Pattern METRAGEM = Pattern.compile(
            "(\\d{2,3},\\d{2})\\s*m[²2](?:\\s*\\+\\s*(\\d{1,3},\\d{2})\\s*m[²2])?(?![\\p{L}\\p{N}])");

    private static final Pattern PAVIMENTOS = Pattern.compile(
            "Torres?\\s+de\\s+(\\d{1,2})\\s+pavimentos?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ELEVADOR = Pattern.compile("com\\s+elevador", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIDADES_POR_ANDAR = Pattern.compile(
            "(\\d{1,2})\\s+unidades?\\s+por\\s+andar", Pattern.CASE_INSENSITIVE);
    private static final Pattern VAGAS_GARAGEM = Pattern.compile(
            "(\\d{1,2})\\s+vagas?\\s+de\\s+garagem", Pattern.CASE_INSENSITIVE);

    // Lista de lazer sob o rótulo "ÁREAS COMUNS" (uma linha por item, até a primeira linha vazia ou
    // que comece com "*" — que nos books reais é sempre a nota de rodapé "*Imagem meramente ilustrativa.")
    private static final Pattern AREAS_COMUNS_BLOCO = Pattern.compile(
            "(?im)^\\s*(?:[ÁA]REAS\\s+COMUNS|[ÁA]REAS\\s+DE\\s+LAZER|LAZER)\\s*:?\\s*$((?:\\R(?!\\s*$)(?!\\s*\\*).+)*)");

    // Vocabulário de itens de lazer/áreas comuns — usado quando o book lista as áreas comuns sem o
    // rótulo "ÁREAS COMUNS" (ex.: Book Solare, página 2: a lista vem logo após a nota "*O mercado é o
    // único ambiente entregue..."). Só reconhece uma lista quando a maioria das linhas é desse vocabulário.
    private static final Pattern VOCABULARIO_LAZER = Pattern.compile(
            "(?i)guarita|portaria|biciclet|playground|brinquedoteca|quiosque|churrasqueira|quadra|pet\\s*place|pet\\b"
                    + "|fitness|academia|piscina|sal[aã]o|espa[cç]o\\s+(gourmet|kids|zen|teen)|gourmet|mercado|lavanderia"
                    + "|coworking|horta|red[aá]rio|sauna|estacionamento|deck|solarium|lounge|cinema|jogos|pomar|pista"
                    + "|pra[cç]a|bosque|sport|beach");

    // "Colégio Estadual Cívico Militar (2 min)" — ponto de referência com tempo estimado até o local.
    private static final Pattern PONTO_REFERENCIA = Pattern.compile(
            "(?m)^\\s*([^\\n(]{3,70}?)\\s*\\((\\d{1,3})\\s*min\\)\\s*$");

    // "UTFPR  6 min" / "BR 487  4 min" — a mesma lista do mapa, sem parênteses (Book Marbella).
    // Só vale quando a página tem pelo menos 2 linhas assim (uma lista), para não pegar frase solta.
    private static final Pattern PONTO_REFERENCIA_SEM_PARENTESES = Pattern.compile(
            "(?m)^[ \\t]*([^\\n(]{3,70}?)[ \\t]+(\\d{1,3})[ \\t]*min\\.?[ \\t]*$");

    // "Shopping Norte - 1,2 km" / "Escola Municipal (800 m)" — ponto de referência por distância.
    private static final Pattern PONTO_REFERENCIA_DISTANCIA = Pattern.compile(
            "(?m)^[ \\t]*([^\\n(]{3,70}?)[ \\t]*(?:[-–][ \\t]*|\\()(\\d{1,4}(?:,\\d{1,2})?)[ \\t]*(km|m)\\)?[ \\t]*$");

    // Rótulo da seção de diferenciais sozinho na linha ("DIFERENCIAIS" / "Diferenciais").
    private static final Pattern ROTULO_DIFERENCIAIS = Pattern.compile("(?i)^\\s*diferenciais\\s*$");

    // O que caracteriza um item de diferencial do apartamento/condomínio nos books reais: torres,
    // pavimentos, elevador, unidades por andar, vagas, acabamentos... Frases de marketing ("O lugar onde
    // mora a felicidade") não casam e encerram a lista.
    private static final Pattern TERMO_DIFERENCIAL = Pattern.compile(
            "(?i)por\\s+andar|vagas?\\b|garagem|elevador|torres?\\b|pavimentos?|piso|porcelanato|sacada|varanda"
                    + "|churrasqueira|aquec|g[aá]s\\s+(central|encanado|individual)|medi[cç][aã]o\\s+individual"
                    + "|fechadura|ar[- ]condicionado|infraestrutura|tomadas?\\b|esquadria|laje|forro|gesso"
                    + "|vista|energia\\s+solar|fotovoltaic|led\\b|reuso|jardim\\s+privativo|acessibilidade");
    // Linhas que parecem diferencial mas são planta/rodapé: metragem, quartos, notas e avisos.
    private static final Pattern NAO_DIFERENCIAL = Pattern.compile(
            "(?i)m[²2]|\\bquartos?\\b|ilustrativ|consulte|memorial|n[aã]o\\s+s[aã]o\\s+entregues|^\\*");
    // Palavra que, no fim de uma linha, indica que o item continua na linha de baixo ("Torres de" / "4 pavimentos").
    private static final Pattern TERMINA_EM_CONECTIVO = Pattern.compile(
            "(?i)\\b(de|do|da|dos|das|com|e|em|por|para|na|no|nas|nos|a|o|ao)$");

    // "Zona Norte de Londrina" / "Região Central de Maringá" — na página de localização/mapa (que nos
    // books reais vem logo após a lista de ÁREAS COMUNS)
    private static final Pattern REGIAO_CIDADE = Pattern.compile(
            "((?i:(?:Zona|Regi[aã]o)\\s+(?:Norte|Sul|Leste|Oeste|Central|Metropolitana)))\\s+de\\s+"
                    + "([A-ZÀ-Ú][\\p{L}]+(?:\\s+(?:de|do|dos|da|das)\\s+[A-ZÀ-Ú][\\p{L}]+)*)");

    // "Seja bem-vindo ao Residencial Felicce," / "Seja bem-vindo ao Solare Essenza," — abertura padrão dos
    // books reais da Pride, sempre na página 2, apresentando o nome oficial do empreendimento logo após
    // "ao"
    private static final Pattern NOME_BOAS_VINDAS = Pattern.compile(
            "(?i:Seja\\s+bem[- ]?vindo\\s+ao)\\s+([A-ZÀ-Ú][\\p{L}]*(?:\\s+[A-ZÀ-Ú][\\p{L}]*){0,3})");

    // Fallback para books sem a abertura "Seja bem-vindo ao X" (ex.: "...tem orgulho em apresentar... o
    // Residencial Marbella,"). Menos específico que o padrão acima — só usado quando ele não é encontrado.
    private static final Pattern NOME_RESIDENCIAL = Pattern.compile(
            "(Residencial\\s+[A-ZÀ-Ú][\\p{L}]*(?:\\s+[A-ZÀ-Ú][\\p{L}]*){0,3})");

    // Sinais de que um endereço encontrado pertence à Pride (institucional/comercial), não ao
    // empreendimento — rodapé de contato, site/redes institucionais, central de vendas/plantão etc.
    private static final Pattern SINAL_INSTITUCIONAL_PRIDE = Pattern.compile(
            "(?i)meuapepride|construtorapride|central\\s+de\\s+vendas|plant[aã]o\\s+de\\s+vendas|fale\\s+conosco|atendimento\\s+ao\\s+cliente");
    private static final int JANELA_CONTEXTO_ENDERECO = 150;

    /**
     * @param elegivelParaEndereco false quando esta página não pode ser usada como fonte do endereço
     *                             do empreendimento — usado pelo orquestrador para excluir a última
     *                             página
     */
    public void extrair(String texto, EmpreendimentoDocumento doc, Integer pagina, boolean elegivelParaEndereco,
                         Map<String, Object> camposGerais, List<String> alertas) {
        extrairNome(texto, doc, pagina, camposGerais);
        extrairEndereco(texto, doc, pagina, elegivelParaEndereco, camposGerais, alertas);
        extrairDiferenciais(texto, doc, pagina, camposGerais);
        extrairCaracteristicas(texto, doc, pagina, camposGerais);
        extrairAreasComuns(texto, doc, pagina, camposGerais);
        extrairPontosReferencia(texto, doc, pagina, camposGerais);
    }

    private void extrairNome(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        if (!extrairNomeComPadrao(NOME_BOAS_VINDAS, texto, doc, pagina, camposGerais, 75)) {
            extrairNomeComPadrao(NOME_RESIDENCIAL, texto, doc, pagina, camposGerais, 65);
        }
    }

    private boolean extrairNomeComPadrao(Pattern padrao, String texto, EmpreendimentoDocumento doc, Integer pagina,
                                          Map<String, Object> camposGerais, int confianca) {
        Matcher m = padrao.matcher(texto);
        if (!m.find()) return false;
        String nome = m.group(1).trim();
        if (nome.isBlank() || nome.length() > 60) return false;
        Map<String, Object> identificacao = subMapa(camposGerais, "identificacao");
        setSeAusente(identificacao, "nome", nome, confianca, m.group(0), doc, pagina);
        return true;
    }

    /** Mantido para chamadas que não precisam desativar a extração de endereço (todas elegíveis por padrão). */
    public void extrair(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais, List<String> alertas) {
        extrair(texto, doc, pagina, true, camposGerais, alertas);
    }

    private void extrairEndereco(String texto, EmpreendimentoDocumento doc, Integer pagina, boolean elegivelParaEndereco,
                                  Map<String, Object> camposGerais, List<String> alertas) {
        if (!elegivelParaEndereco) return; // última página de um Book: nunca fonte do endereço/localização (§1/§3)
        boolean encontrado = extrairEnderecoComPadrao(ENDERECO_LINHA_UNICA, texto, doc, pagina, camposGerais, alertas, 75, 80, 80, 80);
        if (!encontrado) {
            encontrado = extrairEnderecoComPadrao(ENDERECO_DUAS_LINHAS, texto, doc, pagina, camposGerais, alertas, 70, 75, 75, 75);
        }
        if (!encontrado) {
            extrairEnderecoIsolado(texto, doc, pagina, camposGerais, alertas);
        }
        extrairRegiaoCidade(texto, doc, pagina, camposGerais, alertas);
    }

    private void extrairEnderecoIsolado(String texto, EmpreendimentoDocumento doc, Integer pagina,
                                         Map<String, Object> camposGerais, List<String> alertas) {
        Matcher m = ENDERECO_LINHA_ISOLADA.matcher(texto);
        while (m.find()) {
            if (contextoIndicaPride(texto, m.start(), m.end())) {
                alertas.add("Endereço \"" + m.group(0).trim()
                        + "\" ignorado: aparece junto a dados institucionais/comerciais da Pride, não do empreendimento.");
                continue;
            }
            // confiança abaixo do limiar de revisão (60%): sem cidade/UF na mesma linha para corroborar,
            // fica marcado com ★ na tela de revisão para confirmação humana antes de virar dado oficial.
            Map<String, Object> localizacao = subMapa(camposGerais, "localizacao");
            setSeAusente(localizacao, "endereco", m.group(1).trim(), 55, m.group(0), doc, pagina);
            setSeAusente(localizacao, "numero", m.group(2).trim(), 55, m.group(0), doc, pagina);
            if (m.group(3) != null) {
                setSeAusente(localizacao, "bairro", m.group(3).trim(), 55, m.group(0), doc, pagina);
            }
            return;
        }
    }

    private void extrairRegiaoCidade(String texto, EmpreendimentoDocumento doc, Integer pagina,
                                      Map<String, Object> camposGerais, List<String> alertas) {
        Matcher m = REGIAO_CIDADE.matcher(texto);
        while (m.find()) {
            if (contextoIndicaPride(texto, m.start(), m.end())) {
                alertas.add("Região/cidade \"" + m.group(0).trim()
                        + "\" ignorada: aparece junto a dados institucionais/comerciais da Pride, não do empreendimento.");
                continue;
            }
            Map<String, Object> localizacao = subMapa(camposGerais, "localizacao");
            setSeAusente(localizacao, "regiao", m.group(1).trim(), 65, m.group(0), doc, pagina);
            setSeAusente(localizacao, "cidade", m.group(2).trim(), 70, m.group(0), doc, pagina);
            return;
        }
    }

    private boolean extrairEnderecoComPadrao(Pattern padrao, String texto, EmpreendimentoDocumento doc, Integer pagina,
                                              Map<String, Object> camposGerais, List<String> alertas,
                                              int confEndereco, int confNumero, int confCidade, int confEstado) {
        Matcher m = padrao.matcher(texto);
        while (m.find()) {
            if (contextoIndicaPride(texto, m.start(), m.end())) {
                alertas.add("Endereço \"" + m.group(0).replaceAll("\\s+", " ").trim()
                        + "\" ignorado: aparece junto a dados institucionais/comerciais da Pride, não do empreendimento.");
                continue; // não usa como fonte, mas continua procurando outro candidato na mesma página
            }
            Map<String, Object> localizacao = subMapa(camposGerais, "localizacao");
            setSeAusente(localizacao, "endereco", m.group(1).trim(), confEndereco, m.group(0), doc, pagina);
            setSeAusente(localizacao, "numero", m.group(2).trim(), confNumero, m.group(0), doc, pagina);
            setSeAusente(localizacao, "cidade", m.group(3).trim(), confCidade, m.group(0), doc, pagina);
            setSeAusente(localizacao, "estado", m.group(4).trim(), confEstado, m.group(0), doc, pagina);
            return true;
        }
        return false;
    }

    private boolean contextoIndicaPride(String texto, int inicioMatch, int fimMatch) {
        int inicio = Math.max(0, inicioMatch - JANELA_CONTEXTO_ENDERECO);
        int fim = Math.min(texto.length(), fimMatch + JANELA_CONTEXTO_ENDERECO);
        return SINAL_INSTITUCIONAL_PRIDE.matcher(texto.substring(inicio, fim)).find();
    }

    private void extrairDiferenciais(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        if (camposGerais.containsKey("diferenciais")) return; // já encontrado em página/documento anterior

        Matcher mRotulo = DIFERENCIAIS_ROTULO_LINHA.matcher(texto);
        if (mRotulo.find()) {
            adicionarDiferenciais(camposGerais, splitDiferenciais(mRotulo.group(1), ","), mRotulo.group(0), doc, pagina, 70);
            return;
        }
        Matcher mBloco = DIFERENCIAIS_BLOCO.matcher(texto);
        if (mBloco.find()) {
            // um item por linha; itens quebrados em duas linhas são juntados, e a lista termina na primeira
            // linha que não é diferencial (no Book Felicce o slogan "O lugar onde / mora a felicidade." vem
            // colado logo abaixo, sem linha em branco)
            List<String> itens = itensDeDiferencial(java.util.Arrays.asList(mBloco.group(1).split("\\R")), true);
            if (!itens.isEmpty()) {
                adicionarDiferenciais(camposGerais, itens.toArray(String[]::new), mBloco.group(0), doc, pagina, 75);
                return;
            }
        }
        // rótulo DEPOIS da lista: o PDFBox lê o título "Diferenciais" por último quando ele está à direita
        // ou abaixo dos itens (Book Solare) — a lista é o que vem logo acima do rótulo
        String[] linhas = texto.split("\\R");
        for (int i = 0; i < linhas.length; i++) {
            if (!ROTULO_DIFERENCIAIS.matcher(linhas[i]).matches()) continue;
            java.util.LinkedList<String> acima = new java.util.LinkedList<>();
            for (int j = i - 1; j >= 0; j--) {
                String item = ultimoSegmento(linhas[j]);
                if (item.isEmpty() || !ehDiferencial(item)) break;
                acima.addFirst(item);
            }
            if (!acima.isEmpty()) {
                String trecho = String.join("\n", acima) + "\n" + linhas[i].trim();
                adicionarDiferenciais(camposGerais, acima.toArray(String[]::new), trecho, doc, pagina, 70);
                return;
            }
        }
    }

    /**
     * Diferenciais de um Book sem o rótulo "Diferenciais" (ex.: Book Marbella): nos books reais a página
     * de diferenciais vem logo depois do mapa geral dos blocos (implantação). Chamado pelo orquestrador só
     * para essa página, e só aceita as linhas que realmente descrevem um diferencial.
     */
    public void extrairDiferenciaisSemRotulo(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        if (camposGerais.containsKey("diferenciais")) return;
        List<String> itens = itensDeDiferencial(java.util.Arrays.asList(texto.split("\\R")), false);
        if (itens.size() < 2) return; // um item isolado pode ser só uma frase da planta
        adicionarDiferenciais(camposGerais, itens.toArray(String[]::new), String.join("\n", itens), doc, pagina, 60);
    }

    /**
     * Junta linhas quebradas ("4 apartamentos" + "por andar.") e devolve só os itens de diferencial.
     * Com {@code pararNoPrimeiroInvalido}, a lista acaba no primeiro item que não é diferencial.
     */
    static List<String> itensDeDiferencial(List<String> linhas, boolean pararNoPrimeiroInvalido) {
        List<String> juntas = new java.util.ArrayList<>();
        for (String bruta : linhas) {
            String linha = limparItem(bruta);
            if (linha.isEmpty()) continue;
            boolean continua = !juntas.isEmpty()
                    && (Character.isLowerCase(linha.charAt(0)) || TERMINA_EM_CONECTIVO.matcher(juntas.get(juntas.size() - 1)).find())
                    && !linha.startsWith("*");
            if (continua) juntas.set(juntas.size() - 1, juntas.get(juntas.size() - 1) + " " + linha);
            else juntas.add(linha);
        }
        List<String> itens = new java.util.ArrayList<>();
        for (String j : juntas) {
            String item = j.replaceAll("\\.+$", "").trim();
            if (ehDiferencial(item)) itens.add(item);
            else if (pararNoPrimeiroInvalido) break; // a lista começa logo abaixo do rótulo; o resto é outra coisa
        }
        return itens;
    }

    static boolean ehDiferencial(String item) {
        return item.length() <= 100 && TERMO_DIFERENCIAL.matcher(item).find() && !NAO_DIFERENCIAL.matcher(item).find();
    }

    /** Remove marcadores ("●", "•", "-") e espaços; " " conta como espaço. */
    private static String limparItem(String linha) {
        return linha.replace(' ', ' ').replaceFirst("^[\\s●•▪·\\-–]+", "").trim().replaceAll("\\s{2,}", " ");
    }

    /** "…informações detalhadas.  4 unidades por andar" → "4 unidades por andar" (texto vizinho colado na mesma linha). */
    private static String ultimoSegmento(String linha) {
        String[] partes = linha.replace(' ', ' ').trim().split("\\s{2,}");
        return limparItem(partes[partes.length - 1]).replaceAll("\\.+$", "").trim();
    }

    private String[] splitDiferenciais(String bruto, String separador) {
        return bruto.split(Pattern.quote(separador));
    }

    private void adicionarDiferenciais(Map<String, Object> camposGerais, String[] itens, String trecho, EmpreendimentoDocumento doc, Integer pagina, int confianca) {
        var lista = new java.util.ArrayList<Map<String, Object>>();
        for (String item : itens) {
            String titulo = item.trim();
            if (titulo.isBlank() || titulo.length() > 100) continue;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("titulo", campo(titulo, confianca, trecho, doc, pagina));
            lista.add(d);
        }
        if (!lista.isEmpty()) camposGerais.put("diferenciais", lista);
    }

    private void extrairCaracteristicas(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        Matcher mQuartos = QUARTOS.matcher(texto);
        if (mQuartos.find()) {
            Map<String, Object> caracteristicas = subMapa(camposGerais, "caracteristicas");
            setSeAusente(caracteristicas, "quartos_min", mQuartos.group(1), 65, mQuartos.group(0), doc, pagina);
            setSeAusente(caracteristicas, "quartos_max", mQuartos.group(1), 65, mQuartos.group(0), doc, pagina);
        }

        // metragem: só assume valores quando todas as ocorrências da página têm a MESMA área base — evita
        // adivinhar qual das várias tipologias de uma planta é "a" metragem do empreendimento. Dentro de
        // uma mesma área base, algumas variantes da unidade somam uma área adicional (garden/varanda) e
        // outras não — min = o menor total real (a base sozinha, quando existe uma variante sem adicional)
        // e max = o maior total real (base + a maior área adicional encontrada). Nunca combina bases
        // diferentes nem inventa uma soma que não apareceu explicitamente no texto.
        Matcher mMetragem = METRAGEM.matcher(texto);
        java.util.LinkedHashSet<String> basesDistintas = new java.util.LinkedHashSet<>();
        List<java.math.BigDecimal> totais = new java.util.ArrayList<>();
        List<String> trechosMetragem = new java.util.ArrayList<>();
        while (mMetragem.find()) {
            basesDistintas.add(mMetragem.group(1));
            java.math.BigDecimal total = parseDecimalBr(mMetragem.group(1));
            if (mMetragem.group(2) != null) total = total.add(parseDecimalBr(mMetragem.group(2)));
            totais.add(total);
            trechosMetragem.add(mMetragem.group(0));
        }
        if (basesDistintas.size() == 1 && !totais.isEmpty()) {
            int idxMin = 0, idxMax = 0;
            for (int i = 1; i < totais.size(); i++) {
                if (totais.get(i).compareTo(totais.get(idxMin)) < 0) idxMin = i;
                if (totais.get(i).compareTo(totais.get(idxMax)) > 0) idxMax = i;
            }
            Map<String, Object> caracteristicas = subMapa(camposGerais, "caracteristicas");
            setSeAusente(caracteristicas, "metragem_min", formatarDecimalBr(totais.get(idxMin)), 70, trechosMetragem.get(idxMin), doc, pagina);
            setSeAusente(caracteristicas, "metragem_max", formatarDecimalBr(totais.get(idxMax)), 70, trechosMetragem.get(idxMax), doc, pagina);
        }

        Matcher mPav = PAVIMENTOS.matcher(texto);
        if (mPav.find()) {
            Map<String, Object> caracteristicas = subMapa(camposGerais, "caracteristicas");
            setSeAusente(caracteristicas, "pavimentos", mPav.group(1), 80, mPav.group(0), doc, pagina);
        }
        // elevador é procurado na página inteira, não só perto de "Torres de N pavimentos": alguns books
        // reais (ex.: Felicce) dizem apenas "Torres com elevador", sem informar a quantidade de pavimentos —
        // amarrar a busca ao match de PAVIMENTOS perdia essa informação real por completo.
        Matcher mElevador = ELEVADOR.matcher(texto);
        if (mElevador.find()) {
            Map<String, Object> caracteristicas = subMapa(camposGerais, "caracteristicas");
            setSeAusente(caracteristicas, "possui_elevador", "true", 75, mElevador.group(0), doc, pagina);
        }

        Matcher mUnidadesAndar = UNIDADES_POR_ANDAR.matcher(texto);
        if (mUnidadesAndar.find()) {
            Map<String, Object> caracteristicas = subMapa(camposGerais, "caracteristicas");
            setSeAusente(caracteristicas, "unidades_por_andar", mUnidadesAndar.group(1), 80, mUnidadesAndar.group(0), doc, pagina);
        }

        Matcher mVagas = VAGAS_GARAGEM.matcher(texto);
        if (mVagas.find()) {
            Map<String, Object> caracteristicas = subMapa(camposGerais, "caracteristicas");
            setSeAusente(caracteristicas, "vagas_min", mVagas.group(1), 75, mVagas.group(0), doc, pagina);
            setSeAusente(caracteristicas, "vagas_max", mVagas.group(1), 75, mVagas.group(0), doc, pagina);
        }
    }

    private void extrairAreasComuns(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        if (camposGerais.containsKey("areas_comuns")) return;
        Matcher m = AREAS_COMUNS_BLOCO.matcher(texto);
        if (!m.find()) return;
        var lista = new java.util.ArrayList<Map<String, Object>>();
        for (String linha : m.group(1).split("\n")) {
            String nome = linha.trim();
            if (nome.isBlank() || nome.length() > 60) continue;
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("nome", campo(nome, 70, m.group(0), doc, pagina));
            lista.add(a);
        }
        if (!lista.isEmpty()) camposGerais.put("areas_comuns", lista);
    }

    /**
     * Áreas comuns de um Book sem o rótulo "ÁREAS COMUNS" (ex.: Book Solare): procura na página uma
     * lista de linhas curtas seguidas em que a maioria é item de lazer conhecido (guarita, playground,
     * quiosque...). Chamado pelo orquestrador, a partir das primeiras páginas do book.
     */
    public void extrairAreasComunsSemRotulo(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        if (camposGerais.containsKey("areas_comuns")) return;
        List<String> melhor = List.of();
        List<String> atual = new java.util.ArrayList<>();
        for (String bruta : (texto + "\n.").split("\\R")) { // "." final fecha a última lista
            String linha = limparItem(bruta);
            if (linha.isEmpty()) continue; // o PDFBox às vezes põe linha em branco entre itens espaçados
            boolean itemCurto = linha.length() <= 50 && !linha.startsWith("*")
                    && !linha.endsWith(".") && linha.split(" ").length <= 7;
            if (itemCurto) { atual.add(linha); continue; }
            if (listaDeLazer(atual) && atual.size() > melhor.size()) melhor = atual;
            atual = new java.util.ArrayList<>();
        }
        if (melhor.isEmpty()) return;
        var lista = new java.util.ArrayList<Map<String, Object>>();
        String trecho = String.join("\n", melhor);
        for (String nome : melhor) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("nome", campo(nome, 65, trecho, doc, pagina));
            lista.add(a);
        }
        camposGerais.put("areas_comuns", lista);
    }

    private static boolean listaDeLazer(List<String> linhas) {
        if (linhas.size() < 3) return false;
        long conhecidas = linhas.stream().filter(l -> VOCABULARIO_LAZER.matcher(l).find()).count();
        return conhecidas >= 3 && conhecidas * 10 >= linhas.size() * 7; // pelo menos 70% da lista
    }

    @SuppressWarnings("unchecked")
    private void extrairPontosReferencia(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        var existentes = (java.util.List<?>) camposGerais.get("pontos_referencia");
        var lista = existentes != null
                ? new java.util.ArrayList<Map<String, Object>>((java.util.List<Map<String, Object>>) (java.util.List<?>) existentes)
                : new java.util.ArrayList<Map<String, Object>>();
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (Map<String, Object> p : lista) vistos.add(chaveNome(valorDe(p.get("nome"))));
        int antes = lista.size();

        // "Nome (2 min)" vale sozinho; os formatos sem parênteses só valem em lista (2+ na mesma página)
        adicionarPontos(PONTO_REFERENCIA.matcher(texto), 1, false, lista, vistos, doc, pagina, 75);
        adicionarPontos(PONTO_REFERENCIA_SEM_PARENTESES.matcher(texto), 2, false, lista, vistos, doc, pagina, 70);
        adicionarPontos(PONTO_REFERENCIA_DISTANCIA.matcher(texto), 2, true, lista, vistos, doc, pagina, 70);

        if (lista.size() > antes) camposGerais.put("pontos_referencia", lista);
    }

    private void adicionarPontos(Matcher m, int minimoNaPagina, boolean porDistancia, List<Map<String, Object>> lista,
                                 java.util.Set<String> vistos, EmpreendimentoDocumento doc, Integer pagina, int confianca) {
        List<Map<String, Object>> novos = new java.util.ArrayList<>();
        java.util.Set<String> vistosAqui = new java.util.HashSet<>();
        while (m.find()) {
            String nome = m.group(1).trim().replaceAll("[\\s\\-–:]+$", "");
            String chave = chaveNome(nome);
            if (nome.length() < 3 || nome.length() > 80 || !nome.matches(".*\\p{L}.*")) continue;
            if (vistos.contains(chave) || !vistosAqui.add(chave)) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("nome", campo(nome, confianca, m.group(0).trim(), doc, pagina));
            if (porDistancia) {
                p.put("distancia", campo(m.group(2), confianca, m.group(0).trim(), doc, pagina));
                p.put("unidade", campo(m.group(3).toLowerCase(java.util.Locale.ROOT), confianca, m.group(0).trim(), doc, pagina));
            } else {
                p.put("tempo", campo(m.group(2), confianca, m.group(0).trim(), doc, pagina));
            }
            String categoria = categoriaDoPonto(nome);
            if (categoria != null) p.put("categoria", campo(categoria, confianca, m.group(0).trim(), doc, pagina));
            novos.add(p);
        }
        if (novos.size() < minimoNaPagina) return;
        lista.addAll(novos);
        vistos.addAll(vistosAqui);
    }

    private static final Pattern CAT_SAUDE = Pattern.compile("saude|\\bubs\\b|hospital|\\bupa\\b|clinica|farmacia|drogaria|pronto[- ]atendimento");
    private static final Pattern CAT_EDUCACAO = Pattern.compile("escola|colegio|\\bcei\\b|\\bcmei\\b|creche|faculdade|universidade|utfpr|\\buel\\b|unopar|unicesumar|\\bifpr\\b|senai|sesi|ensino");
    private static final Pattern CAT_COMPRAS = Pattern.compile("mercado|muffato|condor|feira|atacad|hiper|padaria|\\bsuper\\b|shopping|loja|comercio|tonhao");
    private static final Pattern CAT_TRANSPORTE = Pattern.compile("terminal|onibus|rodovia|\\brod\\b|\\bbr[ -]?\\d|\\bpr[ -]?\\d|^av\\b|^av\\.|avenida|aeroporto|rodoviaria|estacao|centro\\b");
    private static final Pattern CAT_LAZER = Pattern.compile("parque|praca|lago|bosque|clube|estadio|igreja|catedral");

    /** Categoria do ponto pelo nome — a tela agrupa e mostra um ícone por categoria. Null se não reconhecer. */
    static String categoriaDoPonto(String nome) {
        String n = java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        if (CAT_SAUDE.matcher(n).find()) return "Saúde";
        if (CAT_EDUCACAO.matcher(n).find()) return "Educação";
        if (CAT_COMPRAS.matcher(n).find()) return "Compras";
        if (CAT_LAZER.matcher(n).find()) return "Lazer";
        if (CAT_TRANSPORTE.matcher(n).find()) return "Transporte";
        return null;
    }

    private static String chaveNome(String nome) {
        if (nome == null) return "";
        return java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    @SuppressWarnings("unchecked")
    private static String valorDe(Object campo) {
        return campo instanceof Map<?, ?> m && m.get("valor") != null ? String.valueOf(m.get("valor")) : null;
    }

    private java.math.BigDecimal parseDecimalBr(String valor) {
        return new java.math.BigDecimal(valor.replace(",", "."));
    }

    private String formatarDecimalBr(java.math.BigDecimal valor) {
        return valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> subMapa(Map<String, Object> pai, String chave) {
        return (Map<String, Object>) pai.computeIfAbsent(chave, k -> new LinkedHashMap<String, Object>());
    }

    private void setSeAusente(Map<String, Object> mapa, String chave, String valor, int confianca, String trecho, EmpreendimentoDocumento doc, Integer pagina) {
        if (mapa.containsKey(chave)) return; // não sobrescreve dado já encontrado por outra página/documento
        mapa.put(chave, campo(valor, confianca, trecho, doc, pagina));
    }

    private Map<String, Object> campo(String valor, int confianca, String trecho, EmpreendimentoDocumento doc, Integer pagina) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valor", valor);
        m.put("confianca", confianca);
        m.put("pagina", pagina);
        m.put("trecho_original", trecho);
        m.put("documento_nome", doc.getNomeOriginal());
        return m;
    }
}

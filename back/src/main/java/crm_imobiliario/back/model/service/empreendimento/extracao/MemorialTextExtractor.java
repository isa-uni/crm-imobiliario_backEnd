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

    // "39,50m²" — área privativa mencionada em texto corrido de planta. Não casa com "+ 9,09m²"
    // (área adicional/garden), que fica deliberadamente fora do escopo automático (não somar áreas — §9).
    // Sem "\b" no final: "²" (U+00B2) não é considerado caractere de palavra pelo \w/\b do regex Java em
    // modo ASCII, então uma fronteira de palavra logo após "²" nunca é satisfeita e o casamento falha
    // sempre — foi assim que o valor real "39,50m²" deixou de ser reconhecido durante os testes.
    private static final Pattern METRAGEM = Pattern.compile("(?<!\\+\\s{0,3})(\\d{2,3},\\d{2})\\s*m[²2](?![\\p{L}\\p{N}])");

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
            "(?im)^\\s*[ÁA]REAS\\s+COMUNS\\s*$((?:\\R(?!\\s*$)(?!\\s*\\*).+)*)");

    // "Colégio Estadual Cívico Militar (2 min)" — ponto de referência com tempo estimado até o local.
    private static final Pattern PONTO_REFERENCIA = Pattern.compile(
            "(?m)^\\s*([^\\n(]{3,70}?)\\s*\\((\\d{1,3})\\s*min\\)\\s*$");

    public void extrair(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais, List<String> alertas) {
        extrairEndereco(texto, doc, pagina, camposGerais);
        extrairDiferenciais(texto, doc, pagina, camposGerais);
        extrairCaracteristicas(texto, doc, pagina, camposGerais);
        extrairAreasComuns(texto, doc, pagina, camposGerais);
        extrairPontosReferencia(texto, doc, pagina, camposGerais);
    }

    private void extrairEndereco(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        Matcher m = ENDERECO_LINHA_UNICA.matcher(texto);
        if (m.find()) {
            Map<String, Object> localizacao = subMapa(camposGerais, "localizacao");
            setSeAusente(localizacao, "endereco", m.group(1).trim(), 75, m.group(0), doc, pagina);
            setSeAusente(localizacao, "numero", m.group(2).trim(), 80, m.group(0), doc, pagina);
            setSeAusente(localizacao, "cidade", m.group(3).trim(), 80, m.group(0), doc, pagina);
            setSeAusente(localizacao, "estado", m.group(4).trim(), 80, m.group(0), doc, pagina);
            return;
        }
        Matcher m2 = ENDERECO_DUAS_LINHAS.matcher(texto);
        if (m2.find()) {
            Map<String, Object> localizacao = subMapa(camposGerais, "localizacao");
            setSeAusente(localizacao, "endereco", m2.group(1).trim(), 70, m2.group(0), doc, pagina);
            setSeAusente(localizacao, "numero", m2.group(2).trim(), 75, m2.group(0), doc, pagina);
            setSeAusente(localizacao, "cidade", m2.group(3).trim(), 75, m2.group(0), doc, pagina);
            setSeAusente(localizacao, "estado", m2.group(4).trim(), 75, m2.group(0), doc, pagina);
        }
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
            adicionarDiferenciais(camposGerais, splitDiferenciais(mBloco.group(1), "\n"), mBloco.group(0), doc, pagina, 75);
        }
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

        // metragem: só assume um valor quando todas as ocorrências da página concordam — evita
        // adivinhar qual das várias tipologias de uma planta é "a" metragem do empreendimento.
        Matcher mMetragem = METRAGEM.matcher(texto);
        java.util.LinkedHashSet<String> valoresDistintos = new java.util.LinkedHashSet<>();
        String primeiroTrecho = null;
        while (mMetragem.find()) {
            valoresDistintos.add(mMetragem.group(1));
            if (primeiroTrecho == null) primeiroTrecho = mMetragem.group(0);
        }
        if (valoresDistintos.size() == 1) {
            Map<String, Object> caracteristicas = subMapa(camposGerais, "caracteristicas");
            String valor = valoresDistintos.iterator().next();
            setSeAusente(caracteristicas, "metragem_min", valor, 70, primeiroTrecho, doc, pagina);
            setSeAusente(caracteristicas, "metragem_max", valor, 70, primeiroTrecho, doc, pagina);
        }

        Matcher mPav = PAVIMENTOS.matcher(texto);
        if (mPav.find()) {
            Map<String, Object> caracteristicas = subMapa(camposGerais, "caracteristicas");
            setSeAusente(caracteristicas, "pavimentos", mPav.group(1), 80, mPav.group(0), doc, pagina);
            boolean temElevador = ELEVADOR.matcher(texto.substring(mPav.end(), Math.min(texto.length(), mPav.end() + 30))).find();
            if (temElevador) {
                setSeAusente(caracteristicas, "possui_elevador", "true", 80, mPav.group(0), doc, pagina);
            }
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

    private void extrairPontosReferencia(String texto, EmpreendimentoDocumento doc, Integer pagina, Map<String, Object> camposGerais) {
        Matcher m = PONTO_REFERENCIA.matcher(texto);
        var existentes = (java.util.List<?>) camposGerais.get("pontos_referencia");
        var lista = existentes != null
                ? new java.util.ArrayList<Map<String, Object>>((java.util.List<Map<String, Object>>) (java.util.List<?>) existentes)
                : new java.util.ArrayList<Map<String, Object>>();
        boolean encontrouAlgum = false;
        while (m.find()) {
            String nome = m.group(1).trim();
            if (nome.isBlank() || nome.length() > 80) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("nome", campo(nome, 75, m.group(0), doc, pagina));
            p.put("tempo", campo(m.group(2), 75, m.group(0), doc, pagina));
            lista.add(p);
            encontrouAlgum = true;
        }
        if (encontrouAlgum) camposGerais.put("pontos_referencia", lista);
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

package crm_imobiliario.back.model.service.empreendimento;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Lê empreendimentos do Guia de Bolso — porta de guia_parser.py.
 * Tenta: 1) guia-de-bolso_empreendimentos.json 2) guia-de-bolso.html (DB.empreendimentos.push)
 */
@Slf4j
@Service
public class GuiaParserService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Normalizer normalizer;

    public GuiaParserService(Normalizer normalizer) {
        this.normalizer = normalizer;
    }

    public List<EmpreendimentoGuia> carregar(Path jsonPath, Path htmlPath) {
        // Tenta JSON primeiro (mais barato)
        if (jsonPath != null && Files.exists(jsonPath)) {
            try {
                String json = Files.readString(jsonPath, StandardCharsets.UTF_8);
                List<EmpreendimentoGuia> list = mapper.readValue(json, new TypeReference<>() {});
                log.info("Guia carregado via JSON: {} empreendimentos de {}", list.size(), jsonPath);
                return list;
            } catch (Exception e) {
                log.warn("Falha ao ler JSON Guia {}: {}", jsonPath, e.getMessage());
            }
        }
        if (htmlPath != null && Files.exists(htmlPath)) {
            try {
                String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
                List<EmpreendimentoGuia> list = extrairDoHtml(html);
                log.info("Guia carregado via HTML: {} empreendimentos de {}", list.size(), htmlPath);
                return list;
            } catch (Exception e) {
                log.warn("Falha ao ler HTML Guia {}: {}", htmlPath, e.getMessage());
            }
        }
        log.warn("Nenhum Guia encontrado (json={}, html={})", jsonPath, htmlPath);
        return List.of();
    }

    private List<EmpreendimentoGuia> extrairDoHtml(String html) throws IOException {
        List<EmpreendimentoGuia> out = new ArrayList<>();
        Pattern p = Pattern.compile("DB\\.empreendimentos\\.push\\(\\s*(\\{.*?\\})\\s*\\)", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String literal = m.group(1);
            if (!literal.contains("\"id\"") || !literal.contains("\"slug\"")) continue;
            try {
                EmpreendimentoGuia g = mapper.readValue(literal, EmpreendimentoGuia.class);
                if (g.slug != null) out.add(g);
            } catch (Exception e) {
                log.warn("Bloco Guia ignorado: {}", e.getMessage());
            }
        }
        return out;
    }

    // DTO espelho do JSON do Guia — apenas campos usados na capa/detalhe
    public static class EmpreendimentoGuia {
        public String id;
        public String slug;
        public String nome;
        public String status;
        public String regiao;
        public String bairro;
        public String cidade;
        public String uf;
        public String endereco;
        public Double metragem_min;
        public Double metragem_max;
        public Long preco_min;
        public Long preco_max;
        public String descricao_resumo;
        public List<String> resumo_10s;
        public String condicoes_comerciais;
        public String plantas;
        public List<String> pontos_referencia;
        public List<AreaComum> areas_comuns;
        public List<Diferencial> diferenciais;
        public String previsao_entrega;
        public String entrega_contratual;
        public Integer parcelamento_max;
        public Geo geo;
        public Tabela tabela;

        public static class Geo {
            public Double lat;
            public Double lng;
            public Integer zoom;
        }
        public static class Tabela {
            public String referencia;
            public String validade;
        }
        public static class AreaComum {
            public String nome;
            public String icone;
        }
        public static class Diferencial {
            public String titulo;
            public String descricao;
            public String icone;
        }
    }
}

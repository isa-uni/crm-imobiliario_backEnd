package crm_imobiliario.back.model.service.empreendimento;

import java.util.List;
import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;

public interface EmpreendimentoExtractionService {
    ExtractionResult extract(List<EmpreendimentoDocumento> documentos);

    class CampoEvidencia {
        public String valor;
        public Integer confianca;
        public Integer pagina;
        public String trechoOriginal;
        public String documentoNome;
        public CampoEvidencia() {}
        public CampoEvidencia(String valor, Integer confianca, Integer pagina, String trecho, String doc) {
            this.valor = valor; this.confianca = confianca; this.pagina = pagina; this.trechoOriginal = trecho; this.documentoNome = doc;
        }
    }

    class ExtractionResult {
        public Object resultadoJson; // map estruturado
        public List<EmpreendimentoFonteData> fontes;
        public String modelo;
        public List<String> avisosTruncamento;
    }
    class EmpreendimentoFonteData {
        public String campo;
        public String valorExtraido;
        public Integer pagina;
        public String trecho;
        public Integer confianca;
        public String documentoNome;
        public Long documentoId;
    }
}

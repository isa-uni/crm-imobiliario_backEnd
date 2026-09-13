package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado bruto (não interpretado) da extração de uma tabela de um documento
 * — uma linha de cabeçalho e N linhas de dados, célula a célula, com a origem
 * de cada linha. Não decide significado de coluna; isso é papel do
 * {@link UnidadeTableMapper}.
 */
public class TabelaBruta {

    public List<String> cabecalhos = new ArrayList<>();
    public List<Linha> linhas = new ArrayList<>();

    /** Página (PDF, 1-based) ou aba (planilha) de onde a tabela foi lida. */
    public Integer pagina;
    public String aba;

    public static class Linha {
        public List<String> celulas = new ArrayList<>();
        /** Número da linha na tabela de origem (1-based, conta a partir da primeira linha de dados). */
        public int linhaOrigem;
    }
}

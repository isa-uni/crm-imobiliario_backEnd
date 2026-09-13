package crm_imobiliario.back.model.service.empreendimento.extracao;

/**
 * Campos canônicos de uma linha de tabela de unidades — catálogo de referência
 * do §7 do spec de importação. Colunas de documentos reais (Pride/CVCRM)
 * variam em presença e ordem; o mapeamento é sempre por nome de cabeçalho
 * (ver {@link UnidadeHeaderNormalizer}), nunca por posição.
 */
public enum CampoUnidade {
    BLOCO,
    TORRE,
    UNIDADE,
    AREA_PRIVATIVA,
    TIPOLOGIA,
    AREA_COMUM,
    OUTRAS_AREAS,
    GARAGEM,
    SITUACAO,
    VALOR_TOTAL,
    ATO,
    SUBSIDIO_COHAPAR,
    FINANCIAMENTO,
    VALOR_AVALIACAO
}

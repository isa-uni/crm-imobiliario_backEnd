package crm_imobiliario.back.model.dto;

import java.time.LocalDateTime;

import crm_imobiliario.back.model.entity.Lead;

public record LeadListaDTO(
        Long id,
        String nome,
        String telefone,
        String email,
        String origem,
        String historico,
        String status,
        Long valorInteresse,
        String observacao,
        String motivoDescarte,
        Boolean ativo,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao,
        String corretor_responsavel,
        Long corretorId,
        String corretorNome,
        Long equipeId,
        String equipeNome,
        String statusAtribuicao,
        Long imovelId,
        String imovelTitulo
) {
    public static LeadListaDTO from(Lead l) {
        return new LeadListaDTO(
                l.getId(),
                l.getNome(),
                l.getTelefone(),
                l.getEmail(),
                l.getOrigem(),
                l.getHistorico(),
                l.getStatus(),
                l.getValorInteresse(),
                l.getObservacao(),
                l.getMotivoDescarte(),
                l.getAtivo(),
                l.getDataCriacao(),
                l.getDataAtualizacao(),
                l.getCorretor_responsavel(),
                l.getCorretor() != null ? l.getCorretor().getId() : null,
                l.getCorretor() != null ? l.getCorretor().getNome() : null,
                l.getEquipe() != null ? l.getEquipe().getId() : null,
                l.getEquipe() != null ? l.getEquipe().getNome() : null,
                l.getStatusAtribuicao(),
                l.getImovel() != null ? l.getImovel().getId() : null,
                l.getImovel() != null ? l.getImovel().getTitulo() : null
        );
    }
}

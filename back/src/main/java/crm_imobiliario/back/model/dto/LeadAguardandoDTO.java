package crm_imobiliario.back.model.dto;

import java.time.LocalDateTime;

import crm_imobiliario.back.model.entity.Lead;

public record LeadAguardandoDTO(
        Long id,
        String nome,
        String email,
        String telefone,
        Long equipeId,
        String equipeNome,
        String statusAtribuicao,
        LocalDateTime dataAtualizacao,
        Long corretorAnteriorId,
        String corretorAnteriorNome,
        String motivoDesligamento,
        LocalDateTime dataDesligamento,
        String origem,
        String status
) {
    public static LeadAguardandoDTO from(Lead lead, crm_imobiliario.back.model.entity.LeadResponsavelHistorico ultimo, crm_imobiliario.back.model.entity.LeadResponsavelHistorico anterior) {
        Long equipeId = lead.getEquipe() != null ? lead.getEquipe().getId() : null;
        String equipeNome = lead.getEquipe() != null ? lead.getEquipe().getNome() : null;
        Long corrAntId = anterior != null && anterior.getCorretor() != null ? anterior.getCorretor().getId()
                : (ultimo != null && ultimo.getCorretor() != null ? null : null);
        // corretorAnterior é penúltimo se existir, senão tenta extrair do histórico
        String corrAntNome = null;
        if (anterior != null && anterior.getCorretor() != null) corrAntNome = anterior.getCorretor().getNome();
        else if (anterior != null) corrAntNome = null;

        String motivo = ultimo != null ? ultimo.getMotivo() : "DESLIGAMENTO_CORRETOR";
        LocalDateTime dataDeslig = ultimo != null ? ultimo.getDataInicio() : lead.getDataAtualizacao();

        // se anterior existe, usa nome dele
        if (anterior != null && anterior.getCorretor() != null) {
            corrAntId = anterior.getCorretor().getId();
            corrAntNome = anterior.getCorretor().getNome();
        } else if (anterior == null && ultimo != null && ultimo.getMotivo() != null && ultimo.getMotivo().equals("DESLIGAMENTO_CORRETOR")) {
            // não há anterior, mantém null
        }

        return new LeadAguardandoDTO(
                lead.getId(),
                lead.getNome(),
                lead.getEmail(),
                lead.getTelefone(),
                equipeId,
                equipeNome,
                lead.getStatusAtribuicao(),
                lead.getDataAtualizacao(),
                corrAntId,
                corrAntNome,
                motivo,
                dataDeslig,
                lead.getOrigem(),
                lead.getStatus()
        );
    }

    public static LeadAguardandoDTO fromSimple(Lead lead) {
        Long equipeId = lead.getEquipe() != null ? lead.getEquipe().getId() : null;
        String equipeNome = lead.getEquipe() != null ? lead.getEquipe().getNome() : null;
        return new LeadAguardandoDTO(
                lead.getId(), lead.getNome(), lead.getEmail(), lead.getTelefone(),
                equipeId, equipeNome, lead.getStatusAtribuicao(), lead.getDataAtualizacao(),
                null, null, "DESLIGAMENTO_CORRETOR", lead.getDataAtualizacao(),
                lead.getOrigem(), lead.getStatus()
        );
    }
}

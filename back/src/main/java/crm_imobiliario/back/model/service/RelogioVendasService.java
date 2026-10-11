package crm_imobiliario.back.model.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import crm_imobiliario.back.model.dto.ProporcaoFunilDTO;
import crm_imobiliario.back.model.dto.RelogioVendasDTO;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.ProporcaoFunil;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.ProporcaoFunilRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;
import crm_imobiliario.back.util.RegraNegocioException;

/**
 * Relógio de Vendas — sempre dos clientes do próprio usuário (qualquer papel), no mês atual.
 * A regra de contagem é a mesma do Dashboard do Gestor ({@link ContagemFunil}).
 */
@Service
public class RelogioVendasService {

    @Autowired private LeadRepository leadRepository;
    @Autowired private TramitacaoRepository tramitacaoRepository;
    @Autowired private ProporcaoFunilRepository proporcaoRepository;

    public ProporcaoFunil proporcoes() {
        return proporcaoRepository.findById(ProporcaoFunil.ID_UNICO).orElseGet(ProporcaoFunil::padrao);
    }

    /** Admin ajusta as proporções; precisam seguir a ordem do funil (cada etapa ≥ a seguinte). */
    @Transactional
    public ProporcaoFunil atualizarProporcoes(ProporcaoFunilDTO dto, Usuario admin) {
        int[] v = { dto.leads(), dto.oportunidades(), dto.visitasAgendadas(), dto.visitasRealizadas(), dto.pastas(), dto.aprovados() };
        String[] campos = { "leads", "oportunidades", "visitasAgendadas", "visitasRealizadas", "pastas", "aprovados" };
        String[] nomes = { "Leads", "Oportunidades", "Visitas agendadas", "Visitas realizadas", "Pastas", "Aprovados" };
        for (int i = 1; i < v.length; i++) {
            if (v[i] > v[i - 1]) {
                throw new RegraNegocioException(nomes[i] + " (" + v[i] + ") não pode ser maior que " + nomes[i - 1].toLowerCase()
                        + " (" + v[i - 1] + "): o funil vai estreitando a cada etapa.", campos[i], "INVALID_FUNNEL_RATIO");
            }
        }
        ProporcaoFunil p = proporcoes();
        p.setId(ProporcaoFunil.ID_UNICO);
        p.setLeads(dto.leads());
        p.setOportunidades(dto.oportunidades());
        p.setVisitasAgendadas(dto.visitasAgendadas());
        p.setVisitasRealizadas(dto.visitasRealizadas());
        p.setPastas(dto.pastas());
        p.setAprovados(dto.aprovados());
        p.setDataAtualizacao(LocalDateTime.now());
        p.setAtualizadoPor(admin);
        return proporcaoRepository.save(p);
    }

    @Transactional(readOnly = true)
    public RelogioVendasDTO resumo(Usuario usuario, LocalDateTime agora) {
        YearMonth mesAtual = YearMonth.from(agora);
        List<Lead> leads = leadRepository.findByCorretorId(usuario.getId());
        Map<Long, List<Tramitacao>> trams = leads.isEmpty() ? Map.of()
                : tramitacaoRepository.findByLeadIdInOrderByDataMovimentacaoAsc(leads.stream().map(Lead::getId).toList())
                        .stream().collect(Collectors.groupingBy(t -> t.getLead().getId()));

        List<ContagemFunil.Avaliado> naContagem = leads.stream()
                .map(l -> new ContagemFunil.Avaliado(l, ContagemFunil.avaliar(l, trams.getOrDefault(l.getId(), List.of()), mesAtual, agora, true)))
                .filter(a -> a.situacao().entra())
                .toList();
        return new RelogioVendasDTO(mesAtual.toString(), ProporcaoFunilDTO.from(proporcoes()), naContagem.size(),
                ContagemFunil.etapas(naContagem, agora));
    }
}

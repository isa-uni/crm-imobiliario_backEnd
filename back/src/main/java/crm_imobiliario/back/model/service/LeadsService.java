package crm_imobiliario.back.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.dto.LeadAtualizacaoDTO;
import crm_imobiliario.back.model.dto.LeadsDTO;
import crm_imobiliario.back.model.dto.MetricsDTO;
import crm_imobiliario.back.model.entity.Imovel;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.repository.ImovelRepository;
import crm_imobiliario.back.model.repository.LeadRepository;

@Service
public class LeadsService {
    
    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private ImovelRepository imovelRepository;

    public Lead cadastrarLeads(LeadsDTO dto) {

        Lead lead = new Lead();

        lead.setNome(dto.getNome());
        lead.setEmail(dto.getEmail());
        lead.setTelefone(dto.getTelefone());
        lead.setOrigem(dto.getOrigem());
        lead.setStatus(dto.getStatus());
        lead.setValorInteresse(dto.getValorInteresse());
        lead.setObservacao(dto.getObservacao());
        lead.setAtivo(true);
        
        Imovel imovel = imovelRepository.findById(dto.getImovelId())
                .orElseThrow(() -> new RuntimeException("Imóvel não encontrado"));

        lead.setImovel(imovel);

        try {
            return leadRepository.save(lead);

        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Lead já cadastrado");
        }
    }

    public Lead atualizarLeads(Long id, LeadAtualizacaoDTO dto) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead não encontrado"));

            
        if (dto.getNome() != null) {
            lead.setNome(dto.getNome());
        }

        if (dto.getEmail() != null) {
            lead.setEmail(dto.getEmail());
        }

        if (dto.getTelefone() != null) {
            lead.setTelefone(dto.getTelefone());
        }

        if (dto.getOrigem() != null) {
            lead.setOrigem(dto.getOrigem());
        }

        if (dto.getStatus() != null) {
            lead.setStatus(dto.getStatus());
            if ("descarte".equals(dto.getStatus())) {
                if (dto.getMotivoDescarte() == null || dto.getMotivoDescarte().isBlank()) {
                    throw new RuntimeException("Motivo do descarte é obrigatório");
                }
                lead.setAtivo(false);
            } else {
                lead.setAtivo(true);
            }
        }

        if (dto.getValorInteresse() != null) {
            lead.setValorInteresse(dto.getValorInteresse());
        }

        if (dto.getObservacao() != null) {
            lead.setObservacao(dto.getObservacao());
        }

        if (dto.getMotivoDescarte() != null) {
            lead.setMotivoDescarte(dto.getMotivoDescarte());
        }

        if (dto.getImovelId() != null) {
            Imovel imovel = imovelRepository.findById(dto.getImovelId())
                .orElseThrow(() -> new RuntimeException("Imóvel não encontrado"));

            lead.setImovel(imovel);
        }

        

        return leadRepository.save(lead);
    }

    public void inativarLead(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        lead.setAtivo(false);
        // lead.setStatus("descarte");

        leadRepository.save(lead);
    }

    public void ativarLead(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        lead.setAtivo(true);

        leadRepository.save(lead);
    }

    public List<Lead> ConsultarLeads() {
        return leadRepository.findAll();
    }

    public void deletarLead(Long id){
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Lead com id " + id + " não encontrado"
                ));
        leadRepository.delete(lead);
    }

    public MetricsDTO getMetrics() {

        List<Lead> leads = leadRepository.findAll();

        MetricsDTO metrics = new MetricsDTO();

        long total = leads.size();

        long contratos = leads.stream()
                .filter(l -> "contrato".equalsIgnoreCase(l.getStatus()))
                .count();

        long descartes = leads.stream()
                .filter(l -> "descarte".equalsIgnoreCase(l.getStatus()))
                .count();

        metrics.setTotalLeads(leads.stream().filter(l -> "lead".equals(l.getStatus())).count());
        metrics.setTotalOportunidades(leads.stream().filter(l -> "oportunidade".equals(l.getStatus())).count());
        metrics.setTotalVisitasAgen(leads.stream().filter(l -> "visita-agendada".equals(l.getStatus())).count());
        metrics.setTotalVisitasReal(leads.stream().filter(l -> "visita-realizada".equals(l.getStatus())).count());
        metrics.setTotalPastas(leads.stream().filter(l -> "pasta".equals(l.getStatus())).count());
        metrics.setTotalAprovados(leads.stream().filter(l -> "aprovado".equals(l.getStatus())).count());
        metrics.setTotalContratos(contratos);
        metrics.setTotalDescartes(descartes);

        double taxa = total > 0 ? ((double) contratos / total) * 100 : 0;
        metrics.setTaxaConversaoGeral(taxa);

        double valorTotal = leads.stream()
                .filter(l -> "contrato".equals(l.getStatus()))
                .mapToDouble(Lead::getValorInteresse)
                .sum();

        metrics.setValorTotalFechado(valorTotal);

        return metrics;
    }
}

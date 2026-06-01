package crm_imobiliario.back.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.dto.ImovelAtualizacaoDTO;
import crm_imobiliario.back.model.dto.ImovelDTO;
import crm_imobiliario.back.model.entity.Imovel;
import crm_imobiliario.back.model.repository.ImovelRepository;

@Service
public class ImovelService {
    
    @Autowired
    private ImovelRepository imovelRepository;

    public Imovel cadastrarImovel(ImovelDTO dto){
        Imovel imovel = new Imovel();

        imovel.setTitulo(dto.getTitulo());
        imovel.setStatus(dto.getStatus());
        imovel.setEndereco(dto.getEndereco());
        imovel.setBairro(dto.getBairro());
        imovel.setCidade(dto.getCidade());
        imovel.setQuartos(dto.getQuartos());
        imovel.setBanheiros(dto.getBanheiros());
        imovel.setVagas(dto.getVagas());
        imovel.setDescricao(dto.getDescricao());
        imovel.setValorVenda(dto.getValorVenda());
        imovel.setArea(dto.getArea());
        imovel.setAtivo(true);

        try {
            return imovelRepository.save(imovel);

        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("imovel já cadastrado");
        }
    }

    public Imovel atualizarimovel(Long id, ImovelAtualizacaoDTO dto) {
        Imovel imovel = imovelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("imovel não encontrado"));

            
        if (dto.getTitulo() != null) {
            imovel.setTitulo(dto.getTitulo());
        }

        if (dto.getStatus() != null) {
            imovel.setStatus(dto.getStatus());
        }

        if (dto.getEndereco() != null) {
            imovel.setEndereco(dto.getEndereco());
        }

        if (dto.getBairro() != null) {
            imovel.setBairro(dto.getBairro());
        }

        if (dto.getCidade() != null) {
            imovel.setCidade(dto.getCidade());
        }

        if (dto.getQuartos() != null) {
            imovel.setQuartos(dto.getQuartos());
        }

        if (dto.getBanheiros() != null) {
            imovel.setBanheiros(dto.getBanheiros());
        }

        if (dto.getVagas() != null) {
            imovel.setVagas(dto.getVagas());
        }

        if (dto.getDescricao() != null) {
            imovel.setDescricao(dto.getDescricao());
        }

        if (dto.getValorVenda() != null) {
            imovel.setValorVenda(dto.getValorVenda());
        }

        if (dto.getArea() != null) {
            imovel.setArea(dto.getArea());
        }

        return imovelRepository.save(imovel);
    }

    public List<Imovel> ConsultarImoveis() {
        return imovelRepository.findByAtivoTrue();
    }

    public List<Imovel> ConsultarImoveisDisponiveis() {
        return imovelRepository.findByStatusAndAtivoTrue("disponivel");
    }

    public void inativarImovel(Long id) {
        Imovel imovel = imovelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Imóvel não encontrado"));

        imovel.setAtivo(false);

        imovelRepository.save(imovel);
    }

    public void ativarImovel(Long id) {
        Imovel imovel = imovelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Imóvel não encontrado"));

        imovel.setAtivo(true);

        imovelRepository.save(imovel);
    }

    public void deletarImovel(Long id){
        Imovel imovel = imovelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Imovel com id " + id + " não encontrado"
                ));
        imovelRepository.delete(imovel);
    }
}

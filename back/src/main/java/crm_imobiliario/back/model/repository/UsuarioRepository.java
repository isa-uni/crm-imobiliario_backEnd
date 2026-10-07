package crm_imobiliario.back.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Usuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {
    Optional<Usuario> findByEmail(String email);

    boolean existsByCpf(String cpf);
    boolean existsByEmail(String email);
    boolean existsByMatricula(String matricula);
    List<Usuario> findByGestorId(Long gestorId);
    List<Usuario> findByEquipeId(Long equipeId);

    long countByAtivo(boolean ativo);

    /** Quantidade de usuários (ativos e inativos) por papel: [nome do papel, total]. */
    @Query("select p.papel, count(u) from usuario u join u.papel p group by p.papel")
    List<Object[]> contarPorPapel();
}

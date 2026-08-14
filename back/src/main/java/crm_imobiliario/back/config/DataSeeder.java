package crm_imobiliario.back.config;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import crm_imobiliario.back.model.dto.UsuarioDTO;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.UsuarioService;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private PapelRepository papelRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Override
    public void run(String... args) throws Exception {
        if (papelRepository.count() == 0) {
            Papel admin = new Papel();
            admin.setPapel("admin");
            papelRepository.save(admin);

            Papel corretor = new Papel();
            corretor.setPapel("corretor");
            papelRepository.save(corretor);
        }

        if (usuarioRepository.count() == 0) {
            Papel papelAdmin = papelRepository.findAll().stream()
                    .filter(p -> "admin".equals(p.getPapel()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Papel admin não encontrado para o seed"));

            UsuarioDTO adminDTO = new UsuarioDTO(
                    "Administrador",
                    "admin@crm.com",
                    "52998224725",
                    "M",
                    "(11) 99999-9999",
                    LocalDate.of(1990, 1, 1),
                    papelAdmin.getId()
            );

            usuarioService.cadastrarUsuario(adminDTO);
        }
    }
}

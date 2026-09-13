package crm_imobiliario.back.empreendimento;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;
import crm_imobiliario.back.model.repository.EmpreendimentoDocumentoRepository;
import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoStorageService;

/** Validações de upload (§21/§22 do spec de importação): extensão, tamanho e arquivo vazio. */
class EmpreendimentoStorageServiceTest {

    private final EmpreendimentoDocumentoRepository repository = mock(EmpreendimentoDocumentoRepository.class);
    private final EmpreendimentoStorageService service = new EmpreendimentoStorageService(repository, "./data/pdfs");
    private Path arquivoCriado;

    @AfterEach
    void limpar() throws Exception {
        if (arquivoCriado != null) Files.deleteIfExists(arquivoCriado);
    }

    @Test
    void rejeitaExtensaoNaoSuportada() {
        MockMultipartFile file = new MockMultipartFile("files", "planta.dwg", "application/octet-stream", "dados".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.armazenar(file, null, 1L));
        verify(repository, never()).save(any());
    }

    @Test
    void rejeitaArquivoVazio() {
        MockMultipartFile file = new MockMultipartFile("files", "tabela.pdf", "application/pdf", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service.armazenar(file, null, 1L));
    }

    @Test
    void rejeitaArquivoAcimaDoLimite() {
        byte[] grande = new byte[21 * 1024 * 1024]; // 21MB > limite de 20MB
        MockMultipartFile file = new MockMultipartFile("files", "tabela.pdf", "application/pdf", grande);
        assertThrows(IllegalArgumentException.class, () -> service.armazenar(file, null, 1L));
    }

    @Test
    void armazenaArquivoValidoERegistraMetadados() throws Exception {
        when(repository.save(any(EmpreendimentoDocumento.class))).thenAnswer(inv -> inv.getArgument(0));
        byte[] conteudo = "conteudo de teste".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("files", "tabela.pdf", "application/pdf", conteudo);

        EmpreendimentoDocumento doc = service.armazenar(file, null, 1L);
        arquivoCriado = Path.of(doc.getCaminho());

        assertEquals("tabela.pdf", doc.getNomeOriginal());
        assertEquals("pdf", doc.getTipo());
        assertEquals((long) conteudo.length, doc.getTamanho());
        assertEquals("pendente", doc.getStatusProcessamento());
        assertNotNull(doc.getHash());
        assertTrue(Files.exists(arquivoCriado));
        verify(repository).save(any(EmpreendimentoDocumento.class));
    }
}

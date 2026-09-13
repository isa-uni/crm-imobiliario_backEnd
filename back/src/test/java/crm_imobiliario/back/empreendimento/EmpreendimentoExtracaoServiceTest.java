package crm_imobiliario.back.empreendimento;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import crm_imobiliario.back.model.dto.empreendimento.EmpreendimentoExtracaoDTO;
import crm_imobiliario.back.model.entity.Empreendimento;
import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;
import crm_imobiliario.back.model.entity.EmpreendimentoExtracao;
import crm_imobiliario.back.model.entity.EmpreendimentoFonte;
import crm_imobiliario.back.model.repository.EmpreendimentoDocumentoRepository;
import crm_imobiliario.back.model.repository.EmpreendimentoFonteRepository;
import crm_imobiliario.back.model.repository.EmpreendimentoRepository;
import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoExtracaoService;

/**
 * Fluxo assíncrono completo de extração (upload -> processamento -> status)
 * (§17/§22 do spec de importação): status vai para "revisao" quando há
 * conflito entre documentos, e "concluido" quando a extração é limpa.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:extracaotestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class EmpreendimentoExtracaoServiceTest {

    @Autowired
    private EmpreendimentoExtracaoService extracaoService;
    @Autowired
    private EmpreendimentoDocumentoRepository documentoRepository;
    @Autowired
    private EmpreendimentoFonteRepository fonteRepository;
    @Autowired
    private EmpreendimentoRepository empreendimentoRepository;

    @TempDir
    Path tempDir;

    private EmpreendimentoDocumento salvarDocumento(String nomeArquivo, Path caminho) throws Exception {
        EmpreendimentoDocumento d = new EmpreendimentoDocumento();
        d.setNomeOriginal(nomeArquivo);
        d.setNomeArmazenado(nomeArquivo);
        d.setTipo("pdf");
        d.setCaminho(caminho.toString());
        d.setHash(sha256(caminho));
        d.setTamanho(java.nio.file.Files.size(caminho));
        d.setStatusProcessamento("pendente");
        return documentoRepository.save(d);
    }

    private String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(md.digest(java.nio.file.Files.readAllBytes(p)));
    }

    private Path escreverPdfComLinhas(String nomeArquivo, String[] cabecalho, String[]... linhas) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 700;
                escreverLinha(cs, y, cabecalho);
                for (String[] linha : linhas) { y -= 30; escreverLinha(cs, y, linha); }
            }
            Path p = tempDir.resolve(nomeArquivo);
            doc.save(p.toFile());
            return p;
        }
    }

    private void escreverLinha(PDPageContentStream cs, float y, String... colunas) throws IOException {
        float x = 50;
        for (String coluna : colunas) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            cs.newLineAtOffset(x, y);
            cs.showText(coluna);
            cs.endText();
            x += 120;
        }
    }

    private EmpreendimentoExtracaoDTO aguardarConclusao(Long extracaoId) throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        EmpreendimentoExtracaoDTO dto;
        do {
            Thread.sleep(200);
            dto = extracaoService.obter(extracaoId);
        } while ((dto.getStatus().equals("pendente") || dto.getStatus().equals("processando")) && System.currentTimeMillis() < limite);
        return dto;
    }

    @Test
    void extracaoLimpaFicaConcluida() throws Exception {
        Path pdf = escreverPdfComLinhas("tabela.pdf",
                new String[]{"BLOCO", "UNIDADE", "SITUAÇÃO", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "Disponível", "R$ 227.200,00"});
        EmpreendimentoDocumento doc = salvarDocumento("tabela.pdf", pdf);

        EmpreendimentoExtracao extracao = extracaoService.criarExtracao(List.of(doc.getId()), 1L, null);
        EmpreendimentoExtracaoDTO resultado = aguardarConclusao(extracao.getId());

        assertEquals("concluido", resultado.getStatus());
        assertTrue(resultado.getConflitos() == null || resultado.getConflitos().isEmpty());
    }

    @Test
    void extracaoComConflitoEntreDocumentosFicaEmRevisao() throws Exception {
        Path pdf1 = escreverPdfComLinhas("docA.pdf",
                new String[]{"BLOCO", "UNIDADE", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "R$ 227.200,00"});
        Path pdf2 = escreverPdfComLinhas("docB.pdf",
                new String[]{"BLOCO", "UNIDADE", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "R$ 240.000,00"});
        EmpreendimentoDocumento doc1 = salvarDocumento("docA.pdf", pdf1);
        EmpreendimentoDocumento doc2 = salvarDocumento("docB.pdf", pdf2);

        EmpreendimentoExtracao extracao = extracaoService.criarExtracao(List.of(doc1.getId(), doc2.getId()), 1L, null);
        EmpreendimentoExtracaoDTO resultado = aguardarConclusao(extracao.getId());

        assertEquals("revisao", resultado.getStatus());
        assertNotNull(resultado.getConflitos());
        assertTrue(resultado.getConflitos().stream().anyMatch(c -> c.getCampo().equals("unidades[B01.101].VALOR_TOTAL")));
    }

    @Test
    void reprocessarVoltaParaPendenteEProcessaNovamente() throws Exception {
        Path pdf = escreverPdfComLinhas("reprocessar.pdf",
                new String[]{"BLOCO", "UNIDADE", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "R$ 200.000,00"});
        EmpreendimentoDocumento doc = salvarDocumento("reprocessar.pdf", pdf);

        EmpreendimentoExtracao extracao = extracaoService.criarExtracao(List.of(doc.getId()), 1L, null);
        aguardarConclusao(extracao.getId());

        extracaoService.reprocessar(extracao.getId());
        EmpreendimentoExtracaoDTO resultado = aguardarConclusao(extracao.getId());
        assertEquals("concluido", resultado.getStatus());
        assertNull(resultado.getErro());
    }

    @Test
    void vincularEmpreendimentoPreencheEmpreendimentoIdNasFontesJaGravadas() throws Exception {
        // no fluxo real, a extração roda (e grava as fontes) ANTES de o empreendimento existir —
        // vincularEmpreendimento precisa recuperar essas fontes órfãs, não só documentos.
        Path pdf = escreverPdfComLinhas("fonte.pdf",
                new String[]{"BLOCO", "UNIDADE", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "R$ 227.200,00"});
        EmpreendimentoDocumento doc = salvarDocumento("fonte.pdf", pdf);

        EmpreendimentoExtracao extracao = extracaoService.criarExtracao(List.of(doc.getId()), 1L, null);
        aguardarConclusao(extracao.getId());

        List<EmpreendimentoFonte> antes = fonteRepository.findByExtracaoId(extracao.getId());
        assertFalse(antes.isEmpty());
        assertTrue(antes.stream().allMatch(f -> f.getEmpreendimentoId() == null), "antes de vincular, as fontes não têm empreendimento ainda");

        Empreendimento emp = empreendimentoRepository.save(Empreendimento.builder().nome("Teste Vinculo Fonte").slug("teste-vinculo-fonte").build());
        extracaoService.vincularEmpreendimento(extracao.getId(), emp.getId());

        List<EmpreendimentoFonte> depois = fonteRepository.findByEmpreendimentoId(emp.getId());
        assertFalse(depois.isEmpty(), "vincularEmpreendimento deve preencher o empreendimentoId das fontes já gravadas");
    }
}

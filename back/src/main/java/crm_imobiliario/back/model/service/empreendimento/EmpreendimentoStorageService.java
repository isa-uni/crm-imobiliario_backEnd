package crm_imobiliario.back.model.service.empreendimento;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;
import crm_imobiliario.back.model.repository.EmpreendimentoDocumentoRepository;

@Service
public class EmpreendimentoStorageService {

    private final EmpreendimentoDocumentoRepository documentoRepository;
    private final Path basePath;

    private static final Set<String> ALLOWED_MIMES = Set.of(
        "application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/csv",
        "image/jpeg","image/jpg","image/png"
    );
    private static final Set<String> ALLOWED_EXT = Set.of("pdf","doc","docx","xls","xlsx","csv","jpg","jpeg","png");

    public EmpreendimentoStorageService(EmpreendimentoDocumentoRepository documentoRepository,
                                       @Value("${crm.pdf-storage:./data/pdfs}") String base) {
        this.documentoRepository = documentoRepository;
        this.basePath = Paths.get("./data/empreendimentos").toAbsolutePath().normalize();
        try { Files.createDirectories(basePath); } catch (IOException ignored) {}
    }

    public EmpreendimentoDocumento armazenar(MultipartFile file, Long empreendimentoId, Long usuarioId) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Arquivo vazio: " + file.getOriginalFilename());
        if (file.getSize() > 20 * 1024 * 1024) throw new IllegalArgumentException("Arquivo excede 20MB: " + file.getOriginalFilename());
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "arquivo";
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')+1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) throw new IllegalArgumentException("Formato não suportado: " + ext + " (" + original + ")");
        String mime = file.getContentType();
        // mime pode ser null para alguns, permitir se extensão ok
        if (mime != null && !ALLOWED_MIMES.contains(mime.toLowerCase()) && !ext.equals("csv") && !mime.startsWith("image/")) {
            // não bloqueia rígido, apenas aviso
        }
        byte[] bytes = file.getBytes();
        String hash = sha256(bytes);
        // quota: se já existe hash concluído, reutiliza? não duplica, retorna existente
        // mas para rastreabilidade de upload novo, criamos novo registro mesmo com hash igual

        String armazenado = UUID.randomUUID() + "." + ext;
        Path dir = empreendimentoId != null ? basePath.resolve(String.valueOf(empreendimentoId)) : basePath.resolve("tmp");
        Files.createDirectories(dir);
        Path destino = dir.resolve(armazenado);
        Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

        EmpreendimentoDocumento doc = EmpreendimentoDocumento.builder()
            .empreendimentoId(empreendimentoId)
            .nomeOriginal(original)
            .nomeArmazenado(armazenado)
            .tipo(ext)
            .tamanho(file.getSize())
            .caminho(destino.toString())
            .hash(hash)
            .mime(mime)
            .usuarioUpload(usuarioId)
            .statusProcessamento("pendente")
            .build();
        return documentoRepository.save(doc);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) { throw new RuntimeException("Erro hash", e); }
    }

    public byte[] lerArquivo(EmpreendimentoDocumento doc) throws IOException {
        return Files.readAllBytes(Paths.get(doc.getCaminho()));
    }
}

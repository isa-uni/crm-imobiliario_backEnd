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
                                       @Value("${crm.pdf-storage:./data/empreendimentos}") String base) {
        this.documentoRepository = documentoRepository;
        // antes o parâmetro era ignorado e o caminho ficava fixo em ./data/empreendimentos
        this.basePath = Paths.get(base).toAbsolutePath().normalize();
        try { Files.createDirectories(basePath); } catch (IOException ignored) {}
    }

    //Receber um arquivo enviado pelo usuário, verificar se ele pode ser enviado, 
    //armazená-lo no servidor e registrar suas informações no banco de dados.
    public EmpreendimentoDocumento armazenar(MultipartFile file, Long empreendimentoId, Long usuarioId) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("O arquivo \"" + file.getOriginalFilename() + "\" está vazio. Verifique o arquivo e envie novamente.");
        if (file.getSize() > 20 * 1024 * 1024) throw new IllegalArgumentException("O arquivo \"" + file.getOriginalFilename() + "\" tem " + String.format("%.1f", file.getSize() / (1024.0 * 1024.0)) + " MB e excede o limite de 20 MB por arquivo.");
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "arquivo";
        //descobrindo o nome original do arquivo enviado.
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')+1).toLowerCase() : "";
        //Descobrindo a extensão do arquivo
        if (!ALLOWED_EXT.contains(ext)) throw new IllegalArgumentException("O arquivo \"" + original + "\" não pode ser enviado porque o formato " + (ext.isEmpty() ? "(sem extensão)" : "." + ext) + " não é aceito. Envie PDF, Word (DOC/DOCX), planilha (XLS/XLSX/CSV) ou imagem (JPG/PNG).");
        //Verificando se o formato é permitido
        String mime = file.getContentType();//O MIME informa que o conteúdo é identificado como PDF.

        // mime pode ser null para alguns, permitir se extensão ok
        //if (mime != null && !ALLOWED_MIMES.contains(mime.toLowerCase()) && !ext.equals("csv") && !mime.startsWith("image/")) {
            // não bloqueia rígido, apenas aviso
        //}

        byte[] bytes = file.getBytes();//Lê o conteúdo inteiro do arquivo e transforma em um conjunto de bytes.
        String hash = sha256(bytes);//"Impressão digital"
        //Arquivos exatamente com o mesmo conteúdo, poderão gerar o mesmo hash SHA-256.

        String armazenado = UUID.randomUUID() + "." + ext;//gera um identificador único
        Path dir = empreendimentoId != null ? basePath.resolve(String.valueOf(empreendimentoId)) : basePath.resolve("tmp");//encontrando o caminho na pasta
        Files.createDirectories(dir);//Criando a pasta, caso ela não exista
        Path destino = dir.resolve(armazenado);//Definindo o caminho completo do arquivo(com o nome do hash)
        Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);//Copiando o arquivo para o armazenamento

        EmpreendimentoDocumento doc = EmpreendimentoDocumento.builder()
            .empreendimentoId(empreendimentoId)
            .nomeOriginal(original)
            .nomeArmazenado(armazenado)
            .tipo(ext)
            .tamanho(file.getSize())
            .caminho(destino.toString())
            .hash(hash)//"Impressão digital do arquivo"
            .mime(mime)
            .usuarioUpload(usuarioId)//"Id de quem enviou"
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

}

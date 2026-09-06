package crm_imobiliario.back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "crm")
public class CrmProperties {

    /** URL base do CV CRM */
    private String baseUrl = "https://pride.cvcrm.com.br";

    /** Caminho do perfil Playwright persistido (OneDrive/Documentos/OA LEADS/.perfil_navegador) */
    private String perfilPath = "";

    /** Intervalo de sincronização em segundos (padrão 6h = 21600) */
    private long refreshSeg = 21600;

    /** Timeout de espera do detalhe sob demanda */
    private long esperaSeg = 300;

    /** CORS origin */
    private String corsOrigem = "*";

    /** Habilita sync automático no startup */
    private boolean syncNoStartup = true;

    /** Pasta para cache de PDFs */
    private String pdfStorage = "./data/pdfs";
}

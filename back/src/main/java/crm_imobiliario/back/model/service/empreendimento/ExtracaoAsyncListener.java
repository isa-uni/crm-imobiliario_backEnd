package crm_imobiliario.back.model.service.empreendimento;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

/**
 * Dispara o processamento da extração em segundo plano (TaskExecutor "crm-async-*"), somente
 * depois que a transação que criou a extração e os vínculos com os documentos fez commit — assim a
 * thread assíncrona sempre enxerga os registros, e o upload responde 202 sem esperar a extração.
 */
@Component
@RequiredArgsConstructor
public class ExtracaoAsyncListener {

    private final EmpreendimentoExtracaoService extracaoService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExtracaoSolicitada(EmpreendimentoExtracaoService.ExtracaoSolicitadaEvent event) {
        extracaoService.processar(event.extracaoId());
    }
}

package searchengine.exception;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.GenericJDBCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import searchengine.dto.indexing.IndexingDTO;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;

@Component
@ControllerAdvice
@RequiredArgsConstructor
public class ExceptionHandlerController {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final ApplicationEventPublisher eventPublisher;
    private final SiteRepository siteRepository;

    public void handleIndexingError(String errorMessage, Exception e) {
        IndexingDTO indexingErrorDTO = new IndexingDTO(false, errorMessage);
        valueLogger.error("ExceptionHandlerController: {}", e.getMessage());
        eventPublisher.publishEvent(new IndexingErrorEvent(this, indexingErrorDTO));
    }

    public void handleException(Exception e) {
        if (e instanceof DataAccessResourceFailureException
                || e instanceof CannotCreateTransactionException
                || e instanceof GenericJDBCException) {
            handleIndexingError("Ошибка доступа к данным", e);
        } else {
            handleIndexingError(e.getClass().toString(), e);
        }
    }
    public void handleExceptionNet(Exception e, SiteEntity siteEntity) {
        String errorMessage = "Невозможно установить соединение с удаленным узлом.";
        handleIndexingError(errorMessage, e);
        siteEntity.setLastError(errorMessage);
        siteRepository.save(siteEntity);
    }
}
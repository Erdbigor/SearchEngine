package searchengine.errorHandling;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import searchengine.dto.indexing.IndexingDTO;

@Component
@ControllerAdvice
@RequiredArgsConstructor
public class ExceptionHandlerSE {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final ApplicationEventPublisher eventPublisher;

    @ExceptionHandler(Exception.class)
    public void handleException(Exception ex) {
        IndexingDTO indexingErrorDTO = new IndexingDTO(
                false, "Невозможно установить связь с сервером!");
        valueLogger.error("ExceptionHandlerSE.Произошла ошибка: {}".toUpperCase(), ex.getMessage());
        eventPublisher.publishEvent(new IndexingErrorEvent(this, indexingErrorDTO));


//        if (ex instanceof DataAccessException) {
//            valueLogger.error("Ошибка доступа к данным: {}", ex.getMessage());
//            return new IndexingDTO(false, "Ошибка подключения к базе данных".toUpperCase());
//        } else {
//            valueLogger.error("ExceptionHandlerSE.Произошла ошибка: {}", ex.getMessage());
//            return new IndexingDTO(false, "Непредвиденная ошибка".toUpperCase());
//        }
    }
}
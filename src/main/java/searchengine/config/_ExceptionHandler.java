package searchengine.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import searchengine.dto.indexing.IndexingDTO;

import java.sql.SQLException;

@Component
@RestControllerAdvice
public class _ExceptionHandler {
    @ExceptionHandler({CannotCreateTransactionException.class, SQLException.class})
    public IndexingDTO handleException(Exception ex) {
        String message = "Невозможно установить связь с сервером!";
        return new IndexingDTO(false, message);
    }
}

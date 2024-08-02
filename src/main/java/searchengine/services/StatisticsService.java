package searchengine.services;

import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

public interface StatisticsService {
    CompletableFuture<ResponseEntity<Object>> getStatistics();
}

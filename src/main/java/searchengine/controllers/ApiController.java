package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingDTO;
import searchengine.dto.search.DataDTO;
import searchengine.dto.search.SearchDTO;
import searchengine.exception.IndexingErrorEvent;
import searchengine.mappers.SearchMapper;
import searchengine.services.SearchService;
import searchengine.services.SiteAndPageService;
import searchengine.services.StatisticsService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteAndPageService siteAndPageService;
    private final SearchService searchService;


    @GetMapping("/statistics")
    public ResponseEntity<Object> statistics() {
        CompletableFuture<ResponseEntity<Object>> future = statisticsService.getStatistics();
        return future.join();
    }

    @GetMapping("/startIndexing")
    public IndexingDTO startIndexing() {
        CompletableFuture<IndexingDTO> future = siteAndPageService
                .siteAndPageUpdateManager(false, "");
        return future.join();
    }

    @GetMapping("/stopIndexing")
    public IndexingDTO stopIndexing() {
        CompletableFuture<IndexingDTO> future = siteAndPageService.stopScanning();
        return future.join();
    }

    @PostMapping("/indexPage")
    public IndexingDTO indexPage(@RequestParam(value = "url") String url) {
        CompletableFuture<IndexingDTO> future = siteAndPageService.siteAndPageUpdateManager(true, url.trim());
        return future.join();
    }

    @GetMapping("/search")
    public SearchDTO search(@RequestParam(value = "query") String query,
                            @RequestParam(value = "site", required = false) String site,
                            @RequestParam(value = "offset", required = false) Integer offset,
                            @RequestParam(value = "limit", required = false) Integer limit) {
        double frequencyThreshold = 1;
        CompletableFuture<SearchDTO> future = searchService.searchResult(query, site, offset, limit, frequencyThreshold);
        return future.join();
    }
}

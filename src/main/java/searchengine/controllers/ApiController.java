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
import searchengine.errorHandling.IndexingErrorEvent;
import searchengine.mappers.SearchMapper;
import searchengine.services.SearchService;
import searchengine.services.SiteAndPageService;
import searchengine.services.StatisticsService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final StatisticsService statisticsService;
    private final SiteAndPageService siteAndPageService;
    private final SitesList sitesList;
    private final SearchService searchService;
    private final SearchMapper searchMapper;
    public boolean isIndexing;
    private final String indexing = "Индексация уже запущена!";
    private IndexingDTO lastError;

    @EventListener
    public void handleIndexingError(IndexingErrorEvent event) {
        this.lastError = event.getIndexingErrorDTO();
    }

    @GetMapping("/statistics")
    public ResponseEntity<Object> statistics() {
        if (lastError != null) {
            IndexingDTO response = new IndexingDTO(
                    false, lastError.getError());
            lastError = null;
            valueLogger.error(response.getError());
            return ResponseEntity.ok(response);
        } else  return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public IndexingDTO startIndexing() {

        isIndexing = siteAndPageService.isIndexing().get();
        if (isIndexing) {
            return new IndexingDTO(false, indexing);
        } else {
            siteAndPageService.siteAndPageUpdateManager(false, "", new Site());
            return new IndexingDTO(true, "Индексация начата.");
        }
    }

    @GetMapping("/stopIndexing")
    public IndexingDTO stopIndexing() {
        isIndexing = siteAndPageService.isIndexing().get();
        if (isIndexing) {
            siteAndPageService.stopScanning();
            return new IndexingDTO(true, "Индексация будет остановлена!");
        } else {
            return new IndexingDTO(false, "Индексация не запущена.");
        }
    }

    @PostMapping("/indexPage")
    public IndexingDTO indexPage(@RequestParam(value = "url") String url) {
        isIndexing = siteAndPageService.isIndexing().get();
        valueLogger.info("isIndexing: " + isIndexing);
        if (isIndexing) {
            return new IndexingDTO(false, indexing);
        }
        String urlRegex = "^(https?://)?(www\\.)?([-a-zA-Z0-9@:%._+~#=]{2,256}\\.[a-z]{2,6})\\b([-a-zA-Z0-9@:%_+.~#?&//=]*)$";
        Pattern pattern = Pattern.compile(urlRegex);
        String trimUrl = url.trim();
        Matcher matcher = pattern.matcher(trimUrl);
        isIndexing = siteAndPageService.isIndexing().get();
        if (!matcher.matches()) {
            return new IndexingDTO(false, "Неверный формат URL!");
        }
        for (Site site : sitesList.getSites()) {
            if (url.startsWith(site.getUrl())) {
                siteAndPageService.siteAndPageUpdateManager(true, trimUrl, site);
                return new IndexingDTO(true, "");
            }
        }
        return new IndexingDTO(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
    }

    @GetMapping("/search")
    public SearchDTO search(@RequestParam(value = "query") String query,
                            @RequestParam(value = "site", required = false) String site,
                            @RequestParam(value = "offset", required = false) Integer offset,
                            @RequestParam(value = "limit", required = false) Integer limit) {

        if (searchService.isSearch.get()) {
            return new SearchDTO(false, 0, "Поиск уже идёт!", new ArrayList<>());
        }
        site = site == null ? "all_site" : site;
        offset = offset == null ? 0 : offset;
        limit = limit == null ? 20 : limit;
        double frequencyThreshold = 1;
        valueLogger.info("query: " + query);
        valueLogger.info("site: " + site);
        valueLogger.info("offset: " + offset);
        valueLogger.info("limit: " + limit);
        valueLogger.info("frequencyThreshold: " + frequencyThreshold);
        if (query.isEmpty()) {
            return searchMapper.map(false, offset, limit, isIndexing, new ArrayList<>());
        }
        List<DataDTO> resultDataDTOList = searchService.searchResult(query, site, offset, limit, frequencyThreshold);
        searchService.setIsSearch(false);
        return searchMapper.map(true, offset, limit, isIndexing, resultDataDTOList);
    }
}

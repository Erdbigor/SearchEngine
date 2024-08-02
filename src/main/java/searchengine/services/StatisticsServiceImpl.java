package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingDTO;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.exception.IndexingErrorEvent;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteAndPageService siteAndPageService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private IndexingDTO lastError;

    @EventListener
    public void handleIndexingError(IndexingErrorEvent event) {
        this.lastError = event.getIndexingErrorDTO();
    }

    @Override
    public CompletableFuture<ResponseEntity<Object>> getStatistics() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        if (lastError != null) {
            IndexingDTO response = new IndexingDTO(false, lastError.getError());
            future.complete(ResponseEntity.ok(response));
            lastError = null;
            System.out.println(response.getError());
            return future;
        }
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());

        if (!siteAndPageService.getIsIndexing().get()) {//indexing ******
            total.setIndexing(true);
        } else {
            total.setIndexing(false);
        }

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        try {
            for (int i = 0; i < sitesList.size(); i++) {
                Site site = sitesList.get(i);
                SiteEntity siteEntity = siteRepository.getByUrl(site.getUrl());

                DetailedStatisticsItem item = new DetailedStatisticsItem();
                item.setName(site.getName());
                item.setUrl(site.getUrl());
                int pagesNumber = pageRepository.findBySite(siteEntity).size();
                int lemmas = lemmaRepository.findBySite(siteEntity).size();
                item.setPages(pagesNumber);
                item.setLemmas(lemmas);
                item.setStatus(siteEntity.getStatus().name());
                item.setError(siteEntity.getLastError());
                long statusTimeMillis = siteEntity.getStatusTime().toInstant(ZoneOffset.UTC).toEpochMilli();
                item.setStatusTime(statusTimeMillis);
                total.setPages(total.getPages() + pagesNumber);
                total.setLemmas(total.getLemmas() + lemmas);
                detailed.add(item);
            }
        } catch (NullPointerException ne) {
            future.complete(ResponseEntity.ok(new IndexingDTO(false, "Необходима индексация")));
            return future;
        }
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        future.complete(ResponseEntity.ok(response));
        return future;
    }
}

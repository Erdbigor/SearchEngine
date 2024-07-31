package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingDTO;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteAndPageService siteAndPageService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public Object getStatistics() {

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());

        if (!siteAndPageService.isIndexing().get()) {//indexing ******
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
            return new IndexingDTO(false, "Необходима индексация");
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}

package searchengine.services;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;
import searchengine.pageCount.SiteMapBuilder;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
@EnableAsync
@NoArgsConstructor
public class BuildMapService {

    private SiteRepository siteRepository;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private SitesList sitesList;
    private CountDownLatch latch = new CountDownLatch(0);
    private String lastError = "";

    @Autowired
    public BuildMapService(SitesList sitesList, SiteRepository siteRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
    }

    @Async
    public void scheduleScanSite() {
        initDB();
        latch = new CountDownLatch(sitesList.getSites().size());
        for (Site site : sitesList.getSites()) {
            String siteUrl = site.getUrl();
            addSite(site, List.of(), true);
            executorService.submit(() -> {
                try {
                    String startUrl = siteUrl;
                    if (startUrl.endsWith("/")) {
                        startUrl = startUrl.substring(0, startUrl.length() - 1);
                    }
                    SiteMapBuilder siteMapBuilder = new SiteMapBuilder(startUrl, startUrl, siteRepository);
                    ArrayList<String> siteMap = (ArrayList<String>) siteMapBuilder.buildSiteMap();
                    siteMap.add(startUrl);
                    addSite(site, siteMap, false);
                    System.out.println("Количество страниц: " + siteUrl + " " + siteMap.size());
                    latch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
            System.out.println("Сканирование сайтов завершено.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void addSite(Site site, List<String> siteMap, Boolean isIndexing) {
        SiteEntity siteEntity = (isIndexing)
                ? new SiteEntity()
                : siteRepository.findByUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus((isIndexing) ? SiteStatus.INDEXING : SiteStatus.INDEXED);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        siteRepository.flush();
    }

    public void initDB() {
        siteRepository.deleteAll();
    }

    public void updateLastError(String error, String siteUrl, SiteRepository siteRepository) {
        if (lastError.equals(error)) {
            return;
        }
        System.out.println(error);
        SiteEntity site = siteRepository.findByUrl(siteUrl);
        site.setLastError(error);
        siteRepository.save(site);
        lastError = error;
    }

    public CountDownLatch getLatch() {
        return latch;
    }
}





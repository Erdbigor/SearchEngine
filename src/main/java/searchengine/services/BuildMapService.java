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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
@EnableAsync
@NoArgsConstructor
public class BuildMapService {

    private final List<SiteMapBuilder> siteMapBuilders = new ArrayList<>();
    private SiteRepository siteRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private SitesList sitesList;
    private CountDownLatch latch = new CountDownLatch(0);
    private static String lastError = "";
    private static Boolean isInterrupted;

    @Autowired
    public BuildMapService(SitesList sitesList, SiteRepository siteRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
    }

    @Async
    public void scheduleScanSite() {
        isInterrupted = false;
        latch = new CountDownLatch(sitesList.getSites().size());
        for (Site site : sitesList.getSites()) {
            String siteUrl = site.getUrl();
            siteRepository.deleteByUrl(siteUrl);// удаление одноименной записи в таблице 'site;;
            addSite(site, List.of(), true);
            executor.submit(() -> {
                try {
                    String startUrl = siteUrl;
                    if (startUrl.endsWith("/")) {
                        startUrl = startUrl.substring(0, startUrl.length() - 1);
                    }
                    SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
                    SiteMapBuilder siteMapBuilder = new SiteMapBuilder(startUrl, startUrl, siteRepository, siteEntity);
                    siteMapBuilders.add(siteMapBuilder);
                    ArrayList<String> siteMap = (ArrayList<String>) siteMapBuilder.buildSiteMap();
                    siteMap.add(startUrl);
                    addSite(site, siteMap, false);
                    System.out.println("Сканирование сайта " + siteUrl + " завершено. Количество страниц: "
                            + siteMap.size() + ".");
                    latch.countDown();
                    siteMap.forEach(System.out::println);
                } catch (Exception e) {
                    System.err.println(e.getClass().getName());
                    addSite(site, List.of(), false);
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
            System.out.println("Сканирование завершено.");
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
        if ((siteMap.size() < 2 && !isIndexing)
                || (lastError.equals("ConnectException") && !isIndexing)
                || (lastError.equals("SocketTimeoutException") && !isIndexing)
                || (lastError.equals("NoRouteToHostException") && !isIndexing)
                || (isInterrupted)) {
            siteEntity.setStatus(SiteStatus.FAILED);
            siteEntity.setLastError((isInterrupted) ? "Interrupted on demand" : lastError);
        } else {
            siteEntity.setStatus((isIndexing) ? SiteStatus.INDEXING : SiteStatus.INDEXED);
            siteEntity.setLastError("");
        }
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        siteRepository.flush();
    }

    public void initDB() {
        siteRepository.deleteAll();
    }

    public void updateLastError(String error, String siteUrl
            , SiteRepository siteRepository, SiteEntity siteEntity) {
        if (lastError.equals(error)) {
            return;
        }
        System.out.println(error);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
        lastError = error;
    }

    public Integer getSiteMapBuildersSize() {
        return siteMapBuilders.size();
    }

    public void stopScanning() {
        System.out.println("Остановка сканирования ...");
        isInterrupted = true;
        System.out.println("siteMapBuilders.size: " + siteMapBuilders.size());
        for (SiteMapBuilder siteMapBuilder : siteMapBuilders) {
            siteMapBuilder.stopScanning();
        }
        siteMapBuilders.clear();
    }
}





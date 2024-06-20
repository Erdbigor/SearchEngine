package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.pageCount.SiteMapBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@EnableAsync
public class BuildMapService {

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final SitesList sitesList;
    private final Map<String, List<String>> scannedPages = new ConcurrentHashMap<>();
    private CountDownLatch latch = new CountDownLatch(0);

    @Autowired
    public BuildMapService(SitesList sitesList) {
        this.sitesList = sitesList;
    }

    @Async
    public void scheduleScanSite() {
        System.out.println("latchStart: " + latch.getCount());
        latch = new CountDownLatch(sitesList.getSites().size());
        for (Site site : sitesList.getSites()) {
            String siteUrl = site.getUrl();
            executorService.submit(() -> {
                try {
                    String startUrl = siteUrl;
                    if (startUrl.endsWith("/")) {
                        startUrl = startUrl.substring(0, startUrl.length() - 1);
                    }
                    SiteMapBuilder siteMapBuilder = new SiteMapBuilder(startUrl, startUrl);
                    ArrayList<String> siteMap = (ArrayList<String>) siteMapBuilder.buildSiteMap();
                    siteMap.add(startUrl);
                    scannedPages.put(siteUrl, siteMap);
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
            System.out.println("latchEnd: " + latch.getCount());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public CountDownLatch getLatch() {
        return latch;
    }
}





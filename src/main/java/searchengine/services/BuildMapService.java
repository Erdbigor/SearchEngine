package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.pageCount.SiteMapBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@EnableAsync
public class BuildMapService {

    public boolean isScanning;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    @Autowired
    private final SitesList sitesList = new SitesList();

    public void scheduleScanSite() {
        executorService.submit(() -> {
            try {
                String startUrl = sitesList.getSites().get(2).getUrl();
                if (startUrl.endsWith("/")) {
                    startUrl = startUrl.substring(0, startUrl.length() - 1);
                }
                isScanning = true;
                SiteMapBuilder siteMapBuilder = new SiteMapBuilder(startUrl, startUrl);
                List<String> siteMap = siteMapBuilder.buildSiteMap();
                isScanning = false;
                siteMap.add(startUrl);
                System.out.println("Количество страниц: " + siteMap.size());
            } catch (Exception e) {
                e.getStackTrace();
            }
        });

    }

    // Метод для остановки сервиса
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            // Если поток был прерван, остановите сервис принудительно
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean isScanning() {
        return isScanning;
    }
}

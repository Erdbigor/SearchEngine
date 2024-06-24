package searchengine.services;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.PageDTO;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;
import searchengine.pageCount.SiteMapBuilder;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
@EnableAsync
@NoArgsConstructor
public class BuildMapService {

    private final List<SiteMapBuilder> siteMapBuilders = new ArrayList<>();
    private SiteRepository siteRepository;
    private PageRepository pageRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private SitesList sitesList;
    private CountDownLatch latch = new CountDownLatch(0);
    private static String lastError = "";
    private static Boolean isInterrupted;


    @Autowired
    public BuildMapService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    @Async
    public void scheduleScanSite() {
        isInterrupted = false;
        latch = new CountDownLatch(sitesList.getSites().size());
        for (Site site : sitesList.getSites()) {
            String siteUrl = site.getUrl();
            siteRepository.deleteByUrl(siteUrl);// удаление одноименной записи в таблице 'site;;
            addSite(site, 0, true);
            executor.submit(() -> {
                try {
                    String startUrl = siteUrl;
                    if (startUrl.endsWith("/")) {
                        startUrl = startUrl.substring(0, startUrl.length() - 1);
                    }
                    SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
                    SiteMapBuilder siteMapBuilder = new SiteMapBuilder(startUrl, startUrl, siteRepository, siteEntity);
                    siteMapBuilders.add(siteMapBuilder);
                    HashMap<String, PageDTO> siteMap = (HashMap<String, PageDTO>) siteMapBuilder.buildSiteMap();
                    int siteMapSize = siteMap.size();
                    addSite(site, siteMapSize, false);
                    addPage(siteMap, siteEntity, siteMapSize);
                    playSound();
                    System.out.println("Сканирование сайта " + siteUrl + " завершено. Количество страниц: "
                            + siteMap.size() + ".");
                    latch.countDown();
//                    siteMap.forEach((key, value)-> {
//                        System.out.println(key);
//                    });
                } catch (Exception e) {
                    System.err.println(e.getClass().getName());
                    siteMapBuilders.clear();
                    addSite(site, 0, false);
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

    public void addSite(Site site, int siteMapSize, Boolean isIndexing) {
        SiteEntity siteEntity = (isIndexing)
                ? new SiteEntity()
                : siteRepository.findByUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        if ((siteMapSize < 2 && !isIndexing)
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

    public void addPage(Map<String, PageDTO> siteMap, SiteEntity siteEntity, int siteMapSize) {
        if ((siteMapSize < 2)
                || (lastError.equals("ConnectException"))
                || (lastError.equals("SocketTimeoutException"))
                || (lastError.equals("NoRouteToHostException"))
                || (isInterrupted)) {
            return;
        } else {
            siteMap.forEach((key, value) -> {
                PageEntity pageEntity = new PageEntity();
                pageEntity.setSite(siteRepository.findByUrl(siteEntity.getUrl()));
                pageEntity.setCode(value.getCode());
                pageEntity.setPath(value.getPath());
                pageEntity.setContent(value.getContent());
                pageRepository.save(pageEntity);
                pageRepository.flush();
            });
        }
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
//        System.out.println("siteMapBuilders.size: " + siteMapBuilders.size());
        for (SiteMapBuilder siteMapBuilder : siteMapBuilders) {
            siteMapBuilder.stopScanning();
        }
        siteMapBuilders.clear();
    }

    public void playSound() {
        try {
            File soundFile =
                    new File("src/main/java/searchengine/resources/nkzs.wav");
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
        } catch (Exception ignored) {

        }
    }
}





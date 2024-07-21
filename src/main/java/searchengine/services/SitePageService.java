package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@EnableAsync
@RequiredArgsConstructor
public class SitePageService {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final List<SiteMapBuilder> siteMapBuilders = new ArrayList<>();
    private final LemmaIndexService lemmaIndexService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final SitesList sitesList;
    private CountDownLatch latch = new CountDownLatch(0);
    private static String lastError = "";
    private static Boolean isInterrupted;
    private LocalDateTime start;

    @Async
    public void scheduleScanSite(Boolean isIndexPage, String url) {
        valueLogger.info("scheduleScanSite running");
        start = LocalDateTime.now();
        isInterrupted = false;
        latch = new CountDownLatch(sitesList.getSites().size());
        if (!isIndexPage) {// режим 'startIndexing'
            for (Site site : sitesList.getSites()) {
                String siteUrl = site.getUrl();
                valueLogger.info("Удаление записей " + siteUrl + "...");
                siteRepository.deleteByUrl(siteUrl);// каскадное удаление одноименной записи в таблице 'site' и записей в 'page'
                addSite(siteUrl, 0, true);
                scanSite(siteUrl, isIndexPage);
            }
        } else {
            scanSite(url, isIndexPage); // режим 'indexPage'
        }
        try {
            latch.await();
            valueLogger.info(("Сканирование завершено.").toUpperCase());
            Duration duration = Duration.between(start,LocalDateTime.now());
            valueLogger.info(duration.getSeconds() + " СЕКУНД.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void scanSite(String url, boolean isIndexPage) {
        valueLogger.info("scanSite running.");
        executor.submit(() -> {
            try {
                String startUrl = url;
                if (startUrl.endsWith("/")) {
                    startUrl = startUrl.substring(0, startUrl.length() - 1);
                }
                SiteEntity siteEntity;
                if (isIndexPage) {
                    siteEntity = siteRepository.findByUrl("https://" + getHostName(url));
                } else siteEntity = siteRepository.findByUrl(url);
                SiteMapBuilder siteMapBuilder = new SiteMapBuilder(startUrl, startUrl, siteRepository, siteEntity, isIndexPage);
                siteMapBuilders.add(siteMapBuilder);
                HashMap<String, PageDTO> siteMap = (HashMap<String, PageDTO>) siteMapBuilder.buildSiteMap();
                int siteMapSize = siteMap.size();
                valueLogger.info("Сканирование " + url + " завершено. Размер списка: "
                        + siteMapSize + ".");
                if (!isIndexPage) {
                    addSite(url, siteMapSize, false);
                }
                addPage(siteMap, siteEntity, siteMapSize, isIndexPage, url);
                playSound();
                latch.countDown();
                siteMapBuilders.clear();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println(e.getClass().getName());
                siteMapBuilders.clear();
                addSite(url, 0, false);
                latch.countDown();
            }
        });
    }

    public void addSite(String url, int siteMapSize, Boolean isIndexing) {
        SiteEntity siteEntity = (isIndexing)
                ? new SiteEntity()
                : siteRepository.findByUrl(url);
        siteEntity.setName(getHostName(url));
        siteEntity.setUrl(url);
        if ((siteMapSize < 2 && !isIndexing)
                || (lastError.equals("ConnectException") && !isIndexing)
                || (lastError.equals("SocketTimeoutException") && !isIndexing)
                || (lastError.equals("NoRouteToHostException") && !isIndexing)
                || (lastError.equals("HttpHostConnectException") && !isIndexing)
                || (isInterrupted)) {
            siteEntity.setStatus(SiteStatus.FAILED);
            siteEntity.setLastError((isInterrupted)
                    ? "Индексация остановлена пользователем" : lastError);
        } else {
            siteEntity.setStatus((isIndexing) ? SiteStatus.INDEXING : SiteStatus.INDEXED);
            siteEntity.setLastError("");
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String formattedDateTime = now.format(formatter);
        siteEntity.setStatusTime(LocalDateTime.parse(formattedDateTime, formatter));
        siteRepository.save(siteEntity);
        siteRepository.flush();
    }

    public void addPage(Map<String, PageDTO> siteMap, SiteEntity siteEntity
            , int siteMapSize, boolean isIndexPage, String pagePath) {
        valueLogger.info("Анализ ссылок...");
        if ((siteMapSize <= 1) && ((lastError.equals("ConnectException"))
                || (lastError.equals("SocketTimeoutException"))
                || (lastError.equals("NoRouteToHostException"))
                || (isInterrupted))) {
            return;
        } else {
            siteMap.forEach((key, value) -> {
                String newPath = value.getPath();
                PageEntity pageEntity;
                if (!isIndexPage) {
                    pageEntity = new PageEntity();
                    pageEntity.setSite(siteRepository.findByUrl(siteEntity.getUrl()));
                } else {
                    pageEntity = pageRepository.getPageEntityByPath(newPath);
                    if (pageEntity == null) {
                        pageEntity = new PageEntity();
                        pageEntity.setSite(siteRepository.findByUrl("https://" + getHostName(pagePath)));
                    }
                }
                pageEntity.setCode(value.getCode());
                pageEntity.setPath(value.getPath());
                pageEntity.setContent(value.getContent());
                pageRepository.save(pageEntity);
                pageRepository.flush();
                valueLogger.info("add: " + pageEntity.getPath());
                lemmaIndexService.getWordBaseForms(pageEntity);
            });
        }

    }

    public void updateLastError(String error, String siteUrl
            , SiteRepository siteRepository, SiteEntity siteEntity) {
        if (lastError.equals(error)) {
            return;
        }
        valueLogger.info(error);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
        lastError = error;
    }

    public Integer getSiteMapBuildersSize() {
        return siteMapBuilders.size();
    }

    public void stopScanning() {
        valueLogger.info("Остановка сканирования ...");
        isInterrupted = true;
        for (SiteMapBuilder siteMapBuilder : siteMapBuilders) {
            siteMapBuilder.stopScanning();
        }
        siteMapBuilders.clear();
    }

    public String getHostName(String url) {
        String host = "";
        try {
            URL newUrl = new URL(url);
            host = newUrl.getHost();
        } catch (Exception ignored) {
        }
        return host;
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





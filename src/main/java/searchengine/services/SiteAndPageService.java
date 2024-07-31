package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.db.PageDTO;
import searchengine.dto.indexing.IndexingDTO;
import searchengine.errorHandling.IndexingErrorEvent;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;
import searchengine.SiteScaning.SiteMapBuilder;
import searchengine.repository.IndexRepository;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@EnableAsync
public class SiteAndPageService {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final List<SiteMapBuilder> siteMapBuilders = new ArrayList<>();
    private final LemmaAndIndexService lemmaAndIndexService;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesList;
    private static final AtomicBoolean isInterrupted = new AtomicBoolean(false);
    private final AtomicBoolean isIndexing = new AtomicBoolean();
    private final ApplicationEventPublisher eventPublisher;

    @Async
    public void siteAndPageUpdateManager(Boolean isPageIndexing, String indexedPageUrl, Site pagesSite) {
        valueLogger.info("Индексация начата.".toUpperCase());
        isIndexing.set(true);
        LocalDateTime start = LocalDateTime.now();
        isInterrupted.set(false);
        List<Runnable> tasks = new ArrayList<>();

            try {
                if (!isPageIndexing) {
                    sitesList.getSites().forEach(site -> {
                        valueLogger.info("Удаление записей " + site.getUrl());
                        siteRepository.deleteByUrl(site.getUrl());
                    });
                    sitesList.getSites().parallelStream()
                            .forEach(site -> processSite(site, indexedPageUrl, isPageIndexing));
                } else {
                    if (SiteAndPageService.isInterrupted().get()) return;
                    valueLogger.info("Удаление записей " + indexedPageUrl);
                    siteRepository.deleteByUrl(indexedPageUrl);
                    processSite(pagesSite, indexedPageUrl, isPageIndexing);
                }

                lemmaAndIndexService.indexingAllOrPageManager(isPageIndexing, indexedPageUrl);

                isIndexing.set(false);
                setSitesStatus();
                Duration duration = Duration.between(start, LocalDateTime.now());
                valueLogger.info(("Индексация завершена").toUpperCase() + " за ".toUpperCase()
                        + duration.getSeconds() + " сек.".toUpperCase());
                playSound();
            } catch (Exception e) {
                isIndexing.set(false);
                IndexingDTO indexingErrorDTO = new IndexingDTO(
                        false, e.getClass().toString());
                valueLogger.error("siteAndPageUpdateManager: " + indexingErrorDTO.getError());
                eventPublisher.publishEvent(new IndexingErrorEvent(this, indexingErrorDTO));
            }

    }

    protected void processSite(Site site, String indexedPageUrl, boolean isPageIndexing) {

        SiteEntity siteEntity;
        String url = !isPageIndexing ? site.getUrl() : indexedPageUrl;
        siteEntity = updateSite(site.getUrl(), isPageIndexing);
        valueLogger.info("Сканирование " + (!isPageIndexing ? url : indexedPageUrl) + "...");
        HashMap<String, PageDTO> siteMap = scanSite(siteEntity, isPageIndexing, indexedPageUrl);
        updatePage(siteMap, siteEntity);

    }


    protected HashMap<String, PageDTO> scanSite(SiteEntity siteEntity, boolean isPageIndexing, String indexedPageUrl) {
        HashMap<String, PageDTO> siteMap = new HashMap<>();

        try {
            String url = !isPageIndexing ? siteEntity.getUrl() : indexedPageUrl;
            SiteMapBuilder siteMapBuilder = new SiteMapBuilder(siteRepository, siteEntity, isPageIndexing, url);
            siteMapBuilders.add(siteMapBuilder);
            siteMap = (HashMap<String, PageDTO>) siteMapBuilder.buildSiteMap();
            valueLogger.info("Сканирование " + url + " завершено. Количество страниц: "
                    + siteMap.size() + ".");
            siteMapBuilders.clear();
        } catch (Exception e) {
            siteMapBuilders.clear();
            IndexingDTO indexingErrorDTO = new IndexingDTO(
                    false, "Невозможно установить связь с сервером!");
            eventPublisher.publishEvent(new IndexingErrorEvent(this, indexingErrorDTO));
        }

        return siteMap;
    }

    private SiteEntity updateSite(String siteUrl, boolean isPageIndexing) {
        SiteEntity siteEntity;
        if (!isPageIndexing) {
            siteEntity = new SiteEntity();
            siteEntity.setName(getHostName(siteUrl));
            if (siteUrl.endsWith("/")) {
                siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
            }
            siteEntity.setUrl(siteUrl);
        } else {
            siteEntity = siteRepository.getByUrl(siteUrl);
        }
        siteEntity.setStatus(SiteStatus.INDEXING);
        siteEntity.setLastError("");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String formattedDateTime = now.format(formatter);
        siteEntity.setStatusTime(LocalDateTime.parse(formattedDateTime, formatter));
        siteRepository.save(siteEntity);
        return siteEntity;
    }

    private void updatePage(Map<String, PageDTO> siteMap, SiteEntity siteEntity) {
        valueLogger.info("Анализ ссылок...".toUpperCase());
        if (!(siteMap.size() <= 1) || !isInterrupted.get()) {
            siteMap.forEach((key, value) -> {
                if (SiteAndPageService.isInterrupted().get()) return;
                PageEntity pageEntity;
                pageEntity = new PageEntity();
                pageEntity.setSite(siteRepository.findByUrl(siteEntity.getUrl()));
                pageEntity.setCode(value.getCode());
                pageEntity.setPath(value.getPath());
                pageEntity.setContent(value.getContent());
                pageRepository.save(pageEntity);
            });
        }
    }

    public AtomicBoolean isIndexing() {
        return isIndexing;
    }

    public static AtomicBoolean isInterrupted() {
        return isInterrupted;
    }

    public void stopScanning() {
        valueLogger.info("Остановка сканирования ...".toUpperCase());
        isInterrupted.set(true);
        for (SiteMapBuilder siteMapBuilder : siteMapBuilders) {
            siteMapBuilder.stopScanning();
        }
        siteMapBuilders.clear();
    }

    private String getHostName(String url) {
        String host = "";
        try {
            URL newUrl = new URL(url);
            host = newUrl.getHost();
        } catch (Exception ignored) {
            valueLogger.info("error in getHostName()".toUpperCase());
        }
        return host;
    }

    private void setSitesStatus() {
        for (SiteEntity siteEntity : siteRepository.findAll()) {
            if (isInterrupted.get()) {
                siteEntity.setStatus(SiteStatus.FAILED);
                siteEntity.setLastError("Индексация остановлена пользователем");
                siteRepository.save(siteEntity);
            } else if (pageRepository.findBySiteId(siteEntity.getId()).size() > 0) {
                for (PageEntity pageEntity : pageRepository.findBySiteId(siteEntity.getId())) {
                    if (indexRepository.countByPageId(pageEntity.getId()) > 0) {
                        siteEntity.setStatus(SiteStatus.INDEXED);
                        siteEntity.setLastError("");
                        siteRepository.save(siteEntity);
                    } else {
                        siteEntity.setStatus(SiteStatus.FAILED);
                        siteRepository.save(siteEntity);
                    }
                }
            } else {
                siteEntity.setStatus(SiteStatus.FAILED);
                siteRepository.save(siteEntity);
            }
        }
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
            valueLogger.info("error in playSound()".toUpperCase());
        }
    }

}
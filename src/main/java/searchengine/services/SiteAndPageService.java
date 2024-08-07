package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import searchengine.SiteScaning.SiteMapBuilder;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.db.PageDTO;
import searchengine.dto.indexing.IndexingDTO;
import searchengine.exception.ExceptionHandlerController;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final LemmaRepository lemmaRepository;
    private final SitesList sitesList;
    private static final AtomicBoolean isInterrupted = new AtomicBoolean(false);
    private final AtomicBoolean isIndexing = new AtomicBoolean();
    private final AtomicBoolean isServerErr = new AtomicBoolean();
    private final ExceptionHandlerController exceptionHandler;
    private Site pagesSite;

    @Async
    public CompletableFuture<IndexingDTO> siteAndPageUpdateManager(Boolean isPageIndexing, String indexedPageUrl) {
        CompletableFuture<IndexingDTO> future = new CompletableFuture<>();
        if (isIndexing.get()) {
            future.complete(new IndexingDTO(false, "Индексация уже запущена!"));
            return future;
        }
        if (isPageIndexing && !getIsURLCorrect(indexedPageUrl)) {
            future.complete(new IndexingDTO(false, "Неверный формат URL!"));
            return future;
        }
        if (isPageIndexing && getSiteByIndexedPage(indexedPageUrl) == null) {
            future.complete(new IndexingDTO(false
                    , "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
            return future;
        } else pagesSite = getSiteByIndexedPage(indexedPageUrl);

        future.complete(new IndexingDTO(true, "Индексация начата!"));
        CompletableFuture.runAsync(() -> {
            valueLogger.info("Индексация начата.".toUpperCase());
            isIndexing.set(true);
            isServerErr.set(false);
            LocalDateTime start = LocalDateTime.now();
            isInterrupted.set(false);
            try {
                SiteEntity siteEntity;
                if (!isPageIndexing) {
                    for (Site site : sitesList.getSites()) {
                        if (SiteAndPageService.isInterrupted().get()) return;
                        if (siteRepository.findByUrl(site.getUrl()) != null) {
                            siteEntity = siteRepository.findByUrl(site.getUrl());
                            List<PageEntity> pageEntities = pageRepository.findBySiteId(siteEntity.getId());
                            valueLogger.info("Удаление записей " + site.getUrl());
                            for (PageEntity pageEntity : pageEntities) {
                                List<IndexEntity> indexEntities = indexRepository.findByPageId(pageEntity.getId());
                                indexRepository.deleteAllInBatch(indexEntities);
                            }
                            List<LemmaEntity> lemmaEntities = lemmaRepository.findBySite(siteEntity);
                            for (LemmaEntity lemmaEntity : lemmaEntities) {
                                List<IndexEntity> indexEntities = indexRepository.findByLemma_Id(lemmaEntity.getId());
                                indexRepository.deleteAllInBatch(indexEntities);
                            }
                            lemmaRepository.deleteAllInBatch(lemmaEntities);
                            pageRepository.deleteAllInBatch(pageEntities);
                        }
                    }
                    sitesList.getSites().parallelStream() // ---
                            .forEach(site -> processSite(site, indexedPageUrl, isPageIndexing));
                } else {
                    if (SiteAndPageService.isInterrupted().get()) return;
                    URL parsedUrl = new URL(indexedPageUrl);
                    String path = parsedUrl.getPath();
                    if (pageRepository.findByPath(path) != null) {
                        PageEntity pageEntity = pageRepository.findByPath(path);
                        List<IndexEntity> indexEntities = indexRepository.findByPageId(pageEntity.getId());
                        indexRepository.deleteAllInBatch(indexEntities);
                    }
                    processSite(pagesSite, indexedPageUrl, isPageIndexing);
                }

                lemmaAndIndexService.switchAllOrPageManager(isPageIndexing, indexedPageUrl);

                isIndexing.set(false);
                setSitesStatus();
                Duration duration = Duration.between(start, LocalDateTime.now());
                valueLogger.info(("Индексация завершена").toUpperCase() + " за ".toUpperCase()
                        + duration.getSeconds() + " сек.".toUpperCase());
                beep();
            } catch (Exception e) {
                valueLogger.error("ERROR in siteAndPageUpdateManager: " + e.getClass());
                isIndexing.set(false);
                isServerErr.set(true);
                exceptionHandler.handleException(e);
            }
        });
        return future;
    }

    private boolean getIsURLCorrect(String indexedPageUrl) {
        String urlRegex = "^(https?://)?(www\\.)?([-a-zA-Z0-9@:%._+~#=]{2,256}\\.[a-z]{2,6})\\b([-a-zA-Z0-9@:%_+.~#?&//=]*)$";
        Pattern pattern = Pattern.compile(urlRegex);
        Matcher matcher = pattern.matcher(indexedPageUrl);
        if (!matcher.matches()) {
            return false;
        }
        return true;
    }

    private Site getSiteByIndexedPage(String indexedPageUrl) {
        for (Site site : sitesList.getSites()) {
            if (indexedPageUrl.startsWith(site.getUrl())) {
                return site ;
            }
        }
        return null;
    }

    private void processSite(Site site, String indexedPageUrl, boolean isPageIndexing) {

        SiteEntity siteEntity;
        String url = !isPageIndexing ? site.getUrl() : indexedPageUrl;
        siteEntity = updateSite(site.getUrl(), isPageIndexing);
        siteRepository.save(siteEntity);
        valueLogger.info("Сканирование " + (!isPageIndexing ? url : indexedPageUrl) + "...");
        HashMap<String, PageDTO> siteMap = scanSite(siteEntity, isPageIndexing, indexedPageUrl);
        updatePage(siteMap, siteEntity);

    }

    private SiteEntity updateSite(String siteUrl, boolean isPageIndexing) {

        SiteEntity siteEntity;
        if (!isPageIndexing) {
            if (siteRepository.findByUrl(siteUrl) != null) { //проверка отсутствия сайта в таблице
                siteEntity = siteRepository.getByUrl(siteUrl);
            } else {
                siteEntity = new SiteEntity();
                siteEntity.setName(getHostName(siteUrl));
                if (siteUrl.endsWith("/")) {
                    siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
                }
                siteEntity.setUrl(siteUrl);
            }
        } else {
            siteEntity = siteRepository.getByUrl(siteUrl);
        }
        siteEntity.setStatus(SiteStatus.INDEXING);
        siteEntity.setLastError("");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String formattedDateTime = now.format(formatter);
        siteEntity.setStatusTime(LocalDateTime.parse(formattedDateTime, formatter));
        return siteEntity;
    }

    private HashMap<String, PageDTO> scanSite(SiteEntity siteEntity, boolean isPageIndexing, String indexedPageUrl) {
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
            exceptionHandler.handleExceptionNet(e, siteEntity);
        }
        return siteMap;
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

    public CompletableFuture<IndexingDTO> stopScanning() {
        CompletableFuture<IndexingDTO> future = new CompletableFuture<>();
        if (!isIndexing.get()) {
            future.complete(new IndexingDTO(false, "Индексация не запущена!"));
            return future;
        }
        valueLogger.info("Остановка сканирования ...".toUpperCase());
        isInterrupted.set(true);
        for (SiteMapBuilder siteMapBuilder : siteMapBuilders) {
            siteMapBuilder.stopScanning();
        }
        siteMapBuilders.clear();
        future.complete(new IndexingDTO(true, "Индексация будет остановлена!"));
        return future;
    }

    private String getHostName(String url) {
        String host = "";
        try {
            URL newUrl = new URL(url);
            host = newUrl.getHost();
        } catch (Exception e) {
            valueLogger.info("ERROR in getHostName(): " + e.getClass());
        }
        return host;
    }

    public AtomicBoolean getIsServerErr() {
        return isServerErr;
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

    public void beep() {
        try {
            File soundFile =
                    new File("src/main/java/searchengine/resources/nkzs.wav");
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
        } catch (Exception e) {
            valueLogger.info("ERROR in playSound(): " + e.getClass());
        }
    }

    public AtomicBoolean getIsIndexing() {
        return isIndexing;
    }
}
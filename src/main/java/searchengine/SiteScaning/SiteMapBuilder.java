package searchengine.SiteScaning;

import lombok.RequiredArgsConstructor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import searchengine.dto.db.PageDTO;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class SiteMapBuilder {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final ForkJoinPool pool = new ForkJoinPool();
    private final SiteRepository siteRepository;
    private final SiteEntity siteEntity;
    private final List<String> visitLinks = new ArrayList<>();
    private final boolean isPageIndexing;
    private final Map<String, PageDTO> siteMap = new HashMap<>();
    private final AtomicBoolean isScanning = new AtomicBoolean(true);
    private static String userAgent;
    private static String referrer;
    private static String links;
    private final String indexedPageUrl;

    public Map<String, PageDTO> buildSiteMap() {
        try {
            FileInputStream inputStream = new FileInputStream("src/main/resources/application.yaml");
            Yaml yaml = new Yaml();
            Map<String, String> config = yaml.load(inputStream);
            userAgent = config.get("userAgent");
            referrer = config.get("referrer");
            links = config.get("links");
        } catch (Exception e) {
            valueLogger.error("error in buildSiteMap()".toUpperCase());
        }
        String url = !isPageIndexing ?  siteEntity.getUrl() : indexedPageUrl;
        SiteMapRecursiveAction mainScan = new SiteMapRecursiveAction(url);
        pool.invoke(mainScan);
        pool.shutdown();
        return siteMap;
    }

    @RequiredArgsConstructor
    private class SiteMapRecursiveAction extends RecursiveAction {
        private final String url;

        @Override
        protected void compute() {
            if (!isScanning.get()) {
                return;
            }
            Map<String, PageDTO> urlFoundLinks = parsingUrl(url); // список найденных ссылок для текущего URL
            siteMap.putAll(urlFoundLinks); // добавляю его в основной siteMap
            visitLinks.add(url); // помечаю текущий URL как посещенный

            List<SiteMapRecursiveAction> scanTaskList = new ArrayList<>(); // Подготовка списка для сканирования
            urlFoundLinks.forEach((key, value) -> { // Sub-поиск для каждой ссылки из urlFoundLinks
                if (!visitLinks.contains((String) key)) {
                    SiteMapRecursiveAction subScan = new SiteMapRecursiveAction((String) key);
                    scanTaskList.add(subScan);
                }
            });
            if (!scanTaskList.isEmpty()) {
                invokeAll(scanTaskList);
                if (!isPageIndexing) {
                    setStatusTime(); //периодическое обновление поля 'StatusTime' в 'site'
                }
            }
            try {
                TimeUnit.MILLISECONDS.sleep(150);
            } catch (InterruptedException e) {
                valueLogger.info("ERROR IN compute()");

            }
        }
    }

    private Map<String, PageDTO> parsingUrl(String url) {
        Map<String, PageDTO> urlFoundLinks = new HashMap<>();
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer).get();
            Elements linkElements = doc.select(links);
            if (!linkElements.isEmpty()) {
                for (Element linkElement : linkElements) {
                    String absLink = linkElement.absUrl("href");
                    if (absLink.endsWith("/")) {
                        absLink = absLink.substring(0, absLink.length() - 1);
                    }
                    if (absLink.startsWith(!isPageIndexing ? siteEntity.getUrl() : indexedPageUrl)
                            && !urlFoundLinks.containsKey(absLink) && !absLink.contains("#")
                            && !absLink.contains("tags")
                            && !siteMap.containsKey(absLink)) {
                        System.out.println("add: " + absLink);
                        HttpGet httpGet = new HttpGet(absLink);
                        String href = linkElement.attr("href"); // Получаем атрибут href элемента
                        PageDTO pageDTO = new PageDTO();
                        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                            int code = response.getStatusLine().getStatusCode();
                            if (code == 200) {
                                pageDTO.setContent(EntityUtils.toString(response.getEntity()));
                            } else {
                                pageDTO.setContent("Error getting page content: " + response.getStatusLine());
                            }
                            pageDTO.setPath(href);
                            pageDTO.setCode(code);
                            urlFoundLinks.put(absLink, pageDTO);
                        } catch (Exception e) {
                            valueLogger.info("ERROR IN parsingUrl()1"  + e.getClass().getName());
                            siteEntity.setLastError(e.getClass().toString());
                        }
                    }
                }
            }
            httpClient.close();
        } catch (UnknownHostException ue) {
            String error = "Неизвестный хост или нет доступа к интернет.";
            valueLogger.error(error + " " + ue.getClass().toString());
            siteEntity.setLastError(error);
        } catch (UnsupportedMimeTypeException me) {
            String error = "Неподдерживаемый MIME-тип.";
            valueLogger.error(error + " " + me.getClass().toString());
            siteEntity.setLastError(error);
        } catch (ConnectException ce) {
            String error = "Ошибка подключения.";
            valueLogger.error(error + " " + ce.getClass().toString());
            siteEntity.setLastError(error);
        } catch (HttpStatusException he) {
            String error = "Неверный HTTP-статус.";
            valueLogger.error(error + " " + he.getClass().toString());
            siteEntity.setLastError(error);
        } catch (NoRouteToHostException nre) {
            String error = "Невозможно установить соединение с удаленным узлом.";
            valueLogger.error(error + " " + nre.getClass().toString());
            siteEntity.setLastError(error);
        }
        catch (IOException e) {
                valueLogger.error("ERROR IN parsingUrl()2" + e.getClass().getName());
                siteEntity.setLastError(e.getClass().toString());
            try {
                httpClient.close();
            } catch (IOException ignored) {
                valueLogger.info("ERROR IN scanSite()".toUpperCase());
            }
        } catch (Exception e) {
            valueLogger.error("ERROR IN parsingUrl()3".toUpperCase());
            System.err.println(e.getClass());
        }
        siteRepository.save(siteEntity);
        return urlFoundLinks;
    }

    private void setStatusTime() { //периодическое обновление поля 'StatusTime' в 'site'
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String formattedDateTime = now.format(formatter);
        siteEntity.setStatusTime(LocalDateTime.parse(formattedDateTime, formatter));
        siteRepository.save(siteEntity);
    }

    public void stopScanning() {
        isScanning.set(false);
        try {
            pool.shutdown();
        } catch (Exception e) {
            valueLogger.info("error in stopScanning()".toUpperCase());
        }
    }
}

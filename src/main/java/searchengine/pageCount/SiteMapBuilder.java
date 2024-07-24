package searchengine.pageCount;

import lombok.RequiredArgsConstructor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.yaml.snakeyaml.Yaml;
import searchengine.dto.db.PageDTO;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;
import searchengine.services.SiteAndPageService;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SiteMapBuilder {

    private final ForkJoinPool pool = new ForkJoinPool();
    private Map<String, PageDTO> siteMap = new HashMap<>();
    private final List<String> visitLinks = new ArrayList<>();
    private final String url;
    private final String startUrl;
    private SiteAndPageService siteAndPageService;
    private volatile boolean isScanning = true;
    private final SiteRepository siteRepository;
    private final SiteEntity siteEntity;
    private static String userAgent;
    private static String referrer;
    private static String links;
    private final boolean isPageIndexing;

    public void stopScanning() {
        isScanning = false;
        try {
            pool.shutdown();
        } catch (Exception e) {
        }
    }

    public Map<String, PageDTO> buildSiteMap() {
        try {
            FileInputStream inputStream = new FileInputStream("src/main/resources/application.yaml");
            Yaml yaml = new Yaml();
            Map<String, String> config = yaml.load(inputStream);
            userAgent = config.get("userAgent");
            referrer = config.get("referrer");
            links = config.get("links");
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Сканирование " + startUrl + " ...");
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
            if (!isScanning) {
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
                throw new RuntimeException(e);
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
                    if (absLink.startsWith(startUrl) /*&& !url.contains(absLink)*/
                            && !urlFoundLinks.containsKey(absLink) && !absLink.contains("#")
                            && !absLink.contains("tags") /*&& !absLink.contains("/feed")*/
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
                            e.printStackTrace();
                        }
                    }
                }
            }
            httpClient.close();
        } catch (Exception e) {
            if (!isPageIndexing) {
                setLastError(e);
            }
            try {
                httpClient.close();
            } catch (IOException ignored) {
            }
        }
        return urlFoundLinks;
    }

    private void setLastError(Exception e) {
        siteAndPageService = new SiteAndPageService(
                null, null, null, null);
        siteAndPageService.updateLastError(e.getClass().getSimpleName(), startUrl
                , siteRepository, siteEntity);
    }

    private void setStatusTime() { //периодическое обновление поля 'StatusTime' в 'site'
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String formattedDateTime = now.format(formatter);
        siteEntity.setStatusTime(LocalDateTime.parse(formattedDateTime, formatter));
        siteRepository.save(siteEntity);
    }
}

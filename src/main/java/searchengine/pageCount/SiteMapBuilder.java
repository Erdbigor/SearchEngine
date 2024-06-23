package searchengine.pageCount;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.yaml.snakeyaml.Yaml;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;
import searchengine.services.BuildMapService;

import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

public class SiteMapBuilder {

    private final ForkJoinPool pool = new ForkJoinPool();
    private final List<String> siteMap = new ArrayList<>();
    private final List<String> visitLinks = new ArrayList<>();
    private final String url;
    private final String startUrl;
    private final BuildMapService buildMapService = new BuildMapService();
    private volatile boolean isScanning = true;
    private final SiteRepository siteRepository;
    private final SiteEntity siteEntity;
    private static String userAgent;
    private static String referrer;
    private static String links;

    public SiteMapBuilder(String url, String startUrl, SiteRepository siteRepository, SiteEntity siteEntity) {
        this.url = url;
        this.startUrl = startUrl;
        this.siteRepository = siteRepository;
        this.siteEntity = siteEntity;
    }

    public void stopScanning() {
        isScanning = false;
        System.out.println("pool.getRunningThreadCount: " + pool.getRunningThreadCount());
        try {
            pool.shutdown();
        } catch (Exception e) {
        }
    }

    public List<String> buildSiteMap() {
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

    private class SiteMapRecursiveAction extends RecursiveAction {
        private final String url;

        public SiteMapRecursiveAction(String url) {
            this.url = url;
        }

        @Override
        protected void compute() {
            if (!isScanning) {
                return;
            }
            List<String> urlFoundLinks = parsingUrl(url); // список найденных ссылок для текущего URL
            siteMap.addAll(urlFoundLinks); // добавляю его в основной siteMap
            visitLinks.add(url); // помечаю текущий URL как посещенный

            List<SiteMapRecursiveAction> scanTaskList = new ArrayList<>(); // Подготовка списка для сканирования
            for (String link : urlFoundLinks) { // Sub-поиск для каждой ссылки из urlFoundLinks
                if (!visitLinks.contains(link)) {
                    SiteMapRecursiveAction subScan = new SiteMapRecursiveAction(link);
                    scanTaskList.add(subScan);
                }
            }
            if (!scanTaskList.isEmpty()) {
                invokeAll(scanTaskList);
                siteEntity.setStatusTime(LocalDateTime.now());//блок периодического обновления поля StatusTime
                siteRepository.save(siteEntity);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(150);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List<String> parsingUrl(String url) {
        List<String> urlFoundLinks = new ArrayList<>();
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
                    if (absLink.startsWith(startUrl) && !url.contains(absLink)
                            && !urlFoundLinks.contains(absLink) && !absLink.contains("#")
                            && !absLink.contains("tags") /*&& !absLink.contains("/feed")*/
                            && !siteMap.contains(absLink)) {
//                        System.out.println(absLink);
                        urlFoundLinks.add(absLink);
                    }
                }
            }
        } catch (Exception e) {
            buildMapService.updateLastError(e.getClass().getSimpleName(), startUrl
                    , siteRepository, siteEntity);
        }
        return urlFoundLinks;
    }
}

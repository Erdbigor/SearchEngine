package searchengine.pageCount;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;


public class SiteMapBuilder {

    private final ForkJoinPool pool = new ForkJoinPool();
    private final List<String> siteMap = new ArrayList<>();
    private final List<String> visitLinks = new ArrayList<>();
    private final String url;
    private final String startUrl;

    public SiteMapBuilder(String url, String startUrl) {
        this.url = url;
        this.startUrl = startUrl;
    }

    public List<String> buildSiteMap() {
        System.out.println("Сканирование " + startUrl + " начато...");
        SiteMapRecursiveAction mainScan = new SiteMapRecursiveAction(url);
        pool.invoke(mainScan);
        pool.shutdown();
        System.out.println("Сканирование " + startUrl + " окончено.");
        return siteMap;
    }

    private class SiteMapRecursiveAction extends RecursiveAction {

        private final String url;

        public SiteMapRecursiveAction(String url) {
            this.url = url;
        }
        @Override
        protected void compute() {

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
            Document doc = Jsoup.connect(url).get();
            Elements linkElements = doc.select("a:not([class=\"link\"], [href$=\".docx\"], [href$=\".doc\"], " +
                    "[href$=\".pdf\"], [href$=\".xls\"], [href$=\".xlsx\"], [href$=\".jpg\"])[href]");
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
        } catch (java.net.SocketTimeoutException es) {
            es.printStackTrace();

        } catch (HttpStatusException e) {
            if (e.getStatusCode() != 404) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            if (e instanceof java.net.ConnectException && e.getMessage().contains("Connection timed out")) {
                System.err.println("Ошибка при подключении к URL: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception ex) {
            System.err.println("Произошла ошибка: " + ex.getMessage());
            ex.printStackTrace();
        }
        return urlFoundLinks;
    }
}

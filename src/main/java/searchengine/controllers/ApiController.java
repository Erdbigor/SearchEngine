package searchengine.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.IndexingDTO;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.mappers.IndexingPageMapper;
import searchengine.mappers.IndexingStartMapper;
import searchengine.mappers.IndexingStopMapper;
import searchengine.services.SitePageService;
import searchengine.services.LemmaService;
import searchengine.services.StatisticsService;

import java.net.URL;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SitePageService sitePageService;
    private final LemmaService lemmaService;
    private final SitesList sitesList;

    @Autowired
    public ApiController(StatisticsService statisticsService, SitePageService sitePageService, LemmaService lemmaService, SitesList sitesList) {
        this.statisticsService = statisticsService;
        this.sitePageService = sitePageService;
        this.lemmaService = lemmaService;
        this.sitesList = sitesList;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public IndexingDTO startIndexing() {
        int sizeBuildMapServices = sitePageService.getSiteMapBuildersSize();
        if (sizeBuildMapServices > 0) {
            return IndexingStartMapper.map(true);
        } else {
            sitePageService.scheduleScanSite(false, "");
            return IndexingStartMapper.map(false);
        }
    }

    @GetMapping("/stopIndexing")
    public IndexingDTO stopIndexing() {
        int sizeBuildMapServices = sitePageService.getSiteMapBuildersSize();
        if (sizeBuildMapServices > 0) {
            sitePageService.stopScanning();
            return IndexingStopMapper.map(true);
        } else {
            return IndexingStopMapper.map(false);
        }
    }

    @GetMapping("/{path}/**")
    public IndexingDTO indexPage(HttpServletRequest request) {
        int sizeBuildMapServices = sitePageService.getSiteMapBuildersSize();
        if (sizeBuildMapServices > 0) {
            return IndexingStartMapper.map(true);
        } else {
            String fullPath = request.getRequestURI(); // полный путь запроса
            String path = fullPath.substring("/api/".length()); //'example.com/about'
            String host;
            for (Site site : sitesList.getSites()) {
                try {
                    URL newUrl = new URL(site.getUrl());
                    host = newUrl.getHost();
                    if (path.contains(host)) {
                        String url = "https://" + path;
                        sitePageService.scheduleScanSite(true, url);
                        return IndexingPageMapper.map(true);
                    }
                } catch (Exception ignored) {
                    System.err.println("Данная страница находится за пределами сайтов, " +
                            "указанных в конфигурационном файле");
                }
            }
            return IndexingPageMapper.map(false);
        }
    }

}

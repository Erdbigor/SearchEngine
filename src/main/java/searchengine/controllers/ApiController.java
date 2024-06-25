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
import searchengine.services.BuildMapService;
import searchengine.services.StatisticsService;

import java.net.URL;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final BuildMapService buildMapService;
    private final SitesList sitesList;

    @Autowired
    public ApiController(StatisticsService statisticsService, BuildMapService buildMapService, SitesList sitesList) {
        this.statisticsService = statisticsService;
        this.buildMapService = buildMapService;
        this.sitesList = sitesList;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public IndexingDTO startIndexing() {
        int sizeBuildMapServices = buildMapService.getSiteMapBuildersSize();
        if (sizeBuildMapServices > 0) {
            return IndexingStartMapper.map(true);
        } else {
            buildMapService.scheduleScanSite(false, "");
            return IndexingStartMapper.map(false);
        }
    }

    @GetMapping("/stopIndexing")
    public IndexingDTO stopIndexing() {
        int sizeBuildMapServices = buildMapService.getSiteMapBuildersSize();
        if (sizeBuildMapServices > 0) {
            buildMapService.stopScanning();
            return IndexingStopMapper.map(true);
        } else {
            return IndexingStopMapper.map(false);
        }
    }

    @GetMapping("/{path}/**")
    public IndexingDTO indexPage(HttpServletRequest request) {
        int sizeBuildMapServices = buildMapService.getSiteMapBuildersSize();
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
                        buildMapService.scheduleScanSite(true, url);
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

package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.IndexingDTO;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.mappers.IndexingStartMapper;
import searchengine.mappers.IndexingStopMapper;
import searchengine.services.BuildMapService;
import searchengine.services.StatisticsService;

import java.util.concurrent.CountDownLatch;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final BuildMapService buildMapService;

    @Autowired
    public ApiController(StatisticsService statisticsService, BuildMapService buildMapService) {
        this.statisticsService = statisticsService;
        this.buildMapService = buildMapService;
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
            buildMapService.scheduleScanSite();
            return IndexingStartMapper.map(false);
        }
    }

    @GetMapping("/stopIndexing")
    public IndexingDTO stopIndexing() {
        int sizeBuildMapServices = buildMapService.getSiteMapBuildersSize();
//        System.out.println("sizeBuildMapServices " + sizeBuildMapServices);
        if (sizeBuildMapServices > 0) {
            buildMapService.stopScanning();
            return IndexingStopMapper.map(true);
        } else {
            return IndexingStopMapper.map(false);
        }
    }

    @GetMapping("/indexPage")
    public IndexingDTO indexPage() {
        return null;
    }
}

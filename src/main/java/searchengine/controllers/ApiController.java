package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.IndexingDTO;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.mappers.IndexingMapper;
import searchengine.services.BuildMapService;
import searchengine.services.StatisticsService;

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
        boolean isScanning = buildMapService.isScanning();
        if (isScanning) {
            return IndexingMapper.map(true);
        } else {
            buildMapService.scheduleScanSite();
            return IndexingMapper.map(false);
        }
    }
}

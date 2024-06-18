package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.BuildMapService;
import searchengine.services.StatisticsService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    @Autowired
    private final StatisticsService statisticsService;
    @Autowired
    private final BuildMapService buildMapService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public Map<String, Object> startIndexing() {
        Map<String, Object> response = new HashMap<>();
        if (buildMapService.isScanning()) {
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return response;
        } else {
            buildMapService.scheduleScanSite();
            response.put("result", true);
            return response;
        }
    }
}

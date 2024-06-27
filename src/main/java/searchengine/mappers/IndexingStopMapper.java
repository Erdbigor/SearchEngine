package searchengine.mappers;

import org.springframework.stereotype.Component;
import searchengine.dto.IndexingDTO;
@Component
public class IndexingStopMapper {

    private final IndexingDTO indexingDTO = new IndexingDTO();

    public IndexingDTO map(boolean isScanning) {
        if (isScanning) {
            indexingDTO.setResult(true);
            indexingDTO.setError("Индексация остановлена!");
        } else {
            indexingDTO.setResult(false);
            indexingDTO.setError("Индексация не запущена.");
        }
        return indexingDTO;
    }
}

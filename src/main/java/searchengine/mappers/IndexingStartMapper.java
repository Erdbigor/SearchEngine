package searchengine.mappers;

import org.springframework.stereotype.Component;
import searchengine.dto.IndexingDTO;
@Component
public class IndexingStartMapper {

    private final IndexingDTO indexingDTO = new IndexingDTO();

    public IndexingDTO map(boolean isScanning) {
        if (isScanning) {
            indexingDTO.setResult(false);
            indexingDTO.setError("Индексация уже запущена!");
        } else {
            indexingDTO.setResult(true);
            indexingDTO.setError("Индексация начата.");
        }
        return indexingDTO;
    }
}

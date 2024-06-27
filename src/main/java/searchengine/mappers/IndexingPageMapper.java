package searchengine.mappers;

import org.springframework.stereotype.Component;
import searchengine.dto.IndexingDTO;
@Component
public class IndexingPageMapper {

    private final IndexingDTO indexingDTO = new IndexingDTO();

    public IndexingDTO map(boolean isScanning) {
        if (isScanning) {
            indexingDTO.setResult(true);
            indexingDTO.setError("Индексация страницы начата!");
        } else {
            indexingDTO.setResult(false);
            indexingDTO.setError("Данная страница находится за пределами сайтов" +
                    ", указанных в конфигурационном файле");
        }
        return indexingDTO;
    }
}

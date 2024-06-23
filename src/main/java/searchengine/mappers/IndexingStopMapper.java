package searchengine.mappers;

import searchengine.dto.IndexingDTO;

public class IndexingStopMapper {

    private static final IndexingDTO indexingDTO = new IndexingDTO();

    public static IndexingDTO map(boolean isScanning) {
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

package searchengine.mappers;

import searchengine.dto.IndexingDTO;

public class IndexingMapper {

    private static final IndexingDTO indexingDTO = new IndexingDTO();

    public static IndexingDTO map(boolean isScanning) {
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

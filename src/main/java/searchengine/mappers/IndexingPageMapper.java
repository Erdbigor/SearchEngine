package searchengine.mappers;

import searchengine.dto.IndexingDTO;

public class IndexingPageMapper {

    private static final IndexingDTO indexingDTO = new IndexingDTO();

    public static IndexingDTO map(boolean isScanning) {
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

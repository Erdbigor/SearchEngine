package searchengine.exception;
import searchengine.dto.indexing.IndexingDTO;

public class IndexingErrorEvent {
    private final IndexingDTO indexingErrorDTO;

    public IndexingErrorEvent(Object source, IndexingDTO indexingErrorDTO) {
        super();
        this.indexingErrorDTO = indexingErrorDTO;
    }
    public IndexingDTO getIndexingErrorDTO() {
        return indexingErrorDTO;
    }
}

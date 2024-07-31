package searchengine.errorHandling;
import org.springframework.context.ApplicationEvent;
import searchengine.dto.indexing.IndexingDTO;

public class IndexingErrorEvent extends ApplicationEvent {
    private final IndexingDTO indexingErrorDTO;

    public IndexingErrorEvent(Object source, IndexingDTO indexingErrorDTO) {
        super(source);
        this.indexingErrorDTO = indexingErrorDTO;
    }

    public IndexingDTO getIndexingErrorDTO() {
        return indexingErrorDTO;
    }
}

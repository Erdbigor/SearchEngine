package searchengine.dto.db;

import lombok.Data;

@Data
public class IndexDTO {

    private long id;
    private long pageId;
    private long lemmaId;
    private float rank;

}

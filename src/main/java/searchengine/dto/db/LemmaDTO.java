package searchengine.dto.db;

import lombok.Data;

@Data
public class LemmaDTO {

    private long id;
    private long siteId;
    private String lemma;
    private int frequency;

}

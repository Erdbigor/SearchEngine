package searchengine.dto;

import lombok.Data;
import searchengine.model.SiteEntity;

@Data
public class PageDTO {

    private Long id;
    private Long siteId;
    private String path;
    private int code;
    private String content;

}

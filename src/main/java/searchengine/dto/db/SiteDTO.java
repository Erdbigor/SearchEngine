package searchengine.dto.db;

import lombok.Data;
import searchengine.model.SiteStatus;

import java.time.LocalDateTime;

@Data
public class SiteDTO {

    private long id;
    private LocalDateTime last_error;
    private SiteStatus status;
    private String lastError;
    private String url;
    private String name;

    public void setStatusTime(LocalDateTime parse) {
    }
}

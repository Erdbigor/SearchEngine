package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "page", indexes = {
        @Index(name = "path_index", columnList = "path")
})
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "site_id", referencedColumnName = "id")
    private SiteEntity site;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(nullable = false, length = 16777215)
    private String content;

    @PrePersist
    @PreUpdate
    protected void validatePath() {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Путь должен начинаться со слэша");
        }
    }

}

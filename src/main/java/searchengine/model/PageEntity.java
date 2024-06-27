package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cascade;

import java.util.List;

@Data
@Entity
@Table(name = "page", indexes = {
        @Index(name = "path_index", columnList = "path")
})
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "site_id", referencedColumnName = "id")
    private SiteEntity site;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(nullable = false, length = 16777215)
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.REMOVE)
    private List<IndexEntity> indexes;

}

package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "site")
@Data
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SiteStatus status;

    @Column(nullable = false)
    private LocalDateTime statusTime;

    private String lastError;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String name;

}

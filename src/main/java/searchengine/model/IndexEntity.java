package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "index", uniqueConstraints = {@UniqueConstraint(columnNames = {"page_id", "lemma_id"})})
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "page_id", referencedColumnName = "id")
    private PageEntity page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", referencedColumnName = "id")
    private LemmaEntity lemma;

    @Column(nullable = false, name = "`rank`")
    private float rank;

}

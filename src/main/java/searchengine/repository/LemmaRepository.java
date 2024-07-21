package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Collection;
import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    LemmaEntity findByLemma(String lemma);
    LemmaEntity getByLemma(String lemma);
    LemmaEntity getBySite(SiteEntity siteEntity);

    List<LemmaEntity> findBySite(SiteEntity siteEntity);
    @Query("SELECT MAX(i.frequency) FROM LemmaEntity i")
    int findMaxFrequency();

    LemmaEntity getById(long id);
}

package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {

    IndexEntity getByLemma_IdAndPage_Id(Long lemmaId, Long pageId);
    @Query("SELECT MAX(i.rank) FROM IndexEntity i")
    Float findMaxRank();

    List<IndexEntity> findByLemma_Id(long id);

    List<IndexEntity> findByPageId(long id);

    int countByPageId(long id);

    void deleteByPageId(long id);


}

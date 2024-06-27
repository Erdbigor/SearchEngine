package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {

    IndexEntity findByLemma_IdAndPage_Id(Long lemmaId, Long PageId);

    IndexEntity getByLemma_IdAndPage_Id(Long lemmaId, Long PageId);
}

package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;

@Repository
public interface PageRepository extends JpaRepository<PageEntity,Long> {
    boolean findByPath(String url);
    PageEntity getPageEntityByPath(String url);

}

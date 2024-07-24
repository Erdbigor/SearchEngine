package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageEntity,Long> {
    boolean findByPath(String url);
    PageEntity getPageEntityByPath(String url);

    PageEntity getBySite(SiteEntity siteEntity);

    List<PageEntity> findBySite(SiteEntity siteEntity);
    PageEntity findById(long id);

    List<PageEntity> findBySiteId(long id);

}

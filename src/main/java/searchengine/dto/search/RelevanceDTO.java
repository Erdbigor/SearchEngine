package searchengine.dto.search;

import lombok.Data;
import searchengine.dto.search.LemmaRankDTO;

import java.util.List;

@Data
public class RelevanceDTO {

    long pageId;
    List<LemmaRankDTO> lemmasRank;
    float absRelevance;
    float relRelevance;

}

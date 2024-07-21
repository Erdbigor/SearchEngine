package searchengine.dto.statistics;

import lombok.Data;

import java.util.List;

@Data
public class RelevanceDTO {

    long pageId;
    List<LemmaRankDTO> lemmasRank;
    float absRelevance;
    double relRelevance;

}

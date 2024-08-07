package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.dto.search.DataDTO;
import searchengine.dto.db.IndexDTO;
import searchengine.dto.db.LemmaDTO;
import searchengine.dto.search.LemmaRankDTO;
import searchengine.dto.search.RelevanceDTO;
import searchengine.dto.search.SearchDTO;
import searchengine.mappers.SearchMapper;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final WordsService wordsService;
    private final SnippedService snippedService;
    public List<DataDTO> resultDataDTOList;
    public final AtomicBoolean isSearch = new AtomicBoolean(false);
    private final SearchMapper searchMapper;
    private final SiteAndPageService siteAndPageService;

    public CompletableFuture<SearchDTO> searchResult(String query, String site, Integer offset, Integer limit, double frequencyThreshold) {
        AtomicBoolean isIndexing = siteAndPageService.getIsIndexing();
        CompletableFuture<SearchDTO> future = new CompletableFuture<>();
        if (isSearch.get()) {
            future.complete(new SearchDTO(false, 0, "Поиск уже идёт!", new ArrayList<>()));
            return future;
        }
        if (query.isEmpty()) {
            future.complete(searchMapper.map(false, offset, limit, isIndexing.get(), new ArrayList<>()));
            return future;
        }
        site = site == null ? "all_site" : site;
        offset = (offset == null) ? 0 : offset;
        limit = limit == null ? 20 : limit;
        valueLogger.info("query: " + query);
        valueLogger.info("site: " + site);
        valueLogger.info("offset: " + offset);
        valueLogger.info("limit: " + limit);
        valueLogger.info("frequencyThreshold: " + frequencyThreshold);
        isSearch.set(true);
        List<String> wordsBaseForms = getWordBaseForms(query, site, offset, limit);
        valueLogger.info("wordsBaseForms: " + wordsBaseForms);
        List<LemmaDTO> filteredSortedLemmas = filteringSortingLemmas(wordsBaseForms, frequencyThreshold);
        int filteredLemmasSize = filteredSortedLemmas.size();
        filteredSortedLemmas.forEach(filteredLemma -> valueLogger.info(filteredLemma.toString()));
        valueLogger.info("filteredLemmasSize: " + filteredLemmasSize);
        // ● Поиск страниц по леммам
        List<IndexDTO> foundByQueryIndexList;
        if (filteredLemmasSize > 0) {
            foundByQueryIndexList = getFoundByQueryIndexList(site, filteredSortedLemmas, filteredLemmasSize);
            valueLogger.info("Итого foundByQueryIndexList.size(): " + foundByQueryIndexList.size());
            foundByQueryIndexList.forEach(indexDTO -> valueLogger.info(indexDTO.toString()));
            List<RelevanceDTO> relevanceList = getRelevanceList(foundByQueryIndexList);
            valueLogger.info("relevanceList.size(): " + relevanceList.size());
            relevanceList.forEach(relevanceDTO -> valueLogger.info(relevanceDTO.toString()));
            if (relevanceList.size() > 0) {
                resultDataDTOList = snippedService.resultDataDTOs(relevanceList);
            } else resultDataDTOList = new ArrayList<>();

            isSearch.set(false);
            valueLogger.info("Поиск завершен!".toUpperCase());
        }
        setIsSearch(false);
        future.complete(searchMapper.map(true, offset, limit, isIndexing.get(), resultDataDTOList));
        return future;
    }

    private List<String> getWordBaseForms(String query, String site, Integer offset, Integer limit) {
        List<String> listWords = wordsService.getWords(null, query, site, offset, limit);
        List<String> wordsBaseForms = new ArrayList<>();
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            for (String word : listWords) {
                if (luceneMorph.checkString(word)) {
                    List<String> wordsInfo = luceneMorph.getMorphInfo(word);
                    String subString = wordsInfo.get(0);
                    if (subString.matches("^.\\|.*")
                            || subString.matches(".*\\|(p|n|o|l).*")) {
                    } else {
                        wordsBaseForms.addAll(luceneMorph.getNormalForms(word));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return wordsBaseForms;
    }

    private List<LemmaDTO> filteringSortingLemmas(List<String> wordsBaseForms, double frequencyThreshold) {

        //●	Исключаем из полученного списка леммы, которые встречаются на слишком большом количестве страниц
        List<LemmaEntity> filteredEntityLemmas = lemmaRepository.findAll().stream()
                .filter(lemmaEntity -> lemmaEntity.getFrequency()
                        > lemmaRepository.findMaxFrequency() * frequencyThreshold)
                .toList();
        filteredEntityLemmas.forEach(filteredEntityLemma -> valueLogger.info(filteredEntityLemma.getLemma()
                + " " + filteredEntityLemma.getFrequency()));

        wordsBaseForms.removeAll(filteredEntityLemmas.stream()
                .map(LemmaEntity::getLemma)
                .toList());
        wordsBaseForms.forEach(valueLogger::info);

        // ● Сортировка лемм в порядке увеличения frequency. Формирование LemmaDTO
        List<LemmaDTO> filteredLemmas = new ArrayList<>();
        for (String wbf : wordsBaseForms) {
            LemmaDTO lemmaDTO = new LemmaDTO();
            for (LemmaEntity lemmaEntity : lemmaRepository.findAll()) {
                if (lemmaEntity.getLemma().equals(wbf)) {
                    lemmaDTO.setId(lemmaEntity.getId());
                    lemmaDTO.setSiteId(lemmaEntity.getSite().getId());
                    lemmaDTO.setLemma(lemmaEntity.getLemma());
                    lemmaDTO.setFrequency(lemmaEntity.getFrequency());
                    filteredLemmas.add(lemmaDTO);
                }
            }
        }
        // ● Сортировка
        filteredLemmas.sort(Comparator.comparingInt(LemmaDTO::getFrequency));
        return filteredLemmas;
    }

    private List<IndexDTO> getFoundByQueryIndexList(String site, List<LemmaDTO> filteringSortingLemmas
            , int filteredLemmasSize) {

        List<IndexEntity> newIndexEntityList = new ArrayList<>();
        LemmaDTO[] lemmaDTO = new LemmaDTO[filteredLemmasSize];

        List<SiteEntity> siteEntities;
        List<PageEntity> pageEntities = new ArrayList<>();
        if (site.equals("all_site")) {
            siteEntities = siteRepository.findAll();
            for (SiteEntity siteEntity : siteEntities) {
                long siteId = siteRepository.findByUrl(siteEntity.getUrl()).getId();
                List<PageEntity> pageEntitiesBySideId = pageRepository.findBySiteId(siteId);
                pageEntities.addAll(pageEntitiesBySideId);
            }
        } else {
            long siteId = siteRepository.findByUrl(site).getId();
            valueLogger.info("\nsite: ".toUpperCase() + site);
            pageEntities = pageRepository.findBySiteId(siteId);
            valueLogger.info(("pageEntities.size(): " + pageEntities.size()));
            pageEntities.forEach(pageEntity -> valueLogger.info(pageEntity.getPath()));
        }

        List<IndexDTO> foundByQueryIndexList = new ArrayList<>();
        for (int i = 0; i < filteredLemmasSize; i++) {
            int finalI = i;
            lemmaDTO[i] = filteringSortingLemmas.get(i);
            List<IndexEntity> tempIndexEntityList = new ArrayList<>();

            for (PageEntity pageEntity : pageEntities) {
                List<IndexEntity> list = indexRepository.findByPageId(pageEntity.getId());
                tempIndexEntityList.addAll(list);
            }
            if (i > 0) {
                List<IndexEntity> finalNewIndexEntityList = newIndexEntityList;
                tempIndexEntityList = tempIndexEntityList.stream()
                        .filter(indexEntity -> finalNewIndexEntityList.stream()
                                .anyMatch(foundIndexDTO -> Objects.equals(foundIndexDTO.getPage().getId()
                                        , indexEntity.getPage().getId())))
                        .toList();
            }
            valueLogger.info("tempIndexEntityList.size(): " + tempIndexEntityList.size());
            newIndexEntityList = tempIndexEntityList.stream()
                    .filter(indexEntity -> indexEntity.getLemma().getId() == lemmaDTO[finalI].getId())
                    .toList();
            valueLogger.info("newIndexEntityList.size(): " + newIndexEntityList.size());
            if (newIndexEntityList.size() == 0) {
                return foundByQueryIndexList;
            }
            for (IndexEntity indexEntity : newIndexEntityList) {
                IndexDTO indexDTO = new IndexDTO();
                indexDTO.setId(indexEntity.getId());
                indexDTO.setPageId(indexEntity.getPage().getId());
                indexDTO.setLemmaId(indexEntity.getLemma().getId());
                indexDTO.setRank(indexEntity.getRank());
                foundByQueryIndexList.add(indexDTO);
            }
//                valueLogger.info("foundByQueryIndexList.size(): ".toUpperCase() + foundByQueryIndexList.size());
        }
        return foundByQueryIndexList;
    }

    private List<RelevanceDTO> getRelevanceList(List<IndexDTO> foundIndexDTOList) {

        List<Long> uniqFoundPageIdList = foundIndexDTOList.stream()
                .map(IndexDTO::getPageId)
                .distinct()
                .toList();
        valueLogger.info("uniqFoundPageIdList.size(): " + uniqFoundPageIdList.size());
        valueLogger.info("uniqFoundPageIdList: " + uniqFoundPageIdList);

        List<Long> uniqFoundLemmaIdList = foundIndexDTOList.stream()
                .map(IndexDTO::getLemmaId)
                .distinct()
                .toList();
        valueLogger.info("uniqFoundLemmaIdList.size(): " + uniqFoundLemmaIdList.size());
        valueLogger.info("uniqFoundLemmaIdList: " + uniqFoundLemmaIdList);

        List<RelevanceDTO> relevanceList = new ArrayList<>();
        for (Long uFPL : uniqFoundPageIdList) {
            RelevanceDTO relevanceDTO = new RelevanceDTO();
            List<LemmaRankDTO> lemmaRankList = new ArrayList<>();
            for (IndexDTO indexDTO : foundIndexDTOList) {
                if (uFPL == indexDTO.getPageId()) {
                    lemmaRankList.addAll(getLemmaRankList(indexDTO, uniqFoundLemmaIdList));
                }
            }
            relevanceDTO.setPageId(uFPL);
            relevanceDTO.setLemmasRank(lemmaRankList);
            float absRelevance = 0;
            for (LemmaRankDTO lRL : lemmaRankList) {
                absRelevance = absRelevance + lRL.getRank();
            }
            relevanceDTO.setAbsRelevance(absRelevance);
            relevanceList.add(relevanceDTO);
        }
        float maxAbsRank = relevanceList.stream()
                .map(RelevanceDTO::getAbsRelevance)
                .max(Comparator.naturalOrder())
                .orElse((float) -1);

        for (RelevanceDTO relevanceDTO : relevanceList) {
            relevanceDTO.setRelRelevance((relevanceDTO.getAbsRelevance() / maxAbsRank));
        }
        return relevanceList;
    }

    private List<LemmaRankDTO> getLemmaRankList(IndexDTO indexDTO, List<Long> uniqFoundLemmaList) {

        List<LemmaRankDTO> lemmaRankDTOList = new ArrayList<>();
        for (Long uFLL : uniqFoundLemmaList) {
            LemmaRankDTO lemmaRankDTO = new LemmaRankDTO();
            if (uFLL == indexDTO.getLemmaId() && indexDTO.getRank() != 0) {
                lemmaRankDTO.setLemmaId(indexDTO.getLemmaId());
                lemmaRankDTO.setRank(indexDTO.getRank());
                lemmaRankDTOList.add(lemmaRankDTO);
            }
        }
        return lemmaRankDTOList;
    }

    public void setIsSearch(boolean flag) {
        isSearch.set(flag);
    }
}
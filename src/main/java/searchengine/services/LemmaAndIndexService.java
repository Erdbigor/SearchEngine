package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingDTO;
import searchengine.errorHandling.IndexingErrorEvent;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@EnableAsync
public class LemmaAndIndexService {

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final WordsService wordsService;
    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final ApplicationEventPublisher eventPublisher;

    public void switchAllOrPageManager(boolean isPageIndexing, String indexedPageUrl) {
        if (SiteAndPageService.isInterrupted().get()) return;
        if (!isPageIndexing) {
            pageRepository.findAll().stream()
                    .filter(pageEntity -> !SiteAndPageService.isInterrupted().get())
                    .forEach(this::updateLemmaAndIndexManager);
        } else {
            PageEntity pageEntity;
            try {
                URL parsedUrl = new URL(indexedPageUrl);
                String path = parsedUrl.getPath();
                pageEntity = pageRepository.findByPath(path);
                updateLemmaAndIndexManager(pageEntity);
            } catch (MalformedURLException e) {
                valueLogger.error("ERROR IN indexingAllOrPageManager: " + e.getClass());
            } catch (NullPointerException ne) {
                IndexingDTO indexingErrorDTO = new IndexingDTO(
                        false, "Такой страницы не существует!");
                eventPublisher.publishEvent(new IndexingErrorEvent(this, indexingErrorDTO));
            }
        }
    }

    private void updateLemmaAndIndexManager(PageEntity pageEntity) {

        valueLogger.info("Индексируется: " + pageEntity.getPath());

        List<String> listWords = wordsService.getWords(// получаем слова
                pageEntity, null, null, null, null);
        List<String> wordBaseForms = getWordBaseForms(listWords);// получаем словоформы

        wordBaseForms.stream()

                .filter(lemma -> !SiteAndPageService.isInterrupted().get())
                .forEach(lemma -> {
                    updateLemma(lemma, pageEntity);
                });

        lemmaRepository.findAll().parallelStream() // Параллельные потоки
                .filter(lemmaEntity -> !SiteAndPageService.isInterrupted().get())
                .forEach(lemmaEntity -> updateIndex(pageEntity, lemmaEntity));
    }

    private List<String> getWordBaseForms(List<String> listWords) {
        List<String> wordBaseForms;
        List<String> result = new ArrayList<>();
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            for (String word : listWords) {
                if (luceneMorph.checkString(word)) {
                    List<String> wordsInfo = luceneMorph.getMorphInfo(word);
                    String subString = wordsInfo.get(0);
                    if (subString.matches("^.\\|.*")
                            || subString.matches(".*\\|(p|n|o|l).*")) {
                    } else {
                        wordBaseForms = luceneMorph.getNormalForms(word);
                        result.addAll(wordBaseForms);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private void updateLemma(String lemma, PageEntity pageEntity) {
//        valueLogger.info("Start updateLemma for: " + lemma + " - " + pageEntity.getPath());
        if (SiteAndPageService.isInterrupted().get()) return;
        LemmaEntity lemmaEntity;
        if (lemmaRepository.findByLemma(lemma) != null) {
            lemmaEntity = lemmaRepository.getByLemma(lemma);
            if (indexRepository.getByLemma_IdAndPage_Id(lemmaEntity.getId(), pageEntity.getId()) == null) {
                int frequency = lemmaEntity.getFrequency();
                lemmaEntity.setFrequency(frequency + 1);
            }
        } else {
            lemmaEntity = new LemmaEntity();
            lemmaEntity.setFrequency(1);
            lemmaEntity.setLemma(lemma);
            lemmaEntity.setSite(pageEntity.getSite());
        }
        lemmaRepository.save(lemmaEntity);
//        valueLogger.info("End updateLemma for: "  + lemma +  " - " + pageEntity.getPath());
    }

    private void updateIndex(PageEntity pageEntity, LemmaEntity lemmaEntity) {
        if (SiteAndPageService.isInterrupted().get()) return;
        IndexEntity indexEntity;
        if (indexRepository.getByLemma_IdAndPage_Id(lemmaEntity.getId(), pageEntity.getId()) != null) {
            indexEntity = indexRepository.getByLemma_IdAndPage_Id(lemmaEntity.getId(), pageEntity.getId());
            float rank = indexEntity.getRank();
            indexEntity.setRank(rank + lemmaEntity.getFrequency());
        } else {
            indexEntity = new IndexEntity();
            indexEntity.setLemma(lemmaEntity);
            indexEntity.setPage(pageEntity);
            indexEntity.setRank(lemmaEntity.getFrequency());
        }
        indexRepository.save(indexEntity);
    }


}

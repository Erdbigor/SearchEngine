package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class LemmaAndIndexService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final WordsService wordsService;

    public void getWordBaseForms(PageEntity pageEntity) {
        List<String> listWords = wordsService.getWords(
                pageEntity, null, null, null, null);

        ExecutorService executorService =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Callable<Void>> tasks = new ArrayList<>();
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            for (String word : listWords) {
                tasks.add(() -> {
                    if (luceneMorph.checkString(word)) {
                        List<String> wordsInfo = luceneMorph.getMorphInfo(word);
                        String subString = wordsInfo.get(0);
                        List<String> wordBaseForms;
                        if (subString.matches("^.\\|.*")
                                || subString.matches(".*\\|(p|n|o|l).*")) {
                        } else {
                            wordBaseForms = luceneMorph.getNormalForms(word);
                            addLemmas(wordBaseForms, pageEntity);
                        }
                    }
                    return null;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            executorService.invokeAll(tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            executorService.shutdown();
        }

    }

    private synchronized void addLemmas(List<String> wordBaseForms, PageEntity pageEntity) {
        LemmaEntity lemmaEntity;
        for (String lemma : wordBaseForms) {
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
            lemmaRepository.flush();
            addIndex(pageEntity, lemmaEntity);
        }
    }

    private synchronized void addIndex(PageEntity pageEntity, LemmaEntity lemmaEntity) {

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
        indexRepository.flush();
    }
}

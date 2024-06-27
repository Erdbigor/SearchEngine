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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LemmaIndexService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public void searchLemmas(PageEntity pageEntity) {
        LuceneMorphology luceneMorph = null;
        try {
            luceneMorph = new RussianLuceneMorphology();
            String tegRegex = "<[^>]*>";
            String spaceRegex = "\\s+";
            String exceptRegex = "([,.;:!'\"=+_#*|&\\-?\"()\\[\\]{}/\\\\])";
            String regex = "\\s";

            String sentence = pageEntity.getContent();
            sentence = sentence.replaceAll(tegRegex, "");
            sentence = sentence.replaceAll(exceptRegex, " ");
            sentence = sentence.replaceAll(spaceRegex, " ");
            String[] words = sentence.toLowerCase().split(regex);
            List<String> listWords = new ArrayList<>();
            for (String word : words) {
                if (!word.trim().isEmpty()) {
                    listWords.add(word);
                }
            }
            for (String listWord : listWords) {
                if (luceneMorph.checkString(listWord)) {
                    List<String> wordsInfo = luceneMorph.getMorphInfo(listWord);
                    if (wordsInfo.get(0).contains("|l")
                            || wordsInfo.get(0).contains("|p")
                            || wordsInfo.get(0).contains("|n")
                            || wordsInfo.get(0).contains("|o")) {
                    } else {
                        List<String> wordBaseForms = luceneMorph.getNormalForms(listWord);
                        addLemmas(wordBaseForms, pageEntity);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addLemmas(List<String> wordBaseForms, PageEntity pageEntity) {
        LemmaEntity lemmaEntity;
        if (lemmaRepository.findByLemma(wordBaseForms.get(0)) != null) {
            lemmaEntity = lemmaRepository.getByLemma(wordBaseForms.get(0));
            int frequency = lemmaEntity.getFrequency();
            lemmaEntity.setFrequency(frequency + 1);
        } else {
            lemmaEntity = new LemmaEntity();
            lemmaEntity.setFrequency(1);
            lemmaEntity.setLemma(wordBaseForms.get(0));
            lemmaEntity.setSite(pageEntity.getSite());
        }
        lemmaRepository.save(lemmaEntity);
        lemmaRepository.flush();
        addIndex(pageEntity, lemmaEntity);
    }

    public void addIndex(PageEntity pageEntity, LemmaEntity lemmaEntity) {

        IndexEntity indexEntity;
        if (indexRepository.findByLemma_IdAndPage_Id(pageEntity.getId(), lemmaEntity.getId())!=null) {
            indexEntity = indexRepository.getByLemma_IdAndPage_Id(pageEntity.getId(), lemmaEntity.getId());
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

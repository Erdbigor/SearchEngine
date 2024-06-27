package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LemmaService {

    private final LemmaRepository lemmaRepository;

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
    }
}

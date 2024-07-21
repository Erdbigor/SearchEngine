package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.model.PageEntity;

import java.util.ArrayList;
import java.util.List;

@Service
public class WordsService {

    public List<String> getWords(PageEntity pageEntity, String query, String site, Integer offset, Integer limit) {

        String tegRegex = "<[^>]*>";
        String spaceRegex = "\\s+";
        String exceptRegex = "([,.;:!'\"=+_#*|&\\-?\"()\\[\\]{}/\\\\])";
        String regex = "\\s";
        String sentence;
        if (pageEntity != null) {
            sentence = pageEntity.getContent();
        } else sentence = query;
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
        return listWords;
    }
}

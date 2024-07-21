package searchengine.services;

import lombok.RequiredArgsConstructor;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.dto.DataDTO;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SnippedService {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    SnowballStemmer stemmer = new SnowballStemmer(SnowballStemmer.ALGORITHM.RUSSIAN);


    public List<DataDTO> resultDataDTOs(List<Long> uniqFoundPageIdList,
                                        List<Long> uniqFoundLemmaIdList) {
        List<String> snippetsWithWordsSelected;
        List<DataDTO> resultDataDTOList = new ArrayList<>();
        int maxLength = 50;

        List<String> stems = getStems(uniqFoundLemmaIdList);
        valueLogger.info("stems: " + stems);

        for (Long pageId : uniqFoundPageIdList) {
            PageEntity pageEntity = pageRepository.getById(pageId);

            String contentPage = pageEntity.getContent();
            Document doc = Jsoup.parse(contentPage);

            String content = getCleanedContent(doc).toString();

            List<String> snippets = findSnippets(content, stems, maxLength);
            valueLogger.info("\nПОИСК СНИППЕТОВ НА СТРАНИЦЕ iD: " + pageId + " ЗАВЕРШЕН.");
            snippetsWithWordsSelected = wordsSelectionInSnippets(snippets, stems);
            valueLogger.info("\nВЫДЕЛЕНИЕ СЛОВ В СНИППЕТАХ НА СТРАНИЦЕ iD: '" + pageId + "' ЗАВЕРШЕНО.");
            List<DataDTO> dataDTOList = new ArrayList<>();
            for (String snippetWithWordsSelected : snippetsWithWordsSelected) {
                DataDTO dataDTO = new DataDTO();
                dataDTO.setSite(pageEntity.getSite().getUrl());
                dataDTO.setSiteName(pageEntity.getSite().getName());
                dataDTO.setUri(pageEntity.getPath());
                dataDTO.setTitle(doc.title());
//            dataDTO.setRelevance();
                dataDTO.setSnippet(snippetWithWordsSelected);
                dataDTOList.add(dataDTO);
            }

            resultDataDTOList.addAll(dataDTOList);
        }
        return resultDataDTOList;
    }

    private List<String> wordsSelectionInSnippets(List<String> snippets, List<String> stems) {
        List<String> snippetsWithWordSelection = new ArrayList<>();
        stems.forEach(valueLogger::info);
        for (String snippet : snippets) {
            for (String stem : stems) {
                String regex = stem + "[а-я]*";
                Pattern pattern = Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(snippet);
                snippet = matcher.replaceAll("<b>$0</b>").trim();
            }
            snippetsWithWordSelection.add(snippet);
        }
        return snippetsWithWordSelection;
    }

    private List<String> findSnippets(String content, List<String> stems, int maxLength) {

        String regexStart = "([^.:!?]*)(";
        String regexMiddle = "[а-я]*|";
        String regexEnd = "[а-я]*)([^.!?].{0," + maxLength + "})\\b";

        List<String> snippets = new ArrayList<>();
        int stemsSize = stems.size();
        StringBuilder regex = new StringBuilder();
        regex.append(regexStart);
        for (int i = 0; i < stemsSize; i++) {
            if (stemsSize != (i + 1)) {
                regex.append(stems.get(i)).append(regexMiddle);
            } else regex.append(stems.get(i)).append(regexEnd);
        }
        valueLogger.info("regex: " + regex);
        Pattern pattern = Pattern.compile(regex.toString(), Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            snippets.add(matcher.group().trim() + "...");
        }
        return snippets;
    }

    private StringBuilder getCleanedContent(Document doc) {

        Elements elements = doc.body().children();
        StringBuilder content = new StringBuilder();
        for (Element element : elements) {
            String cleanedContent = element.wholeText().replaceAll("(?<!\\S)\s+|\s+(?!\\S)", "");
            cleanedContent = cleanedContent.replaceAll("(?<![:.])(?:\n\\s*){2,}", ". ");
            cleanedContent = cleanedContent.replaceAll("\\n\\s*\\n", " ");
            cleanedContent = cleanedContent.replaceAll("\\n", " ");
            if (!cleanedContent.isEmpty()) {
                content.append(cleanedContent);
            }
        }
        return content;
    }

    private List<String> getStems(List<Long> uniqFoundLemmaIdList) {
        List<String> lemmas = new ArrayList<>();
        List<String> stems = new ArrayList<>();
        for (Long lemmaId : uniqFoundLemmaIdList) {
            for (LemmaEntity lemmaEntity : lemmaRepository.findAll()) {
                if (lemmaId.equals(lemmaEntity.getId())) {
                    String lemma = lemmaEntity.getLemma();
                    lemmas.add(lemma);
                    String stem = stemmer.stem(lemma).toString();
                    stems.add(stem);
                }
            }
        }
        return stems;
    }
}


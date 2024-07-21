package searchengine;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class LineRunner implements CommandLineRunner {

    private final IndexRepository indexRepository;


    @Override
    public void run(String... args) throws IOException {

//        startIndexing();
//        search("команды для запуска");
//        checkIndexPageLemmaIds();

    }

    private void startIndexing() throws IOException {
        System.out.println("LineRunner started.");
        URL url = new URL("http://localhost:8080/api/startIndexing");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.getContent();
    }
    private void search(String string) throws IOException {
        System.out.println("LineRunner started.");
        String encodedQuery = URLEncoder.encode(string
                , StandardCharsets.UTF_8);
        URL url = new URL("http://localhost:8080/api/search?query=" + encodedQuery);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.getContent();
    }
    private void checkIndexPageLemmaIds() {
        System.out.println("LineRunner started.");
        System.out.println("checkIndexPageLemmaIds() running");
        PageEntity pageEntity = new PageEntity();
        pageEntity.setId(14111L);
        LemmaEntity lemmaEntity = new LemmaEntity();
        lemmaEntity.setId(125107L);

        if (indexRepository.getByLemma_IdAndPage_Id(lemmaEntity.getId(), pageEntity.getId()) != null) {
            System.out.println("Есть совпадение");
        } else System.out.println("Нет совпадения");
    }
}

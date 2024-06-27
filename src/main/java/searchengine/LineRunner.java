package searchengine;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Configuration
@RequiredArgsConstructor
public class LineRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws IOException {

        URL url = new URL("http://localhost:8080/api/startIndexing");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.getContent();

    }
}

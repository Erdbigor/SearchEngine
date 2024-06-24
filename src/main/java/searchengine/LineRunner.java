package searchengine;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import searchengine.controllers.ApiController;

import java.net.HttpURLConnection;
import java.net.URL;

@Configuration
public class LineRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
//        System.out.println("ЕСЛИ ИДЁТ ПРЕРЫВАНИЕ ИЛИ ОШИБКА В ТАБЛИЦЕ 'page' НЕ ДОЛЖНЫ СОЗДАВАТЬСЯ ЗАПИСИ");
//        System.exit(0);
        URL url = new URL("http://localhost:8080/api/startIndexing");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.getContent();
    }
}

package searchengine.mappers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import searchengine.dto.search.DataDTO;
import searchengine.dto.search.SearchDTO;

import java.util.ArrayList;
import java.util.List;

@Component
public class SearchMapper {

    private static final Logger valueLogger = LoggerFactory.getLogger("value-logger");

    public SearchDTO map(boolean isNotEmpty, int offset, int limit, boolean isIndexing, List<DataDTO> resultDataDTOList) {
        if (isIndexing) {
            return new SearchDTO(false, 0, "Поиск не возможен, идёт сканирование.", new ArrayList<>());
        }
        if (isNotEmpty) {
            String notFound = "По запросу ничего не найдено";
            try {
                int count = resultDataDTOList.size();
                if (count != 0) {
                    limit = Math.min(limit, count);
                    offset = offset >= limit ? 0 : offset;
                    List<DataDTO> dataDTOList = new ArrayList<>(resultDataDTOList.subList(offset, limit));
                    return new SearchDTO(true, count, "", dataDTOList);
                } else {
                    return new SearchDTO(false, 0, notFound, new ArrayList<>());
                }
            } catch (NullPointerException npe) {
                return new SearchDTO(false, 0, notFound, new ArrayList<>());
            }
        } else {
            return new SearchDTO(false, 0, "Пустой поисковый запрос", new ArrayList<>());
        }
    }
}

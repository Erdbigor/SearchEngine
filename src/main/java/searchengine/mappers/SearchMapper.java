package searchengine.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import searchengine.dto.DataDTO;
import searchengine.dto.SearchDTO;
import searchengine.services.SearchService;

@Component
@RequiredArgsConstructor
public class SearchMapper {

    private final SearchDTO searchDTO = new SearchDTO();
    private final SearchService searchService;

    public SearchDTO map(boolean isNotEmpty) {
        if (isNotEmpty) {
            try {
                searchDTO.setResult(true);
                searchDTO.setCount(searchService.resultDataDTOList.size());
                searchDTO.setData(searchService.resultDataDTOList);
            } catch (NullPointerException nullPointerException) {
                searchDTO.setResult(false);
                searchDTO.setError("По запросу ничего не найдено");
            }

        } else {
            searchDTO.setResult(false);
            searchDTO.setError("Пустой поисковый запрос");
        }
        return searchDTO;
    }
}

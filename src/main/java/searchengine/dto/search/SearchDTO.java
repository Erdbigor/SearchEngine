package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import searchengine.dto.search.DataDTO;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchDTO {

    boolean result;
    int count;
    String error;
    List<DataDTO> data;

}

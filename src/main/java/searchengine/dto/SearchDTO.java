package searchengine.dto;

import lombok.Data;

import java.util.List;

@Data
public class SearchDTO {

    boolean result;
    int count;
    String error;
    List<DataDTO> data;

}

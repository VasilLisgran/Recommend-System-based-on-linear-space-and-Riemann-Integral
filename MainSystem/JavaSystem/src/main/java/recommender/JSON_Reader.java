package recommender;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;
import java.util.*;

public class JSON_Reader {

    public final List<Map<String, String>> data = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public void addVideo(String title, String category){
        Map<String, String> item = new HashMap<>();
        item.put("title", title);
        item.put("category", category);
        data.add(item);
    }

    public void saveToJson(String filePath) throws Exception{
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(Paths.get(filePath).toFile(), data);
        System.out.println("Saved!");
    }
}
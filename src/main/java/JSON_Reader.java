import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

/**
 * PATCHED for the J.UCS study.
 *
 * The previous version serialized only {title, category}, discarding the like
 * date and duration that YouTubeDataLoader had already retrieved. Without those
 * fields a chronological split, a lambda sweep, and any evaluation of the
 * temporal-decay model itself are impossible.
 *
 * Field contract of the emitted JSON (one object per liked video):
 *   video_id         stable YouTube ID; the dataset's primary key
 *   title            raw title as returned by the API
 *   category_id      numeric YouTube category ID (ground truth, unmapped)
 *   category         human-readable name from CategoryRegistry
 *   liked_at         ISO-8601 date the video was added to the "LL" playlist
 *   duration_seconds parsed from contentDetails.duration
 *   weight           value produced by the active WeightScheme
 *
 * category_id is kept alongside category so the label can be re-derived if the
 * registry changes, and so reviewers can verify the ID -> name mapping.
 */
public class JSON_Reader {

    public final List<Map<String, Object>> data = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Deprecated: kept only so old call sites still compile. */
    @Deprecated
    public void addVideo(String title, String category) {
        addVideo(null, title, null, category, null, -1, -1);
    }

    public void addVideo(String videoId, String title,
                         String categoryId, String categoryName,
                         LocalDate likedAt, int durationSeconds, int weight) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("video_id", videoId);
        item.put("title", title);
        item.put("category_id", categoryId);
        item.put("category", categoryName);
        item.put("liked_at", likedAt == null ? null : likedAt.toString());
        item.put("duration_seconds", durationSeconds);
        item.put("weight", weight);
        data.add(item);
    }

    public void saveToJson(String filePath) throws Exception {
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(Paths.get(filePath).toFile(), data);
        System.out.println("Saved " + data.size() + " items to " + filePath);
    }
}
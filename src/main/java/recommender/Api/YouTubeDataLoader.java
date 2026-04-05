package recommender.Api;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import recommender.ContentLoader.DataLoader;
import recommender.Model.Event;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Loader Data from YouTube API
 * Converts API answers to Event objects
 */
public class YouTubeDataLoader {

    private final YouTube youtube;

    // Используем HashMap для Java 8
    private static final Map<String, String> CATEGORY_ID_TO_NAME = new HashMap<>();

    static {
        CATEGORY_ID_TO_NAME.put("1", "Film & Animation");
        CATEGORY_ID_TO_NAME.put("2", "Autos & Vehicles");
        CATEGORY_ID_TO_NAME.put("10", "Music");
        CATEGORY_ID_TO_NAME.put("15", "Pets & Animals");
        CATEGORY_ID_TO_NAME.put("17", "Sports");
        CATEGORY_ID_TO_NAME.put("18", "Short Movies");
        CATEGORY_ID_TO_NAME.put("19", "Travel & Events");
        CATEGORY_ID_TO_NAME.put("20", "Gaming");
        CATEGORY_ID_TO_NAME.put("21", "Videoblogging");
        CATEGORY_ID_TO_NAME.put("22", "People & Blogs");
        CATEGORY_ID_TO_NAME.put("23", "Comedy");
        CATEGORY_ID_TO_NAME.put("24", "Entertainment");
        CATEGORY_ID_TO_NAME.put("25", "News & Politics");
        CATEGORY_ID_TO_NAME.put("26", "Howto & Style");
        CATEGORY_ID_TO_NAME.put("27", "Education");
        CATEGORY_ID_TO_NAME.put("28", "Science & Technology");
        CATEGORY_ID_TO_NAME.put("29", "Nonprofits & Activism");
    }

    public YouTubeDataLoader(YouTube youtube) {
        this.youtube = youtube;
    }

    /**
     * Getting the history of views from YouTube
     * @param maxEvents is max count of events (0 = all)
     * @return list of Event for user
     */
    // Добавьте этот метод в класс YouTubeDataLoader
    public List<Event> fetchLikedVideos(int maxEvents) throws IOException {
        List<Event> events = new ArrayList<>();
        String pageToken = null;

        System.out.println("Loading liked videos from YouTube...");
        System.out.println("=====================================");

        do {
            YouTube.PlaylistItems.List request = youtube.playlistItems()
                    .list(Arrays.asList("snippet", "contentDetails"));

            request.setPlaylistId("LL");  // LL = Liked Videos
            request.setMaxResults(50L);
            request.setPageToken(pageToken);

            PlaylistItemListResponse response = request.execute();

            System.out.println("📄 Page loaded: " + response.getItems().size() + " items");

            for (PlaylistItem item : response.getItems()) {
                String videoId = item.getContentDetails().getVideoId();
                String title = item.getSnippet().getTitle();
                String publishedAt = item.getSnippet().getPublishedAt().toString();

                System.out.println("\n📺 Liked video: " + title);
                System.out.println("   Date liked: " + publishedAt);

                // Получаем детали видео
                Video video = getVideoDetails(videoId);
                if (video != null) {
                    String categoryId = video.getSnippet().getCategoryId();
                    String categoryName = CATEGORY_ID_TO_NAME.get(categoryId);
                    int watchTime = (int) parseDuration(video.getContentDetails().getDuration());

                    System.out.println("   Category ID: " + categoryId);
                    System.out.println("   Category name: " + categoryName);
                    System.out.println("   Video duration: " + watchTime + " min");

                    // Конвертируем в индекс категории
                    int categoryIndex = getCategoryIndex(categoryId);
                    if (categoryIndex != -1 && watchTime > 0) {
                        LocalDate date = LocalDate.parse(publishedAt, DateTimeFormatter.ISO_DATE_TIME);
                        Event event = new Event(date, categoryIndex, watchTime);
                        events.add(event);
                        System.out.println("   ✅ Added to history! Index: " + categoryIndex);
                    } else {
                        System.out.println("   ⚠️ Skipped - category not in our list");
                    }
                }

                if (maxEvents > 0 && events.size() >= maxEvents) {
                    break;
                }
            }

            pageToken = response.getNextPageToken();

        } while (pageToken != null && (maxEvents == 0 || events.size() < maxEvents));

        System.out.println("\n=====================================");
        System.out.println("✅ Total loaded: " + events.size() + " liked videos");
        return events;
    }

    /**
     * Converting activity to Event
     */
    private Event convertToEvent(PlaylistItem item) throws IOException {
        String videoId = item.getContentDetails().getVideoId();
        String dateStr = item.getSnippet().getPublishedAt().toString();
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);

        Video video = getVideoDetails(videoId);
        if (video == null) return null;

        String youtubeCategoryId = video.getSnippet().getCategoryId();
        int categoryIndex;
        try {
            categoryIndex = Integer.parseInt(youtubeCategoryId);
        } catch (NumberFormatException e) {
            return null;
        }

        int watchTime = (int) parseDuration(video.getContentDetails().getDuration());
        return new Event(date, categoryIndex, watchTime);
    }

    /**
     * Getting information about video by its ID
     */
    private Video getVideoDetails(String videoId) throws IOException {
        // ✅ Исправлено: Arrays.asList с раздельными строками
        VideoListResponse response = youtube.videos()
                .list(Arrays.asList("snippet", "contentDetails"))
                .setId(Collections.singletonList(videoId))
                .execute();

        if (response.getItems() != null && !response.getItems().isEmpty()) {
            // ✅ Исправлено: get(0) вместо getFirst()
            return response.getItems().get(0);
        }
        return null;
    }

    /**
     * Convert YouTube category ID to basis index
     * Index = position of category in categories.txt
     */
    private int getCategoryIndex(String youtubeCategoryId) {
        String categoryName = CATEGORY_ID_TO_NAME.get(youtubeCategoryId);
        if (categoryName == null) return -1;

        List<String> categories = DataLoader.getCategoryList();
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).equals(categoryName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Parse duration from ISO format to minutes
     */
    private double parseDuration(String duration) {
        if (duration == null) return 5.0;

        duration = duration.replace("PT", "");
        double minutes = 0;

        if (duration.contains("H")) {
            minutes += Double.parseDouble(duration.split("H")[0]) * 60;
            duration = duration.substring(duration.indexOf("H") + 1);
        }
        if (duration.contains("M")) {
            minutes += Double.parseDouble(duration.split("M")[0]);
            duration = duration.substring(duration.indexOf("M") + 1);
        }
        if (duration.contains("S")) {
            minutes += Double.parseDouble(duration.split("S")[0]) / 60.0;
        }

        return Math.max(minutes, 1.0);
    }

    /**
     * Print preview of loaded event
     */
    private void printEventPreview(Event event) {
        List<String> categories = DataLoader.getCategoryList();
        String categoryName = (event.getContentId() < categories.size())
                ? categories.get(event.getContentId())
                : "Unknown";
        System.out.printf("  🎬 %s | %s | %d min%n",
                event.getDate(), categoryName, event.getWatchTime());
    }
}
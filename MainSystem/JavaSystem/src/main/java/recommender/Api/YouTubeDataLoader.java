package recommender.Api;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import recommender.JSON_Reader;
import recommender.Model.CategoryRegistry;
import recommender.Model.Event;
import recommender.Model.MyVector;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Loader Data from YouTube API
 * Converts API answers to Event objects
 */
public class YouTubeDataLoader {
    private final JSON_Reader JSONreader = new JSON_Reader();
    private final YouTube youtube;
    public final CategoryRegistry categoryRegistry;

    public YouTubeDataLoader(YouTube youtube, CategoryRegistry categoryRegistry) {
        this.youtube = youtube;
        this.categoryRegistry = categoryRegistry;
    }

    /**
     * Getting the history of views from YouTube
     * @param maxEvents is max count of events (0 = all)
     * @return list of Event for user
     */

    public List<Event> fetchLikedVideos(int maxEvents) throws Exception {
        List<Event> events = new ArrayList<>();
        String pageToken = null;

        LocalDate today = LocalDate.now();
        LocalDate maxAgeDate = today.minusDays(360);

        System.out.println("Loading liked videos from YouTube...");
        System.out.println("=====================================");

        do {
            YouTube.PlaylistItems.List request = youtube.playlistItems()
                    .list(Arrays.asList("snippet", "contentDetails"));

            request.setPlaylistId("LL");
            request.setMaxResults(50L);
            request.setPageToken(pageToken);

            PlaylistItemListResponse response = request.execute();

            System.out.println("📄 Page loaded: " + response.getItems().size() + " items");

            for (PlaylistItem item : response.getItems()) {
                String videoId = item.getContentDetails().getVideoId();
                String title = item.getSnippet().getTitle();
                String likedData = item.getSnippet().getPublishedAt().toString();
                LocalDate likedDate = LocalDate.parse(likedData, DateTimeFormatter.ISO_DATE_TIME);

                if (likedDate.isBefore(maxAgeDate)) {
                    System.out.println("\n⏭️ Skipped (older than 360 days): " + title);
                    System.out.println("   Date liked: " + likedDate);
                    continue;
                }

                System.out.println("\n✅ Liked video (within 360 days): " + title);
                System.out.println("   Date liked: " + likedDate);

                Video video = getVideoDetails(videoId);
                if (video != null) {
                    String categoryId = video.getSnippet().getCategoryId();
                    String categoryName = categoryRegistry.getCategoryName(categoryId);

                    JSONreader.addVideo(title, categoryName);

                    int watchTime = (int) parseDuration(video.getContentDetails().getDuration());

                    System.out.println("   Category ID: " + categoryId);
                    System.out.println("   Category name: " + categoryName);
                    System.out.println("   Video duration: " + watchTime + " min");

                    if (categoryName != null && watchTime > 0) {
                        Event event = new Event(likedDate, categoryId, watchTime);
                        events.add(event);
                        System.out.println("   ✅ Added to history!");
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
        System.out.println("✅ Total loaded (last 180 days): " + events.size() + " liked videos");
        JSONreader.saveToJson("../data/user_videos.json");
        return events;
    }

    /**
     * Getting information about video by its ID
     */
    private Video getVideoDetails(String videoId) throws IOException {
        VideoListResponse response = youtube.videos()
                .list(Arrays.asList("snippet", "contentDetails"))
                .setId(Collections.singletonList(videoId))
                .execute();

        if (response.getItems() != null && !response.getItems().isEmpty()) {
            return response.getItems().get(0);
        }
        return null;
    }


    /**
     * Parse duration from ISO format to minutes
     */
    private double parseDuration(String duration) {
        if (duration == null) return 5.0;

        duration = duration.replace("PT", "");
        double minutes = 0;

        if (duration.contains("D")) {
            String[] parts = duration.split("D");
            minutes += Double.parseDouble(parts[0]) * 24 * 60;
            duration = parts.length > 1 ? parts[1] : "";
        }

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

    public void recommendVideo(Map<String, Map<String, List<String>>> clusters,
                               List<Map.Entry<String, Double>> top) throws IOException {

        if (clusters == null || top.isEmpty()) return;

        System.out.println("\n🎯 Recommendations");
        System.out.println("==============");

        for (var entry : top) {
            String category = entry.getKey();
            double cosine = entry.getValue();
            if (cosine < 0.02) continue;

            int totalToShow = Math.max(3, (int) (cosine * 20));
            Map<String, List<String>> catClusters = clusters.get(category);

            if (catClusters == null || catClusters.isEmpty()) continue;

            for (var cluster : catClusters.entrySet()) {
                String query = String.join(" ", cluster.getValue());
                int toShow = totalToShow / catClusters.size();
                if (toShow < 1) toShow = 3;
                searchOnYouTube(query, toShow, category);
            }
        }
    }

    public void searchOnYouTube(String query, int maxResults, String category) throws IOException {
        YouTube.Search.List request = youtube.search().list(List.of("snippet"));
        request.setQ(query);
        request.setType(List.of("video"));
        request.setMaxResults((long) Math.min(maxResults, 50));
        request.setOrder(category.equals("Gaming") ? "date" : "relevance");

        SearchListResponse response = request.execute();

        int count = 0;
        for (SearchResult result : response.getItems()) {
            System.out.println("   " + (++count) + ". " + result.getSnippet().getTitle());
            System.out.println("      https://youtube.com/watch?v=" + result.getId().getVideoId());
        }
        if (count == 0) System.out.println("   ⚠️ Nothing found");
    }
}
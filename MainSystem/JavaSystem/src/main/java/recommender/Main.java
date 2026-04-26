package recommender;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.YouTube;
import recommender.Api.YouTubeAuth;
import recommender.Api.YouTubeDataLoader;
import recommender.Model.CategoryRegistry;
import recommender.Model.Event;
import recommender.Model.User;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

public class Main {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        try {
            System.out.println("🎬 YouTube Recommendation System");
            System.out.println("================================");
            System.out.println("📌 Using Liked Videos as interest signals\n");

            // 1. Load categories & Authorize YouTube
            System.out.println("🔐 Authorizing YouTube...");
            YouTube youtube = YouTubeAuth.authenticate();
            var categoryRegistry = new CategoryRegistry();
            var dataLoader = new YouTubeDataLoader(youtube, categoryRegistry);
            System.out.println("📂 Loading categories...");
            System.out.println("✅ Loaded " + dataLoader.categoryRegistry.getDimension() + " categories");

            // 2. Authorized
            System.out.println("✅ Authorization successful!\n");

            // 3. Load liked videos
            YouTubeDataLoader ytLoader = new YouTubeDataLoader(youtube, new CategoryRegistry());
            List<Event> likedVideos = ytLoader.fetchLikedVideos(80);  // Loading 80 last liked videos

            if (likedVideos.isEmpty()) {
                System.out.println("\n❌ No liked videos found!");
                System.out.println("   Please like some videos on YouTube first, then try again.");
                return;
            }

            System.out.println("\n####################################################################\n");

            // 4. Launching Python
            System.out.println("\n Launching Python...");
            boolean pythonSuccess = runPythonClustering();
            Map<String, Map<String, List<String>>> clusters = loadClustersFromJson( "../data/clusters_result.json");

            if (clusters != null && !clusters.isEmpty()) {
                System.out.println("✅ Got clusters:");
                for (var category : clusters.entrySet()) {
                    System.out.println("   📁 " + category.getKey());
                    for (var cluster : category.getValue().entrySet()) {
                        System.out.println("      Cluster " + cluster.getKey() + ": " + cluster.getValue());
                    }
                }
            }


            // 5. Create user and add liked videos as history
            User user = new User("YouTube User", ytLoader.categoryRegistry);
            for (Event event : likedVideos) {
                user.addEvent(event);
            }

            System.out.println("\n📊 User profile created with " + user.getHistory().size() + " liked videos");

            // 6. Calculate with decay
            System.out.println("\n📊 Calculating user vector with time decay...");
            user.calculateWithDecayAndDynamics(0.95);

            // 7. Show simple statistics
            user.showVector();

            System.out.println("\n####################################################################\n");

            // 8. Get Top categories
            System.out.println("🎯 TOP CATEGORIES:");
            System.out.println("======================");
            List<Map.Entry<String, Double>> top = user.getTopCategories(10);
            for (var entry : top) {
                System.out.printf("  %s: %.3f%n", entry.getKey(), entry.getValue());
            }

            // 9. Get Recommendations
            ytLoader.recommendVideo(clusters, top);

        } catch (IOException e) {
            System.out.println("\n❌ IO Error: " + e.getMessage());
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            System.out.println("\n❌ Security Error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean runPythonClustering(){
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "../clusters/clusters.py");
            pb.directory(new File("../clusters"));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("   [Python] " + line);
            }

            return process.waitFor() == 0;

        } catch (Exception e) {
            System.out.println("   ⚠️ Python error: " + e.getMessage());
            return false;
        }
    }

    // Method to load clusters from JSON we get after launching python
    public static Map<String, Map<String, List<String>>> loadClustersFromJson(String filePath){
        try {
            Path path = Paths.get(filePath);
            if(!Files.exists(path)) return null;
            return mapper.readValue(path.toFile(), new TypeReference<>() {
            });
        }
        catch (Exception e) {
            return null;
        }
    }
}
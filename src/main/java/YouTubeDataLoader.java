import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * YouTube Data API v3 loader.
 *
 * PATCHED for the J.UCS study. Changes are marked [FIX-n] and explained in
 * the accompanying notes:
 *   [FIX-1] maxResults clamped to 50 (API hard limit for playlistItems.list).
 *   [FIX-2] like date and duration are now serialized, not discarded.
 *   [FIX-3] private/deleted placeholder entries are dropped at the source.
 *   [FIX-4] unknown category IDs and missing video details are counted and
 *           excluded instead of being silently relabelled "People & Blogs".
 *   [FIX-5] robust RFC-3339 date parsing via epoch millis.
 *   [FIX-6] videoId is exported so the dataset is re-fetchable and
 *           deduplicable by a stable key rather than by title string.
 *   [FIX-7] the duration->weight mapping is isolated behind WeightScheme so
 *           the discontinuity at 60 s can be ablated rather than assumed.
 */
public class YouTubeDataLoader {

    /** [FIX-1] YouTube Data API v3 caps playlistItems.list at 50. */
    private static final long API_PAGE_SIZE = 50L;

    /** [FIX-3] Titles YouTube returns for entries the caller cannot read. */
    private static final Set<String> PLACEHOLDER_TITLES =
            Set.of("private video", "deleted video");

    /**
     * [FIX-7] Duration -> weight mappings, kept side by side so the paper can
     * report a sensitivity analysis instead of a single unjustified formula.
     *
     * ORIGINAL is the scheme used in the pilot study. Note it is discontinuous:
     * a 60 s video scores 10, a 61 s video scores 40 + 15*ln(61) = 101.6, a
     * tenfold jump for one extra second. The stated design goal ("so that
     * Shorts and long-form videos contribute comparably") is not achieved by it:
     * Shorts end up 10-16x under-weighted relative to long-form content.
     *
     * CONTINUOUS_LOG removes the jump. UNIFORM ignores duration entirely and
     * is the correct null hypothesis for "does duration weighting help at all?".
     */
    public enum WeightScheme {
        ORIGINAL {
            @Override public double weight(long seconds) {
                return seconds <= 60 ? 10.0 : 40.0 + 15.0 * Math.log(seconds);
            }
        },
        CONTINUOUS_LOG {
            @Override public double weight(long seconds) {
                return 15.0 * Math.log(1.0 + Math.max(seconds, 1));
            }
        },
        UNIFORM {
            @Override public double weight(long seconds) {
                return 1.0;
            }
        };

        public abstract double weight(long seconds);
    }

    private final JSON_Reader jsonReader = new JSON_Reader();
    private final YouTube youtube;
    public final CategoryRegistry categoryRegistry;
    private final WeightScheme weightScheme;

    private final Set<String> watchedTitles = new HashSet<>();
    private final Set<String> seenVideoIds = new HashSet<>();

    // [FIX-4] Data-quality counters. These belong in the paper's dataset table.
    private int skippedPlaceholder = 0;
    private int skippedDuplicate = 0;
    private int skippedMissingDetails = 0;
    private int skippedUnknownCategory = 0;
    private final Map<String, Integer> unknownCategoryIds = new TreeMap<>();

    public YouTubeDataLoader(YouTube youtube, CategoryRegistry categoryRegistry) {
        this(youtube, categoryRegistry, WeightScheme.ORIGINAL);
    }

    public YouTubeDataLoader(YouTube youtube, CategoryRegistry categoryRegistry,
                             WeightScheme weightScheme) {
        this.youtube = youtube;
        this.categoryRegistry = categoryRegistry;
        this.weightScheme = weightScheme;
    }

    public List<Event> fetchLikedVideos(int maxEvents) throws Exception {
        return fetchLikedVideos(maxEvents, "data/user_videos.json");
    }

    public List<Event> fetchLikedVideos(int maxEvents, String outputPath) throws Exception {
        List<Event> events = new ArrayList<>();
        String pageToken = null;

        System.out.println("Loading likes (API), weight scheme = " + weightScheme);
        int totalFetched = 0;

        do {
            YouTube.PlaylistItems.List playlistRequest = youtube.playlistItems()
                    .list(Arrays.asList("snippet", "contentDetails"));
            playlistRequest.setPlaylistId("LL");
            playlistRequest.setMaxResults(API_PAGE_SIZE);   // [FIX-1]
            if (pageToken != null) {
                playlistRequest.setPageToken(pageToken);
            }

            PlaylistItemListResponse playlistResponse = playlistRequest.execute();
            List<PlaylistItem> items = playlistResponse.getItems();
            if (items == null || items.isEmpty()) break;

            List<String> videoIds = items.stream()
                    .map(item -> item.getContentDetails().getVideoId())
                    .collect(Collectors.toList());

            Map<String, VideoDetails> detailsMap = fetchVideoDetailsBatch(videoIds);

            for (PlaylistItem item : items) {
                String videoId = item.getContentDetails().getVideoId();
                String title = item.getSnippet().getTitle();

                // [FIX-3] Placeholder rows carry no title and no usable category.
                if (title == null || title.isBlank()
                        || PLACEHOLDER_TITLES.contains(title.toLowerCase().trim())) {
                    skippedPlaceholder++;
                    continue;
                }

                // [FIX-6] Deduplicate on the stable key, not on the title string.
                if (!seenVideoIds.add(videoId)) {
                    skippedDuplicate++;
                    continue;
                }

                // [FIX-4] No silent "22" / "PT3M0S" substitution: a video whose
                // details did not come back is a missing observation, not a
                // People & Blogs observation of median length.
                VideoDetails details = detailsMap.get(videoId);
                if (details == null) {
                    skippedMissingDetails++;
                    continue;
                }

                String categoryId = details.getCategoryId();
                String categoryName = categoryRegistry.getCategoryName(categoryId);
                if (categoryName == null) {
                    // Categories 30-44 (Movies, Shows, Trailers) and any
                    // region-specific IDs are absent from CategoryRegistry.
                    skippedUnknownCategory++;
                    unknownCategoryIds.merge(categoryId, 1, Integer::sum);
                    continue;
                }

                // [FIX-5] getPublishedAt() is a com.google.api.client.util.DateTime;
                // getValue() is epoch millis and needs no format string.
                // For a playlistItem this is the time the item was ADDED to the
                // playlist, i.e. the like date -- which is what we want.
                LocalDate likedDate = Instant
                        .ofEpochMilli(item.getSnippet().getPublishedAt().getValue())
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate();

                long durationSec = parseISO8601Duration(details.getDurationISO());
                if (durationSec <= 0) {
                    skippedMissingDetails++;
                    continue;
                }

                int weight = (int) Math.round(weightScheme.weight(durationSec));

                events.add(new Event(likedDate, categoryId, weight));
                watchedTitles.add(title.toLowerCase().trim());

                // [FIX-2] Everything the evaluation needs now reaches disk.
                jsonReader.addVideo(videoId, title, categoryId, categoryName,
                        likedDate, (int) durationSec, weight);

                totalFetched++;
                if (maxEvents > 0 && totalFetched >= maxEvents) break;
            }

            if (maxEvents > 0 && totalFetched >= maxEvents) break;
            pageToken = playlistResponse.getNextPageToken();

        } while (pageToken != null);

        jsonReader.saveToJson(outputPath);
        printDataQualityReport(events.size());
        return events;
    }

    /** [FIX-4] These counts belong in the paper's dataset description. */
    private void printDataQualityReport(int kept) {
        System.out.println("--- data quality ---");
        System.out.printf(Locale.US, "  kept                : %d%n", kept);
        System.out.printf(Locale.US, "  private/deleted     : %d%n", skippedPlaceholder);
        System.out.printf(Locale.US, "  duplicate videoId   : %d%n", skippedDuplicate);
        System.out.printf(Locale.US, "  missing details     : %d%n", skippedMissingDetails);
        System.out.printf(Locale.US, "  unknown category    : %d%n", skippedUnknownCategory);
        if (!unknownCategoryIds.isEmpty()) {
            System.out.println("  unknown category IDs: " + unknownCategoryIds);
        }
    }

    private Map<String, VideoDetails> fetchVideoDetailsBatch(List<String> videoIds)
            throws IOException {
        Map<String, VideoDetails> map = new HashMap<>();
        if (videoIds == null || videoIds.isEmpty()) return map;

        YouTube.Videos.List videoRequest = youtube.videos()
                .list(Arrays.asList("snippet", "contentDetails"));
        videoRequest.setId(videoIds);

        VideoListResponse response = videoRequest.execute();
        List<Video> videos = response.getItems();
        if (videos != null) {
            for (Video v : videos) {
                map.put(v.getId(), new VideoDetails(
                        v.getSnippet().getCategoryId(),
                        v.getContentDetails().getDuration()));
            }
        }
        return map;
    }

    /** Returns -1 when the duration cannot be parsed, so the caller can skip it. */
    private long parseISO8601Duration(String isoDuration) {
        if (isoDuration == null || isoDuration.isEmpty()) return -1;
        try {
            return Duration.parse(isoDuration).getSeconds();
        } catch (Exception e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Recommendation generation
    //
    // [FIX-8] search.list is quota-metered: YouTube Data API v3 allows ~100
    //         search queries per project per day (100 units each against a
    //         10,000-unit daily budget). One query per cluster exhausts that
    //         budget on a single account: u4 alone yields 28+21+15 = 64 clusters
    //         across its top three categories. The number of clusters grows with
    //         history size while the budget does not, so the retrieval step has a
    //         hard scalability ceiling. This is reported, budgeted and degraded
    //         gracefully rather than allowed to abort the run.
    // [FIX-9] Degenerate cluster keywords (character-repetition artefacts such as
    //         "skr rrrrrrr") are skipped before a quota unit is spent on them.
    // [FIX-10] Results are written to disk. Previously the system's only output
    //         existed solely as console text and was lost on the next run.
    // ------------------------------------------------------------------

    /** Recommendations actually produced, for serialization by the caller. */
    private final Map<String, List<Map<String, Object>>> recommendations = new LinkedHashMap<>();

    private int searchesUsed = 0;
    private boolean quotaExhausted = false;

    public Map<String, List<Map<String, Object>>> getRecommendations() {
        return recommendations;
    }

    public int getSearchesUsed() {
        return searchesUsed;
    }

    public boolean isQuotaExhausted() {
        return quotaExhausted;
    }

    /**
     * A keyword set is unusable as a search query when it carries no lexical
     * content: tokens that are all very short, or that consist of one character
     * repeated (onomatopoeia, keyboard mashing) which DBSCAN happily groups.
     */
    private static boolean isDegenerateQuery(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return true;
        int informative = 0;
        for (String w : keywords) {
            if (w.length() < 4) continue;
            if (w.chars().distinct().count() <= 2) continue;   // "rrrrrrr", "aaaa"
            informative++;
        }
        return informative == 0;
    }

    /**
     * @param maxSearches hard cap on search.list calls for this run. Pass the
     *                    remaining daily budget; 0 disables retrieval entirely
     *                    (useful when only the profile and clusters are wanted).
     */
    /** Hard ceiling on recommended videos per category, independent of the
     *  search budget, so a very "peaked" profile cannot dump dozens of videos
     *  from a single category into the output. */
    private static final int MAX_VIDEOS_PER_CATEGORY = 8;

    public void generateRecommendations(List<Map.Entry<String, Double>> topCategories,
                                        Map<String, Map<String, List<String>>> clusters,
                                        int maxSearches) {
        System.out.println("\nRecommendations (search budget: " + maxSearches + " queries, "
                + "max " + MAX_VIDEOS_PER_CATEGORY + " videos/category)");
        System.out.println("=========================================");

        // Only categories that actually have clusters compete for the budget,
        // and the budget is split evenly among them (not by raw relevance
        // score) so the top-1 category cannot starve the rest to a single
        // query, which is what a relevance-proportional split did in practice
        // (a 0.85-vs-0.14 score gap turned into 17-vs-1 queries).
        List<Map.Entry<String, Double>> eligible = new ArrayList<>();
        for (var e : topCategories) {
            if (e.getValue() >= 0.05 && clusters.containsKey(e.getKey())
                    && !clusters.get(e.getKey()).isEmpty()) {
                eligible.add(e);
            }
        }
        if (eligible.isEmpty()) {
            System.out.println("(no category cleared the 0.05 relevance threshold)");
            return;
        }

        int perCategoryBudget = Math.max(1, maxSearches / eligible.size());

        for (var entry : eligible) {
            if (searchesUsed >= maxSearches || quotaExhausted) break;

            String category = entry.getKey();
            double score = entry.getValue();
            Map<String, List<String>> catClusters = clusters.get(category);

            int queriesLeft = Math.min(perCategoryBudget, maxSearches - searchesUsed);

            // [FIX-11] perClusterLimit must scale with the queries that will
            // ACTUALLY be issued, not with the raw budget. A category with 20
            // queries allocated but only 1 cluster available issues exactly 1
            // query; sizing the per-query video cap off the budget of 20 (as
            // the previous version did) collapsed the cap to 1 video and left
            // 19 queries unspent. The cap is now sized off
            // min(queriesLeft, clusterCount), i.e. the queries this category
            // will realistically use.
            int queriesToIssue = Math.min(queriesLeft, catClusters.size());
            int perClusterLimit = Math.max(1,
                    (int) Math.ceil(MAX_VIDEOS_PER_CATEGORY / (double) Math.max(1, queriesToIssue)));

            System.out.printf(Locale.US, "%nCategory [%s] (relevance %.4f, "
                            + "%d clusters, querying up to %d, %d videos/query):%n",
                    category, score, catClusters.size(), queriesLeft, perClusterLimit);

            List<Map<String, Object>> forCategory =
                    recommendations.computeIfAbsent(category, k -> new ArrayList<>());

            // Largest clusters first: they represent the most-repeated sub-interest
            // within the category and are the most informative queries to spend
            // a scarce search unit on.
            List<Map.Entry<String, List<String>>> ranked = new ArrayList<>(catClusters.entrySet());
            ranked.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            int issued = 0;
            for (var cluster : ranked) {
                if (issued >= queriesLeft || forCategory.size() >= MAX_VIDEOS_PER_CATEGORY
                        || quotaExhausted) break;

                List<String> keywords = cluster.getValue();
                if (isDegenerateQuery(keywords)) {
                    System.out.printf("  skip (degenerate): \"%s\"%n",
                            String.join(" ", keywords));
                    continue;
                }

                String query = String.join(" ", keywords);
                System.out.printf("  query: \"%s\"%n", query);
                issued++;
                int roomLeft = MAX_VIDEOS_PER_CATEGORY - forCategory.size();
                searchAndFilterOnYouTube(query, Math.min(perClusterLimit, roomLeft),
                        category, cluster.getKey(), forCategory);
            }
        }

        int totalVideos = recommendations.values().stream().mapToInt(List::size).sum();
        System.out.printf(Locale.US, "%nsearch.list calls used: %d/%d, videos returned: %d%s%n",
                searchesUsed, maxSearches, totalVideos,
                quotaExhausted ? "  (DAILY QUOTA EXHAUSTED — results are partial)" : "");
    }

    /**
     * The original version switched search order to "date" for Gaming only.
     * That per-category special case is removed so retrieval is uniform.
     * A 403/429 quota error is recorded and stops further searching instead of
     * propagating and discarding everything already collected.
     */
    private void searchAndFilterOnYouTube(String query, int limit, String category,
                                          String clusterId,
                                          List<Map<String, Object>> sink) {
        List<SearchResult> results;
        try {
            YouTube.Search.List request = youtube.search().list(List.of("snippet"));
            request.setQ(query);
            request.setType(List.of("video"));
            request.setMaxResults(25L);
            request.setOrder("relevance");

            searchesUsed++;
            results = request.execute().getItems();

        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            int code = e.getStatusCode();
            if (code == 429 || code == 403) {
                quotaExhausted = true;
                System.out.printf("   quota exhausted after %d searches; "
                        + "stopping retrieval and keeping partial results%n", searchesUsed);
            } else {
                System.out.printf("   search failed (HTTP %d): %s%n", code, e.getMessage());
            }
            return;
        } catch (IOException e) {
            System.out.println("   search failed: " + e.getMessage());
            return;
        }

        if (results == null || results.isEmpty()) {
            System.out.println("   (no results)");
            return;
        }

        int displayed = 0;
        for (SearchResult r : results) {
            String title = r.getSnippet().getTitle();
            if (watchedTitles.contains(title.toLowerCase().trim())) continue;

            String videoId = r.getId().getVideoId();
            System.out.printf("   %d. %s%n", ++displayed, title);
            System.out.printf("      https://youtube.com/watch?v=%s%n", videoId);

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("category", category);
            rec.put("cluster_id", clusterId);
            rec.put("query", query);
            rec.put("rank", displayed);
            rec.put("video_id", videoId);
            rec.put("title", title);
            sink.add(rec);                                      // [FIX-10]

            if (displayed >= limit) break;
        }

        if (displayed == 0) {
            System.out.println("   (all results already liked)");
        }
    }

    private static class VideoDetails {
        private final String categoryId;
        private final String durationISO;

        VideoDetails(String categoryId, String durationISO) {
            this.categoryId = categoryId;
            this.durationISO = durationISO;
        }
        String getCategoryId() { return categoryId; }
        String getDurationISO() { return durationISO; }
    }
}
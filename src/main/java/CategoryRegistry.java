import java.util.*;

/**
 * YouTube content categories as an orthonormal basis of R^17.
 *
 * PATCHED for the J.UCS study:
 *   [FIX-1] LinkedHashMap instead of HashMap. buildIndexMapping() iterated a
 *           HashMap's keySet, so coordinate indices were assigned in hash order
 *           -- arbitrary, and not guaranteed stable across JVM versions. The
 *           printed "Component N" labels in the pilot logs are therefore not
 *           meaningful identifiers. Indices are now the declaration order below
 *           and are stable across runs and machines.
 *   [FIX-2] getCategoryNameByIndex is an O(1) array lookup instead of a linear
 *           scan over the map on every call.
 *   [FIX-3] Documented that IDs 30-44 (Movies, Shows, Trailers) and any
 *           region-specific IDs are deliberately absent. YouTubeDataLoader now
 *           counts and excludes videos carrying them instead of relabelling
 *           them "People & Blogs", which previously inflated the majority class.
 *
 * Note on the basis: because these vectors are orthonormal, cos(V, e_i) == V_i
 * identically, so ranking categories by cosine similarity is ranking them by
 * normalized coordinate. To make the vector-space formulation do real work,
 * replace buildBasisVectors() with sentence embeddings of the category names
 * and descriptions, which gives a non-orthogonal space in which interest can
 * transfer between related categories (Music/Entertainment, Gaming/Comedy).
 */
public class CategoryRegistry {

    /** Declaration order fixes the coordinate index of every category. */
    private static final String[][] CATEGORIES = {
            {"1",  "Film & Animation"},
            {"2",  "Autos & Vehicles"},
            {"10", "Music"},
            {"15", "Pets & Animals"},
            {"17", "Sports"},
            {"18", "Short Movies"},
            {"19", "Travel & Events"},
            {"20", "Gaming"},
            {"21", "Videoblogging"},
            {"22", "People & Blogs"},
            {"23", "Comedy"},
            {"24", "Entertainment"},
            {"25", "News & Politics"},
            {"26", "Howto & Style"},
            {"27", "Education"},
            {"28", "Science & Technology"},
            {"29", "Nonprofits & Activism"},
    };

    private final Map<String, String> idToName = new LinkedHashMap<>();   // [FIX-1]
    private final Map<String, Integer> idToIndex = new LinkedHashMap<>();
    private final Map<String, MyVector> categoryBasis = new LinkedHashMap<>();
    private final String[] indexToName;                                    // [FIX-2]

    public CategoryRegistry() {
        for (String[] row : CATEGORIES) {
            idToName.put(row[0], row[1]);
        }
        int i = 0;
        indexToName = new String[idToName.size()];
        for (Map.Entry<String, String> e : idToName.entrySet()) {
            idToIndex.put(e.getKey(), i);
            indexToName[i] = e.getValue();
            i++;
        }
        buildBasisVectors();
    }

    private void buildBasisVectors() {
        int dimension = idToName.size();
        for (Map.Entry<String, Integer> entry : idToIndex.entrySet()) {
            List<Double> coords = new ArrayList<>(Collections.nCopies(dimension, 0.0));
            coords.set(entry.getValue(), 1.0);
            categoryBasis.put(idToName.get(entry.getKey()), new MyVector(coords));
        }
    }

    /**
     * [FIX-3] Returns null for IDs outside the table above (30-44 Movies/Shows/
     * Trailers, and region-specific IDs). Callers must treat null as a missing
     * observation, never as a default category.
     */
    public String getCategoryName(String id) {
        return idToName.get(id);
    }

    public Integer getCategoryIndex(String id) {
        return idToIndex.get(id);
    }

    public String getCategoryNameByIndex(int index) {
        return (index >= 0 && index < indexToName.length) ? indexToName[index] : "Unknown";
    }

    public MyVector getBasisVector(String categoryName) {
        return categoryBasis.get(categoryName);
    }

    public Map<String, MyVector> getAllBasisVectors() {
        return Collections.unmodifiableMap(categoryBasis);
    }

    public int getDimension() {
        return idToName.size();
    }

    public boolean isValidCategory(String id) {
        return idToName.containsKey(id);
    }
}
package recommender.ContentLoader;

import recommender.Model.Event;
import recommender.Model.User;
import recommender.Model.Vector;

import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataLoader {
    private static final Map<String, Vector> basis = new HashMap<>(); // Our basis
    private static final List<String> categoryList = new ArrayList<>();

    // Reading the file of categories
    public static void loadBasis(String filePath, int dimension) throws IOException {
        // Пробуем загрузить из resources
        InputStream is = DataLoader.class.getResourceAsStream("/" + filePath);

        if (is == null) {
            // Пробуем из корня проекта
            is = DataLoader.class.getClassLoader().getResourceAsStream(filePath);
        }

        if (is == null) {
            System.err.println("❌ File not found: " + filePath);
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            int index = 0;
            String line;
            while ((line = br.readLine()) != null && index < dimension) {
                line = line.trim();
                if (!line.isEmpty()) {
                    ArrayList<Double> list = new ArrayList<>();
                    for (int i = 0; i < dimension; i++) {
                        if (i == index) list.add(1.0);
                        else list.add(0.0);
                    }
                    Vector vector = new Vector(list);
                    basis.put(line, vector);
                    categoryList.add(line);
                    System.out.println("  Loaded: " + line);
                    index++;
                }
            }
        }

        System.out.println("✅ Loaded " + categoryList.size() + " categories");
    }


    // Reading the history of watching
    public void loadUsersHistory(User user, String filePath){

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))){

            String line;
            // In this Demo we use data with 3 parameters
            while ((line = br.readLine()) != null){
                String[] parts = line.split("\\|");
                LocalDate date = LocalDate.parse(parts[0]);
                int categoryID = Integer.parseInt(parts[1]);
                int watchTime = Integer.parseInt(parts[2]);

                user.addEvent(new Event(date, categoryID, watchTime));
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getCategoryList() { return categoryList; }
    public static Map<String, Vector> getBasis() { return basis; }
}

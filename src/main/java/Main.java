import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Main{
    static void main() throws IOException {
        try {
            DataLoader dt = new DataLoader();

            // 1. Load categories
            DataLoader.loadBasis("src/main/resources/categories.txt", 10);

            Map<String, Vector> basis = DataLoader.getBasis();

            // 2. Show categories
            List<String> categories = new ArrayList<>(basis.keySet());
            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i);
                Vector v = basis.get(category);
                System.out.println(i + ". " + category);
                System.out.println(v.getCoordinates());
            }

            System.out.println("\n####################################################################");

            // 3. Create user
            User user1 = new User("Alice", 10);

            // 4. Load history
            dt.loadUsersHistory(user1, "src/main/resources/Bob");
            user1.loadHistory(user1.getHistory());

            // 5. Show history
            user1.showVector();

            // 6. Calculate
            System.out.println("\nThe full vector:");
            user1.calculateWithDecayAndDynamics(user1.getLambda());
            System.out.println(user1.getVector().getCoordinates());
            System.out.println("\n####################################################################\n");

            user1.getRecommendations(10);
        }
        catch (IOException e) {
            System.out.println("Ошибка при загрузке: " + e.getMessage());
        }

    }
}
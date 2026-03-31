import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Main{
    static void main() throws IOException {
        try {
            DataLoader dt = new DataLoader();

            DataLoader.loadBasis("src/main/resources/categories.txt", 10);

            Map<String, Vector> basis = DataLoader.getBasis();

            // Выводим категории в порядке их индексов
            List<String> categories = new ArrayList<>(basis.keySet());
            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i);
                Vector v = basis.get(category);
                System.out.println(i + ". " + category);
                System.out.println(v.getCoordinates());
            }

            System.out.println("\n####################################################################");

            User user1 = new User("Alice", 10);
            dt.loadUsersHistory(user1, "src/main/resources/Bob");

            user1.loadHistory(user1.getHistory());

            user1.showVector();

            // Выводим полный вектор
            System.out.println("\nПолный вектор:");
            System.out.println(user1.getVector().getCoordinates());

        }
        catch (IOException e) {
            System.out.println("Ошибка при загрузке: " + e.getMessage());
        }

    }
}
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

class Main{
    static void main() throws IOException {
        try {
            DataLoader dt = new DataLoader();

            DataLoader.loadBasis("src/main/resources/categories.txt");

            Map<String, Vector> basis = DataLoader.getBasis();

            for(Map.Entry<String, Vector> entry : basis.entrySet()){
                System.out.print(entry.getKey());
                System.out.println();
                Vector v = entry.getValue();
                System.out.println(v.getCoordinates());
            }

           var history = dt.loadUsersHistory("src/main/resources/Bob");

            for (Event e : history){
                System.out.println(e.getDate() + " " + e.getContentId() + " " + e.getWatchTime());
            }

            User user1 = new User("Alice", history);
            Vector c = user1.CalculateVector(history, 10);

            ArrayList<Double> coordinates = c.getCoordinates();
            for (int i = 0; i < coordinates.size(); i++) {
                double value = coordinates.get(i);
                if (value > 0) { // Выводим только ненулевые значения
                    System.out.printf("  Категория %d: %.1f минут\n", i, value);
                }
            }

            // Выводим полный вектор
            System.out.println("\nПолный вектор:");
            System.out.println(c.getCoordinates());

        }
        catch (IOException e) {
            System.out.println("Ошибка при загрузке: " + e.getMessage());
        }

    }
}
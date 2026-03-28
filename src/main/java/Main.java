import java.io.IOException;
import java.util.Map;

class Main{
    static void main() throws IOException {
        try {
            DataLoader.loadBasis("src/main/resources/categories.txt");

            Map<String, Vector> basis = DataLoader.getBasis();

            for(Map.Entry<String, Vector> entry : basis.entrySet()){
                System.out.print(entry.getKey());
                System.out.println();
                Vector v = entry.getValue();
                System.out.println(v.getCoordinates());
            }
        }
        catch (IOException e) {
            System.out.println("Ошибка при загрузке: " + e.getMessage());
        }

    }
}
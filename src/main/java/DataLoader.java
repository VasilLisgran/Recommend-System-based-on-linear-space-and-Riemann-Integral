import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataLoader {
    private static Map<String, Vector> basis = new HashMap<>();

    public static Map<String, Vector> getBasis() {
        return basis;
    }

    public static void loadBasis(String filePath) throws IOException{

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            int index = 0;
            String line;
            while ((line = br.readLine())!= null){
                ArrayList<Double> list = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    if(i == index) list.add(1.0);
                    else list.add(0.0);
                }
                Vector vector = new Vector(list);

                basis.put(line, vector);

                index++;
            }
        }
        catch (IOException e){
            System.out.println("false");
        }
    }

    public static ArrayList<Event> loadUsersHistory(String filePath){
        ArrayList<Event> history = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))){

            String line;
            while ((line = br.readLine()) != null){
                String[] parts = line.split("\\|");

                LocalDate date = LocalDate.parse(parts[0]);
                int categoryID = Integer.parseInt(parts[1]);
                int watchTime = Integer.parseInt(parts[2]);

                history.add(new Event(date, categoryID, watchTime));
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return history;
    }


}

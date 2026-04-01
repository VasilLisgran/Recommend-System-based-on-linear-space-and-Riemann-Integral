import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataLoader {
    private static final Map<String, Vector> basis = new HashMap<>(); // Our basis
    private static final List<String> categoryList = new ArrayList<>();

    // Reading the file of categories
    public static void loadBasis(String filePath, int dimension) throws IOException{

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) { // Trying to read the file
            int index = 0;
            String line;
            while ((line = br.readLine())!= null){
                ArrayList<Double> list = new ArrayList<>();
                for (int i = 0; i < dimension; i++) {
                    if(i == index) list.add(1.0);   // Creating basis vectors (coordinate with value 1)
                    else list.add(0.0);             // Creating basis vectors (coordinate with value 0)
                }
                Vector vector = new Vector(list);
                basis.put(line, vector);
                categoryList.add(line);
                index++;
            }
        }
        catch (IOException e){
            System.out.println("false");
        }
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

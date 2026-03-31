import java.util.ArrayList;
import java.util.List;

// User class
public class User {
    private String name;                // Name
    private Vector userVector;          // User's vector of content
    private ArrayList<Event> history;   // User's history

    public User(){ }

    public User(String name, int dimension){
        this.name = name;
        this.userVector = Vector.zero(dimension); // Starting from the 0-vector
        this.history = new ArrayList<>();         // The new user has an empty history
    }

    public void addEvent(Event event) {
        history.add(event);
        CalculateVector();
    }

    public Vector CalculateVector(){
        userVector = Vector.zero(userVector.Size());

        for (Event event : history) {
            int index_c = event.getContentId();
            int time = event.getWatchTime();

            double current = userVector.get(index_c);
            userVector.set(index_c, time + current);
        }

        return userVector;
    }

    public void loadHistory(ArrayList<Event> events) {
        this.history = events;

        System.out.println("\n👤 Пользователь: " + name);
        System.out.println("Загружено событий: " + history.size());

        // Показываем что загрузили
        for (Event e : history) {
            System.out.printf("  %s | категория %d | %d мин%n",
                    e.getDate(), e.getContentId(), e.getWatchTime());
        }
    }

    public void showVector() {
        System.out.println("\n📊 Вектор пользователя " + name + ":");
        ArrayList<Double> coords = userVector.getCoordinates();
        List<String> categories = DataLoader.getCategoryList();

        for (int i = 0; i < coords.size(); i++) {
            if (coords.get(i) > 0) {
                System.out.printf("  %s: %.0f мин%n", categories.get(i), coords.get(i));
            }
        }
    }

    public String getName(){ return name; }
    public ArrayList<Event> getHistory() { return history; }
    public Vector getVector() { return userVector; }
}

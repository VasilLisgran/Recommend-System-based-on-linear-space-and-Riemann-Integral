import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

// User class
public class User {
    private String name;                // Name
    private Vector userVector;          // User's vector of content
    private ArrayList<Event> history;   // User's history
    private Map<Integer, LocalDate> lastWatchDate;  // когда последний раз обновляли категорию

    private double lambda = 0.95;        // затухание в день

    // Constructors
    public User(){ }
    public User(String name, int dimension){
        this.name = name;
        this.userVector = Vector.zero(dimension); // Starting from the 0-vector
        this.history = new ArrayList<>();         // The new user has an empty history
    }

    public void addEvent(Event event) {
        history.add(event);
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

    public Vector calculateWithDecayAndDynamics(double lambda) {
        LocalDate today = LocalDate.now();                  // Today
        Vector result = Vector.zero(userVector.Size());     // Result Vector

        // Group watching by categories
        Map<Integer, TreeMap<LocalDate, Integer>> categoryByDay = new HashMap<>();

        for (Event event : history) {
            categoryByDay
                    .computeIfAbsent(event.getContentId(), k -> new TreeMap<>())
                    .merge(event.getDate(), event.getWatchTime(), Integer::sum);
        }

        System.out.println("\n📊 Integral decay");
        System.out.println("   λ = " + lambda);

        for (var entry : categoryByDay.entrySet()) {
            int categoryId = entry.getKey();    // Category ID
            TreeMap<LocalDate, Integer> days = entry.getValue();   // Dates of watching this category

            // Find the first and the last day
            LocalDate firstDay = days.firstKey();

            double dynamic = 0.0;   // Dynamic value
            double total = 0.0;
            String categoryName = DataLoader.getCategoryList().get(categoryId);

            System.out.println("📌 " + categoryName + ":");

            // From the first to the last
            for (LocalDate date = firstDay; !date.isAfter(today); date = date.plusDays(1)) {

                // 1. Decay the dynamic
                total = total * lambda;
                dynamic = dynamic * lambda;

                // 2. Add time if we watched content in this day
                Integer watchTime = days.get(date);
                if (watchTime != null) {
                    dynamic = dynamic + watchTime;

                    // 3. Calculate the contribution
                    long age = ChronoUnit.DAYS.between(date, today);
                    double decay = Math.pow(lambda, age);
                    double contribution = watchTime * decay * dynamic;
                    total += contribution;

                    System.out.printf("  %s: %d min, age %d days, λ^age=%.3f, dynamic=%.2f, contribution=%.2f%n",
                            date, watchTime, age, decay, dynamic, contribution);
                }
                else {
                    // For debugging : decay without view
                    if (dynamic > 0.01) {
                        System.out.printf("  %s: no view, dynamic decays → %.2f%n",
                                date, dynamic);
                    }
                }
            }

            result.set(categoryId, total);
            System.out.printf("  Total for %s: %.2f%n%n", categoryName, total);
        }

        userVector = result;
        return userVector;
    }

    public void loadHistory(ArrayList<Event> events) {
        this.history = events;

        System.out.println("\n👤 User: " + name);
        System.out.println("Loaded events: " + history.size());

        // Показываем что загрузили
        for (Event e : history) {
            System.out.printf("  %s | Category %d | %d min%n",
                    e.getDate(), e.getContentId(), e.getWatchTime());
        }
    }

    public void showVector() {
        System.out.println("\n📊 User's vector " + name + ":");
        ArrayList<Double> coords = userVector.getCoordinates();
        List<String> categories = DataLoader.getCategoryList();

        for (int i = 0; i < coords.size(); i++) {
            if (coords.get(i) > 0) {
                System.out.printf("  %s: %.0f min%n", categories.get(i), coords.get(i));
            }
        }
    }

    // Method returns recommendations
    public void getRecommendations(int topN){
        System.out.println("RECOMMENDATIONS");
        Map<String, Vector> basis = DataLoader.getBasis();              // Getting basis
        List<Map.Entry<String, Double>> scores = new ArrayList<>();     // Lists of COS(vector1, vector2)

        for(Map.Entry<String, Vector> entry : basis.entrySet()){
            String categoryName = entry.getKey();       // Getting the category name
            Vector categoryVector = entry.getValue();   // Getting the category vector

            double cos = this.getVector().Cosine(categoryVector);   // Calculating COS(vector1, vector2)

            scores.add(Map.entry(categoryName, cos));
        }
        for (int i = 0; i < Math.min(topN, scores.size()); i++) {
            System.out.printf("  %d. %s (cosine: %.3f)%n",
                    i+1, scores.get(i).getKey(), scores.get(i).getValue());
        }
    }

    // Getters and Setters
    public String getName(){ return name; }
    public ArrayList<Event> getHistory() { return history; }
    public Vector getVector() { return userVector; }
    public double getLambda() { return lambda; }
    public void setLambda(double lambda) { this.lambda = lambda; }
}

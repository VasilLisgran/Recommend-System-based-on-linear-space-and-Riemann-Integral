package recommender.Model;

import recommender.Api.YouTubeDataLoader;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

// User class
public class User {
    private static final int MAX_DAYS = 360;

    private String name;                // Name
    private final CategoryRegistry categoryRegistry;
    private MyVector userMyVector;          // User's vector of content
    private ArrayList<Event> history;   // User's history

    private double lambda = 0.95;        // затухание в день

    // Constructors
    public User(CategoryRegistry categoryRegistry){
        this.categoryRegistry = categoryRegistry;
    }
    public User(String name, CategoryRegistry categoryRegistry){
        this.name = name;
        this.categoryRegistry = categoryRegistry;
        this.userMyVector = MyVector.zero(categoryRegistry.getDimension()); // Starting from the 0-vector
        this.history = new ArrayList<>();         // The new user has an empty history
    }

    public void addEvent(Event event) {
        history.add(event);
    }

    public void calculateWithDecayAndDynamics(double lambda) {
        LocalDate today = LocalDate.now();   // Today
        LocalDate cutoffDate = today.minusDays(MAX_DAYS);
        MyVector result = MyVector.zero(userMyVector.size());   // Result MyVector

        // Group watching by categories
        Map<Integer, TreeMap<LocalDate, Integer>> categoryByDay = new HashMap<>();

        for (Event event : history) {
            // Skip events older than 45 days
            if (event.getDate().isBefore(cutoffDate)) {
                continue;
            }

            String categoryId = event.getCategoryId();
            categoryByDay
                    .computeIfAbsent(Integer.valueOf(categoryId), k -> new TreeMap<>())
                    .merge(event.getDate(), event.getWatchTime(), Integer::sum);
        }

        System.out.println("\n📊 Integral decay");
        System.out.println("   λ = " + lambda);

        for (var entry : categoryByDay.entrySet()) {
            int categoryId = entry.getKey();    // Category ID
            String categoryName = categoryRegistry.getCategoryName(String.valueOf(categoryId));
            TreeMap<LocalDate, Integer> days = entry.getValue();   // Dates of watching this category

            // Find the first and the last day
            LocalDate firstDay = days.firstKey();

            double dynamic = 0.0;   // Dynamic value
            double total = 0.0;

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
                    double contribution = decay * dynamic;
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

            Integer index = categoryRegistry.getCategoryIndex(String.valueOf(categoryId));
            if (index != null) {
                result.set(index, total);
            }
            System.out.printf("  Total for %s: %.2f%n%n", categoryName, total);
        }

        userMyVector = result;
    }


    public void showVector() {
        if (userMyVector == null) {
            System.out.println("Vector not calculated yet. Call calculateWithDecayAndDynamics() first.");
            return;
        }

        System.out.println("\n📊 User's vector for " + name + ":");
        List<Double> coords = userMyVector.getCoordinates();

        for (int i = 0; i < coords.size(); i++) {
            double value = coords.get(i);
            if (value > 0.01) {
                System.out.printf("  Category %d: %.2f%n", i, value);
            }
        }
    }

    // Method returns recommendations
    // Method returns top-N categories
    public List<Map.Entry<String, Double>> getTopCategories(int topN) {
        System.out.println("Top Categories");

        Map<String, MyVector> basis = categoryRegistry.getAllBasisVectors();

        return basis.entrySet().stream()
                .map(entry -> {
                    double cosine = this.getVector().cosine(entry.getValue());
                    return Map.entry(entry.getKey(), cosine);
                })
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .collect(Collectors.toList());
    }

    // Getters and Setters
    public String getName(){ return name; }
    public ArrayList<Event> getHistory() { return history; }
    public MyVector getVector() { return userMyVector; }
    public double getLambda() { return lambda; }
    public void setLambda(double lambda) { this.lambda = lambda; }
}
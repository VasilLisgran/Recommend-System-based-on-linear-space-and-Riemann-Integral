import java.util.ArrayList;

public class User {
    private String name;
    private ArrayList<Event> history;

    public User(){ }

    public User(String name, ArrayList<Event> history){
        this.name = name;
        this.history = history;
    }

    public void addEvent(Event event){
        history.add(event);
    }

    public Vector CalculateVector(ArrayList<Event> history,
                                  int dimension){
        Vector res = Vector.zero(dimension);

        for (Event event : history) {
            int index_c = event.getContentId();
            int time = event.getWatchTime();

            double current = res.get(index_c);
            res.set(index_c, time + current);
        }

        return res;
    }

    public String getName(){ return name; }
    public ArrayList<Event> getHistory() { return history; }
}

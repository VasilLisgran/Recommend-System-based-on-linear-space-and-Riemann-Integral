import java.util.ArrayList;

public class User {
    private String name;
    private ArrayList<Event> history;

    public User(){ }

    public User(String name){
        this.name = name;
    }

    public void addEvent(Event event){
        history.add(event);
    }

    public String getName(){ return name; }
    public ArrayList<Event> getHistory() { return history; }
}

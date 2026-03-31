import java.util.List;

// Content Vector
public class Content {
    private int id;                     // Number of line in the file
    private String title;               // Title of the video/film/etc
    private List<String> categories;    // Categories of the content
    private Vector vector;              // Vector in the Space of categories

    public Content(){ }
    public Content(int id, String title, Vector vector){
        this.id = id;
        this.title = title;
        this.vector = vector;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public Vector getVector() { return vector; }
}

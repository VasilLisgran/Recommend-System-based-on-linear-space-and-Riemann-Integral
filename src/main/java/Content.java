public class Content {
    private int id;
    private String title;
    private Vector vector;

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

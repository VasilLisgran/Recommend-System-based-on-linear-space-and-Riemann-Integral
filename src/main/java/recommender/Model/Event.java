package recommender.Model;

import java.time.LocalDate;

// User's events : what did user do?
public class Event {
    private LocalDate date;
    private int contentId;
    private int watchTime;

    public Event() { }
    public Event(LocalDate date, int contentId, int watchTime){
        this.date = date;
        this.contentId = contentId;
        this.watchTime = watchTime;
    }

    public LocalDate getDate() { return date; }
    public int getContentId() { return contentId; }
    public int getWatchTime() { return watchTime; }

}

package model;

public class Message {
    private long id; // Thêm ID
    private String text;
    private boolean isSentByUser;
    private String timestamp; // Thêm thời gian
    private boolean isLoading = false;

    // Constructor đầy đủ
    public Message(long id, String text, boolean isSentByUser, String timestamp) {
        this.id = id;
        this.text = text;
        this.isSentByUser = isSentByUser;
        this.timestamp = timestamp;
    }
    public Message(boolean isLoading) {
        this.isLoading = isLoading;
        this.isSentByUser = false; // Loading luôn nằm bên trái (phía Bot)
        this.id = -1; // ID giả
    }

    public boolean isLoading() { return isLoading; }

    public long getId() { return id; }
    public String getText() { return text; }
    public boolean isSentByUser() { return isSentByUser; }
    public String getTimestamp() { return timestamp; }
}
package database;

public class TextEntity {
    private final int lineNumber;
    private final String content;
    private final long timestamp;

    public TextEntity(int lineNumber, String content, long timestamp) {
        this.lineNumber = lineNumber;
        this.content = content;
        this.timestamp = timestamp;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Line " + lineNumber + ": " + content;
    }
}

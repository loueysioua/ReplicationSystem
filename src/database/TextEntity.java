package database;

public class TextEntity {
    private final int lineNumber;
    private final String content;

    public TextEntity(int lineNumber, String content) {
        this.lineNumber = lineNumber;
        this.content = content;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "Line " + lineNumber + ": " + content;
    }
}

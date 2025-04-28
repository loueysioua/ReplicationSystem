package database;

import javax.persistence.*;

@Entity
@Table(name = "texts")
public class TextEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private int lineNumber;
    private String content;

    public TextEntity() {}

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
}

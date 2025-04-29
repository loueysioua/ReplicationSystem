package database;

import javax.persistence.*;

@Entity
@Table(name = "texts")
public class TextEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int lineNumber;
    private String content;

    public TextEntity() {}

    public TextEntity(int lineNumber, String content) {
        this.lineNumber = lineNumber;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getContent() {
        return content;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

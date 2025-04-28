package database;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

public class TextRepository {
    private final EntityManager em;

    public TextRepository(EntityManager em) {
        this.em = em;
    }

    public void saveLine(int lineNumber, String content) {
        em.getTransaction().begin();
        em.persist(new TextEntity(lineNumber, content));
        em.getTransaction().commit();
    }

    public String getLastLine() {
        TypedQuery<TextEntity> query = em.createQuery(
                "SELECT t FROM TextEntity t ORDER BY t.lineNumber DESC", TextEntity.class);
        List<TextEntity> results = query.setMaxResults(1).getResultList();
        return results.isEmpty() ? null : results.get(0).getContent();
    }

    public List<TextEntity> getAllLines() {
        TypedQuery<TextEntity> query = em.createQuery(
                "SELECT t FROM TextEntity t ORDER BY t.lineNumber ASC", TextEntity.class);
        return query.getResultList();
    }
}
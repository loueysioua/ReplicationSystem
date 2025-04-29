package utils;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public class JPAUtil {
    private static EntityManagerFactory emf;

    public static EntityManager getEntityManager(int replicaId) {
        if (emf == null) {
            Map<String, String> properties = new HashMap<>();
            properties.put("javax.persistence.jdbc.driver", "org.sqlite.JDBC");
            properties.put("javax.persistence.jdbc.url", "jdbc:sqlite:replica" + replicaId + ".db");
            properties.put("hibernate.dialect", "org.hibernate.dialect.SQLiteDialect");
            properties.put("hibernate.hbm2ddl.auto", "update");

            emf = Persistence.createEntityManagerFactory("replicaPU", properties);
        }
        return emf.createEntityManager();
    }
}
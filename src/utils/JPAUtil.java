package utils;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class JPAUtil {
    private static final Map<Integer, EntityManagerFactory> emfMap = new ConcurrentHashMap<>();
    private static final Properties properties = loadProperties();

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = JPAUtil.class.getClassLoader().getResourceAsStream("main/resources/application.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find application.properties");
            }
            props.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Error loading application.properties", ex);
        }
        return props;
    }

    public static EntityManager getEntityManager(int replicaId) {
        EntityManagerFactory emf = emfMap.get(replicaId);

        if (emf == null) {
            synchronized (JPAUtil.class) {
                emf = emfMap.get(replicaId);
                if (emf == null) {
                    Map<String, String> dbProps = new HashMap<>();

                    // Load properties from application.properties
                    dbProps.put("hibernate.dialect", properties.getProperty("hibernate.dialect"));
                    dbProps.put("hibernate.hbm2ddl.auto", properties.getProperty("hibernate.hbm2ddl.auto"));
                    dbProps.put("javax.persistence.jdbc.driver", properties.getProperty("javax.persistence.jdbc.driver"));

                    // Modify the URL to include the replica ID
                    String baseUrl = properties.getProperty("javax.persistence.jdbc.url");
                    String dbUrl = baseUrl.replace("replica.db", "replica" + replicaId + ".db");
                    dbProps.put("javax.persistence.jdbc.url", dbUrl);

                    // You still need a minimal persistence.xml file with just the persistence-unit definition
                    // but you're overriding all the properties programmatically
                    emf = Persistence.createEntityManagerFactory("replicaPU", dbProps);
                    emfMap.put(replicaId, emf);
                }
            }
        }

        return emf.createEntityManager();
    }

    public static void closeEntityManagerFactory(int replicaId) {
        EntityManagerFactory emf = emfMap.remove(replicaId);
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    public static void closeAllEntityManagerFactories() {
        for (EntityManagerFactory emf : emfMap.values()) {
            if (emf != null && emf.isOpen()) {
                emf.close();
            }
        }
        emfMap.clear();
    }
}
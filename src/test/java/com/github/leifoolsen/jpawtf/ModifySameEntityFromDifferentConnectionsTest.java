package com.github.leifoolsen.jpawtf;

import com.github.leifoolsen.jpawtf.domain.Counter;
import com.github.leifoolsen.jpawtf.entitymanager.EntityManagerFactoryHelper;
import com.google.common.base.Strings;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.persistence.CacheRetrieveMode;
import javax.persistence.CacheStoreMode;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

public class ModifySameEntityFromDifferentConnectionsTest {

    private static final Logger logger = LoggerFactory.getLogger(ModifySameEntityFromDifferentConnectionsTest.class);

    // Cache properties are ignored by Hibernate 4.3.8.Final, see: https://hibernate.atlassian.net/browse/HHH-9045
    // Don't know how to get around this in Hibernate. Conclusion: Avoid Hibernate until issue is fixed
    private static HashMap<String, Object> cacheModes = new HashMap<String, Object>() {{
        put("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);
        put("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);
    }};

    @BeforeClass
    public static void before() {
        // Bridge java.util.logging to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @Test
    public void modifyWithEclipseLink() {
        logger.debug("**** Modify Counter with EclipseLink ****");
        logger.debug("**** id, version, count, maxCount    ****");

        Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.url", "jdbc:h2:file:./target/myh2;AUTO_SERVER=TRUE");
        properties.put("eclipselink.ddl-generation", "drop-and-create-tables");

        EntityManagerFactory emf = EntityManagerFactoryHelper.createEntityManagerFactoryFor(
                EntityManagerFactoryHelper.PU_ECLIPSELINK, properties, Arrays.asList(Counter.class));

        //EntityManager em = emf.createEntityManager();
        EntityManager em = emf.createEntityManager(cacheModes);

        persistCounter(em);

        modifyCounterFromAnotherDatabaseConnection();

        expectCounterToBeLessThanTenAfterModificationFromAnoterConnection_WTF(em);

        em.close();
        emf.close();
        logger.debug("End\n");
    }

    @Test
    public void modifyWithHibernate() {
        logger.debug("**** Modify Counter with Hibernate  ****");
        logger.debug("**** id, version, count, maxCount   ****");

        Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.url", "jdbc:h2:file:./target/myh2;AUTO_SERVER=TRUE");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");

        EntityManagerFactory emf = EntityManagerFactoryHelper.createEntityManagerFactoryFor(
                EntityManagerFactoryHelper.PU_HIBERNATE, properties, Arrays.asList(Counter.class));

        EntityManager em = emf.createEntityManager(cacheModes); // NOTE: No effect on Hibernate

        persistCounter(em);

        modifyCounterFromAnotherDatabaseConnection();

        expectCounterToBeLessThanTenAfterModificationFromAnoterConnection_WTF(em);

        em.close();
        emf.close();
        logger.debug("End\n");
    }

    @Test
    @Ignore  // Run manually
    public void runWithExternalAppEclipseLink() throws IOException, InterruptedException {
        logger.debug("**** Modify Counter with External app, EclipseLink  ****");
        logger.debug("**** id, version, count, maxCount                   ****");

        Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.url", "jdbc:h2:file:./target/myh2;AUTO_SERVER=TRUE");
        properties.put("eclipselink.ddl-generation", "drop-and-create-tables");

        EntityManagerFactory emf = EntityManagerFactoryHelper.createEntityManagerFactoryFor(
                EntityManagerFactoryHelper.PU_ECLIPSELINK, properties, Arrays.asList(Counter.class));

        EntityManager em = emf.createEntityManager(cacheModes); // NOTE: cacheModes has no effect on Hibernate

        persistCounter(em);


        logCounter("2: Execute ExternalAppModifyingEntity within 15s", (Object[]) null);
        logCounter("2: Run mvn exec:java from console", (Object[]) null);
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        expectCounterToBeLessThanTenAfterModificationFromAnoterConnection_WTF(em);

        em.close();
        emf.close();
        logger.debug("End\n");
    }

    @Test
    @Ignore  // Run manually
    public void runWithExternalAppHibernate() {
        logger.debug("**** Modify Counter with External app, Hibernate  ****");
        logger.debug("**** id, version, count, maxCount                 ****");

        Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.url", "jdbc:h2:file:./target/myh2;AUTO_SERVER=TRUE");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");

        EntityManagerFactory emf = EntityManagerFactoryHelper.createEntityManagerFactoryFor(
                EntityManagerFactoryHelper.PU_HIBERNATE, properties, Arrays.asList(Counter.class));

        EntityManager em = emf.createEntityManager(cacheModes); // NOTE: cacheModes has no effect on Hibernate

        persistCounter(em);

        logCounter("2: Execute ExternalAppModifyingEntity within 15s", (Object[])null);
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        expectCounterToBeLessThanTenAfterModificationFromAnoterConnection_WTF(em);

        em.close();
        emf.close();
        logger.debug("End\n");
    }


    private void persistCounter(EntityManager em) {
        Counter counter = new Counter("101", 10, 10);
        em.getTransaction().begin();
        em.persist(counter);
        em.flush();
        em.clear();
        em.getTransaction().commit();

        Counter persistedCounter = em.find(Counter.class, "101");
        logCounter("1: First connection,  em.persist(counter)", persistedCounter);
    }

    private void expectCounterToBeLessThanTenAfterModificationFromAnoterConnection_WTF(EntityManager em) {

        Counter modifiedCounter = em.find(Counter.class, "101");
        logCounter("3: First connection,  em.find(Counter.class, \"101\")", modifiedCounter);


        // Will give u a lot of trouble if entity is red from L2 cache
        //em.getTransaction().begin();
        //modifiedCounter.count--;
        //Counter mergedCounter = em.merge(modifiedCounter);
        //em.flush();
        //em.getTransaction().commit();

        String jpql = "select c from Counter c where c.id = '101'";
        TypedQuery<Counter> tq = em.createQuery(jpql, Counter.class);
        List<Counter> withTypedQuery = tq.setMaxResults(1).getResultList();
        logCounter("4: First connection,  em.createQuery(jpql, Counter.class)", withTypedQuery.get(0));

        String sql = "select c.id, c.version, c.count, c.maxCount from COUNTER c where c.id = 101";

        Query q = em.createNativeQuery(sql, Counter.class);
        //q.setHint("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);
        //q.setHint("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);

        List<Counter> withResultclass = q.setMaxResults(1).getResultList();
        logCounter("5: First connection,  em.createNativeQuery(sql, Counter.class)", withResultclass.get(0));

        q = em.createNativeQuery(sql);
        List<Object[]> withoutResultclass = q.setMaxResults(1).getResultList();
        Object[] o = withoutResultclass.get(0);
        logCounter("6: First connection,  em.createNativeQuery(sql)", o);

        Counter refreshedCounter = em.find(Counter.class, "101");
        em.refresh(refreshedCounter);
        logCounter("7: First connection,  em.refresh(entity)", refreshedCounter);

        assertThat(refreshedCounter.count, lessThan(10));

    }

    private void modifyCounterFromAnotherDatabaseConnection() {

        Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.url", "jdbc:h2:file:./target/myh2;AUTO_SERVER=TRUE");
        properties.put("hibernate.hbm2ddl.auto", "validate");

        EntityManagerFactory emf = EntityManagerFactoryHelper.createEntityManagerFactoryFor(
                EntityManagerFactoryHelper.PU_HIBERNATE, properties, Arrays.asList(Counter.class));

        EntityManager em = emf.createEntityManager();

        Counter counter = em.find(Counter.class, "101");
        logCounter("2: Second connection, em.find(Counter.class, \"101\")", counter);

        counter.count--;

        em.getTransaction().begin();
        Counter mergedCounter = em.merge(counter);
        em.flush();
        em.getTransaction().commit();
        logCounter("2: Second connection, em.merge(counter)", counter);

        Counter modifiedCounter = em.find(Counter.class, "101");
        logCounter("2: Second connection, em.find(Counter.class, \"101\")", modifiedCounter);

        em.close();
        emf.close();
    }

    private void logCounter(String logMessage, Counter counter) {
        Object[] o = {counter.id, counter.version, counter.count, counter.maxCount};
        logCounter(logMessage, o);
    }

    private void logCounter(String logMessage, Object[] params) {
        logger.debug(Strings.padEnd(logMessage, 62, '.') + ": {}, {}, {}, {}", params);
    }
}

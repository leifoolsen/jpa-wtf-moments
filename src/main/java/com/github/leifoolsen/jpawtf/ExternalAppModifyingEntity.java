package com.github.leifoolsen.jpawtf;

import com.github.leifoolsen.jpawtf.domain.Counter;
import com.github.leifoolsen.jpawtf.entitymanager.EntityManagerFactoryHelper;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.Properties;

public class ExternalAppModifyingEntity {
    private static final Logger logger = LoggerFactory.getLogger(ExternalAppModifyingEntity.class);

    public static void main(String[] args) throws Exception {

        // Bridge java.util.logging to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.url", "jdbc:h2:file:./target/myh2;AUTO_SERVER=TRUE");
        //properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("eclipselink.ddl-generation", "none");

        EntityManagerFactory emf = EntityManagerFactoryHelper.createEntityManagerFactoryFor(
                EntityManagerFactoryHelper.PU_ECLIPSELINK, properties, Arrays.asList(Counter.class));

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

    private static void logCounter(String logMessage, Counter counter) {
        Object[] o = {counter.id, counter.version, counter.count, counter.maxCount};
        logCounter(logMessage, o);
    }

    private static void logCounter(String logMessage, Object[] params) {
        logger.debug(Strings.padEnd(logMessage, 62, '.') + ": {}, {}, {}, {}", params);
    }
}
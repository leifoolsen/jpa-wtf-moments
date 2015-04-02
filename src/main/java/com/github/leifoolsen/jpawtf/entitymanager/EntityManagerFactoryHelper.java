package com.github.leifoolsen.jpawtf.entitymanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.List;
import java.util.Properties;

public class EntityManagerFactoryHelper {

    public static final String PU_ECLIPSELINK = "jpa-eclipselink";
    public static final String PU_HIBERNATE = "jpa-hibernate";

    private static final Logger logger = LoggerFactory.getLogger(EntityManagerFactoryHelper.class);

    private EntityManagerFactoryHelper() {}

    public static EntityManagerFactory createEntityManagerFactoryFor(
            final String persistenceUnitName, Properties properties, List<?> entityClasses) {

        // Configure PU //
        Properties p = new Properties();
        p.put("javax.persistence.jdbc.driver", "org.h2.Driver");
        p.put("javax.persistence.jdbc.url", "jdbc:h2:mem:mymemdb");
        p.put("javax.persistence.jdbc.user", "sa");
        p.put("javax.persistence.jdbc.password", "");

        if(PU_ECLIPSELINK.equals(persistenceUnitName)) {
            // Eclipse Link (EL)

            // eclipselink.ddl-generation: "create-tables", "create-or-extend-tables", "drop-and-create-tables", "none"
            //                        See: http://eclipse.org/eclipselink/documentation/2.5/jpa/extensions/p_ddl_generation.htm
            p.put("eclipselink.ddl-generation", "drop-and-create-tables"); //
            p.put("eclipselink.ddl-generation.output-mode", "database");
            p.put("eclipselink.logging.level", "OFF");  // OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL
            p.put("eclipselink.logging.level.sql", "INFO");
            p.put("eclipselink.logging.parameters", "true");
            p.put("eclipselink.logging.timestamp", "true");
            p.put("eclipselink.logging.session", "true");
            p.put("eclipselink.logging.thread", "true");
            p.put("eclipselink.logging.exceptions", "true");

            // EL optimization, see: http://java-persistence-performance.blogspot.no/2011/06/how-to-improve-jpa-performance-by-1825.html
            p.put("eclipselink.jdbc.cache-statements", "true");
            p.put("eclipselink.jdbc.batch-writing", "JDBC");
            p.put("eclipselink.jdbc.batch-writing.size", "1000");
            p.put("eclipselink.persistence-context.flush-mode", "commit");
            p.put("eclipselink.persistence-context.close-on-commit", "true");
            p.put("eclipselink.persistence-context.persist-on-commit", "false");
            p.put("eclipselink.flush-clear.cache", "drop");

            // Add entity classes, Eclipselink
            p.put("eclipselink.metadata-source", "XML");

            // For EL, persistence classes must be added to META-INF/eclipselink-orm.xml by hand :-(
            p.put("eclipselink.metadata-source.xml.file", "META-INF/eclipselink-orm.xml");
        }
        else {
            // Hibernate
            p.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");

            // hibernate.hbm2ddl.auto: "validate", "update", "create", "create-drop"
            //                    See: http://hibernate.org/orm/documentation/
            p.put("hibernate.hbm2ddl.auto", "create-drop"); //
            p.put("hibernate.default_batch_fetch_size", "16");
            p.put("hibernate.show_sql", "false");
            p.put("hibernate.format_sql", "true");

            /*
            p.put("hibernate.connection.autocommit", "false");
            p.put("hibernate.connection.release_mode", "on_close");
            p.put("hibernate.cache.use_second_level_cache", "false");
            p.put("hibernate.cache.use_query_cache", "false");
            p.put("org.hibernate.cacheable", "false");
            */

            /*
            p.put("hibernate.cache.use_second_level_cache", "true");
            p.put("hibernate.cache.provider_class", "net.sf.ehcache.hibernate.SingletonEhCacheProvider");
            p.put("net.sf.ehcache.configurationResourceName", "/ehcache.xml");
            */

            p.put("hibernate.connection.pool_size", "10");
            p.put("hibernate.connection.provider_class", "org.hibernate.service.jdbc.connections.internal.C3P0ConnectionProvider");
            p.put("hibernate.c3p0.min_size", "1");
            p.put("hibernate.c3p0.max_size", "10");
            p.put("hibernate.c3p0.acquire_increment", "2");
            p.put("hibernate.c3p0.timeout", "500");
            p.put("hibernate.c3p0.max_statements", "50");
            p.put("hibernate.c3p0.idle_test_period", "1000");

            // Add entity classes, Hibernate
            p.put(org.hibernate.jpa.AvailableSettings.LOADED_CLASSES, entityClasses);
        }

        Properties mergedProperties = new Properties();
        mergedProperties.putAll(p);
        if(properties != null) {
            mergedProperties.putAll(properties);
        }

        try {
            return Persistence.createEntityManagerFactory(persistenceUnitName, mergedProperties);
        }
        catch(Throwable t) {
            logger.error("Entity Manager Factory creation failed", t);
            throw new ExceptionInInitializerError(t);
        }

    }
}

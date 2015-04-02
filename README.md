# WTF moments I have experienced with JPA2
WTF moments I have experienced wit JPA2, and how to possibly get around them.

## WTF #1: Update the same entity, but from different sources

In JPA2, if an external source modifies a cached entity, the modifications is not automatically reflected in the 
applications L2 cache.

### Imagine the following scenario

Given an entity:

    @Entity
    public class Counter {
      @Id
      @Column(length=36)
      public String id;
  
      @Version
      public Long version;
  
      public int count;
      public int maxCount;
  
      public Counter() {}
    }

The application persist a new entity with count value = 10:
 
    Counter c = new Counter(id:"101", count:10, maxCount:10);
    em.getTransaction().begin();
    em.persist(c);
    em.flush();
    em.getTransaction().commit();
    
Anoter application, e.g. the database console, connects to the database and set Counter.count = 9 for Counter.id='101':
 
    UPDATE COUNTER
    SET version=2, count=9
    WHERE id='101';
    COMMIT;

The application, still on the same thread and using the same entitymanager factory, reads back the entity:

    // em.find
    Counter c1 = em.find(Counter.class, "101");
    assertThat(c1.count, lessThan(10));
    
    // JPQL
    String jpql = "select c from Counter c where c.id = '101'";
    TypedQuery<Counter> tq = em.createQuery(jpql, Counter.class);
    List<Counter> withTypedQuery = tq.setMaxResults(1).getResultList();
    Counter c2 = withTypedQuery.get(0);
    assertThat(c2.count, lessThan(10));
    
    // SQL
    String sql = "select c.id, c.version, c.count, c.maxCount from COUNTER c where c.id = 101";
    Query q = em.createNativeQuery(sql, Counter.class);
    List<Counter> withResultClass = q.setMaxResults(1).getResultList();
    Counter c3 = withResultClass.get(0);
    assertThat(c3.count, lessThan(10));
    
All three assertions gives the same result:
    
    java.lang.AssertionError: 
    Expected: a value less than <10>
         but: <10> was equal to <10>
         
### WTF!?
Acording to spec this behavior is correct (unfortunately); if an external source modifies an entity that is already 
present in this applications L2 cache, the modification is not automagically reflected to the L2 cache. For large scale,
distributed applications, this behavior can quickly lead to maintenance nightmare. If for example, the app attempts to modify 
the entity that is already changed by an external application, we'll get an OptimisticLockException!
 
    em.getTransaction().begin();
    Counter c = em.find(Counter.class, "101");
    c.count--;
    Counter mergedCounter = em.merge(c);
    em.flush();
    em.getTransaction().commit();
    
JPA will puke a *javax.persistence.OptimisticLockException* right in your face, leaving you with a lot of WTF's and WTH's. 

### How to get around WTF #1

#### Use native query without the resultclass parameter

    String sql = "select c.id, c.version, c.count, c.maxCount from COUNTER c where c.id = 101";
    Query q = em.createNativeQuery(sql);
    List<Object[]> results = q.setMaxResults(1).getResultList();
    Object[] o = results.get(0);
    
    // Not very elegant; must map to entity by hand
    Counter c = new Counter();
    c.id = o[0];
    c.version = o[1];
    c.count = o[2);
    c.maxCount = o[3];
    
    // ... but we got the expected answer
    assertThat(c.count, lessThan(10));

#### Call *em.refresh(entity)*

    Counter c = em.find(Counter.class, "101");
    em.refresh(c);
    assertThat(c.count, lessThan(10));

#### Set *"javax.persistence.cache.retrieveMode"* and *"javax.persistence.cache.storeMode"* properties
JPA2 has two properties which can be used to control L2 cache, *"javax.persistence.cache.retrieveMode"* and 
*"javax.persistence.cache.storeMode"*. The properties can be set on the entity manager, on the entity manager's find 
method, or as a query hint on a given query. If set on the entity manager, the effect will last for the liftetime of
the entity manager - the two other settings are per request. 

#### Set properties on entity manager
    HashMap<String, Object> cacheModes = new HashMap<String, Object>() {{
        put("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);
        put("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);
    }};
    
    EntityManager em = emf.createEntityManager(cacheModes);
    
    // em.find
    Counter c1 = em.find(Counter.class, "101");
    assertThat(c1.count, lessThan(10)); // OK
    
    // JPQL
    String jpql = "select c from Counter c where c.id = '101'";
    TypedQuery<Counter> tq = em.createQuery(jpql, Counter.class);
    List<Counter> withTypedQuery = tq.setMaxResults(1).getResultList();
    Counter c2 = withTypedQuery.get(0);
    assertThat(c2.count, lessThan(10)); // OK
    
    // SQL
    String sql = "select c.id, c.version, c.count, c.maxCount from COUNTER c where c.id = 101";
    Query q = em.createNativeQuery(sql, Counter.class);
    List<Counter> withResultClass = q.setMaxResults(1).getResultList();
    Counter c3 = withResultClass.get(0);
    assertThat(c3.count, lessThan(10)); // OK

#### Set properties per request
    HashMap<String, Object> cacheModes = new HashMap<String, Object>() {{
        put("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);
        put("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);
    }};
    
    EntityManager em = emf.createEntityManager();
    
    // em.find
    Counter c1 = em.find(Counter.class, "101", cacheModes);
    assertThat(c1.count, lessThan(10)); // OK
    
    // JPQL
    String jpql = "select c from Counter c where c.id = '101'";
    TypedQuery<Counter> tq = em.createQuery(jpql, Counter.class);
    tq.setHint("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);
    tq.setHint("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);
    List<Counter> withTypedQuery = tq.setMaxResults(1).getResultList();
    Counter c2 = withTypedQuery.get(0);
    assertThat(c2.count, lessThan(10)); // OK
    
    // SQL
    String sql = "select c.id, c.version, c.count, c.maxCount from COUNTER c where c.id = 101";
    Query q = em.createNativeQuery(sql, Counter.class);
    q.setHint("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);
    q.setHint("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);
    List<Counter> withResultClass = q.setMaxResults(1).getResultList();
    Counter c3 = withResultClass.get(0);
    assertThat(c3.count, lessThan(10)); // OK


**Note**: *javax.persistence.cache* properties is ignored by Hibernate 4.3.8.Final (and 5.0.0.Beta1 as well), 
see: https://hibernate.atlassian.net/browse/HHH-9045. The Hibernate team has rejected the bug and they will not  
fix the flaw. This is, in my opinion, rather ignorant. A large scale, distributed, JPA based application need to control 
all aspects og L2 caching. In this context, Hibernate is useless as a persistence provider!

## WTF #2: Lazy Load Exception
Stay tuned :-)

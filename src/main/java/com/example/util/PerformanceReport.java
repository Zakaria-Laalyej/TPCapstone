package com.example.util;

import org.hibernate.Session;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;

public class PerformanceReport {

    private final EntityManager em;

    public PerformanceReport(EntityManager em) {
        this.em = em;
    }

    public void printReport() {
        Session session = em.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();

        System.out.println("\n==================== RAPPORT DE PERFORMANCE ====================");
        System.out.println("Requetes executees                 : " + stats.getQueryExecutionCount());
        System.out.println("Cache de second niveau (L2) Hits   : " + stats.getSecondLevelCacheHitCount());
        System.out.println("Cache de second niveau (L2) Misses : " + stats.getSecondLevelCacheMissCount());
        System.out.println("Cache de second niveau (L2) Puts   : " + stats.getSecondLevelCachePutCount());
        System.out.println("Transactions executees             : " + stats.getTransactionCount());
        System.out.println("Duree max requete (ms)             : " + stats.getQueryExecutionMaxTime());
        System.out.println("=================================================================\n");
    }
}

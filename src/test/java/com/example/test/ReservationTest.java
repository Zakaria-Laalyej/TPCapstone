package com.example.test;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;

public class ReservationTest {

    private static EntityManagerFactory emf;

    @BeforeClass
    public static void init() throws Exception {
        emf = Persistence.createEntityManagerFactory("ReservationPU");

        MigrationExecutor migration = new MigrationExecutor(
                "jdbc:h2:mem:reservationdb;DB_CLOSE_DELAY=-1", "sa", "");
        migration.executeMigration("db/migration/v2_0_add_description.sql");

        EntityManager em = emf.createEntityManager();
        DataInitializer init = new DataInitializer(em);
        init.initData();
        em.close();
    }

    @AfterClass
    public static void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    public void testCreateReservation() {
        EntityManager em = emf.createEntityManager();
        ReservationService service = new ReservationService(em);

        LocalDateTime debut = LocalDateTime.now().plusDays(5).withHour(10).withMinute(0);
        LocalDateTime fin = debut.plusHours(2);

        Reservation res = service.createReservation(1L, 3L, debut, fin);
        Assert.assertNotNull(res);
        Assert.assertNotNull(res.getId());
        Assert.assertEquals("CONFIRMEE", res.getStatut());
        em.close();
    }

    @Test(expected = IllegalStateException.class)
    public void testConflitReservationDetecte() {
        EntityManager em = emf.createEntityManager();
        ReservationService service = new ReservationService(em);

        // Creneau de la premiere reservation inseree par DataInitializer (demain 9h-11h sur Salle 1)
        LocalDateTime debut = LocalDateTime.now().plusDays(1).withHour(9).withMinute(30);
        LocalDateTime fin = debut.plusHours(1);

        // Doit declencher IllegalStateException car salle 1 occupee
        service.createReservation(2L, 1L, debut, fin);
        em.close();
    }

    @Test
    public void testRechercheCriteria() {
        EntityManager em = emf.createEntityManager();
        ReservationService service = new ReservationService(em);

        List<Reservation> results = service.rechercherMultiCriteres("Toubkal", null, "CONFIRMEE");
        Assert.assertNotNull(results);
        Assert.assertFalse(results.isEmpty());
        Assert.assertTrue(results.get(0).getSalle().getNom().contains("Toubkal"));
        em.close();
    }

    @Test
    public void testOptimisticLockingSurSalle() {
        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();

        em1.getTransaction().begin();
        em2.getTransaction().begin();

        Salle s1 = em1.find(Salle.class, 2L);
        Salle s2 = em2.find(Salle.class, 2L);

        s1.setNom(s1.getNom() + " - Edit1");
        em1.getTransaction().commit();

        boolean conflictCaught = false;
        try {
            s2.setNom(s2.getNom() + " - Edit2");
            em2.getTransaction().commit();
        } catch (Exception e) {
            conflictCaught = true;
            if (em2.getTransaction().isActive()) em2.getTransaction().rollback();
        }

        Assert.assertTrue("Le conflit concurrent doit lever une exception de verrouillage optimiste", conflictCaught);
        em1.close();
        em2.close();
    }
}

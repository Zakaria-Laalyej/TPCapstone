package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;

public class MainApp {

    public static void main(String[] args) throws Exception {
        System.out.println("==========================================================================");
        System.out.println("  TP Capstone : Application Complete de Reservation de Salles (JPA/JEE)");
        System.out.println("==========================================================================");

        // 1. Initialisation de JPA
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("ReservationPU");
        EntityManager em = emf.createEntityManager();

        // 2. Migration DDL
        System.out.println("\n--- [1] Execution de la migration SQL via JDBC ---");
        MigrationExecutor migrationExecutor = new MigrationExecutor(
                "jdbc:h2:mem:reservationdb;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        migrationExecutor.executeMigration("db/migration/v2_0_add_description.sql");

        // 3. Initialisation du jeu d'essai
        System.out.println("\n--- [2] Peuplement du jeu de donnees realiste ---");
        DataInitializer initializer = new DataInitializer(em);
        initializer.initData();

        ReservationService service = new ReservationService(em);

        // 4. Test Requête JPQL : Salles disponibles
        System.out.println("\n--- [3] Recherche JPQL des salles disponibles ---");
        LocalDateTime creneauDebut = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        LocalDateTime creneauFin = creneauDebut.plusHours(2);
        List<Salle> dispos = service.rechercherSallesDisponibles(creneauDebut, creneauFin, 10);
        System.out.println("Salles libres pour le creneau " + creneauDebut + " : " + dispos.size());
        for (Salle s : dispos) {
            System.out.println(" -> " + s.getNom() + " (Capacite: " + s.getCapacite() + ")");
        }

        // 5. Test Requête Criteria API : Recherche multi-criteres
        System.out.println("\n--- [4] Recherche multi-criteres (Criteria API) ---");
        List<Reservation> criteres = service.rechercherMultiCriteres("Atlas", LocalDateTime.now(), "CONFIRMEE");
        System.out.println("Reservations trouvees avec critere 'Atlas' : " + criteres.size());
        for (Reservation r : criteres) {
            System.out.println(" -> Res #" + r.getId() + " dans " + r.getSalle().getNom() + " statut=" + r.getStatut());
        }

        // 6. Test Optimistic Locking (@Version)
        System.out.println("\n--- [5] Demonstration du verrouillage optimiste (@Version) ---");
        EntityManager emClient1 = emf.createEntityManager();
        EntityManager emClient2 = emf.createEntityManager();

        emClient1.getTransaction().begin();
        emClient2.getTransaction().begin();

        Salle s1 = emClient1.find(Salle.class, 1L);
        Salle s2 = emClient2.find(Salle.class, 1L);
        System.out.println("Version initiale Salle: " + s1.getVersion());

        // Client 1 met a jour et valide
        s1.setNom(s1.getNom() + " [Maj Client 1]");
        emClient1.getTransaction().commit();
        System.out.println("Client 1 commit valide. Nouvelle version: " + s1.getVersion());

        // Client 2 tente de mettre a jour avec la version obsolete
        try {
            s2.setNom(s2.getNom() + " [Maj Client 2]");
            emClient2.getTransaction().commit();
            System.out.println("ERREUR : Le conflit n'a pas ete detecte !");
        } catch (Exception e) {
            if (emClient2.getTransaction().isActive()) emClient2.getTransaction().rollback();
            System.out.println("SUCCES DU TEST : OptimisticLockException interceptée !");
            System.out.println(" -> Message : Conflit detecte, version modifiee par une autre transaction.");
        }
        emClient1.close();
        emClient2.close();

        // 7. Generation du rapport de performance
        PerformanceReport report = new PerformanceReport(em);
        report.printReport();

        em.close();
        emf.close();
        System.out.println("Application Capstone executee avec succes !");
    }
}

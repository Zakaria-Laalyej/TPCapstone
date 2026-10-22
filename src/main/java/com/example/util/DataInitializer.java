package com.example.util;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.LocalDateTime;

public class DataInitializer {

    private final EntityManager em;

    public DataInitializer(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        EntityTransaction tx = em.getTransaction();
        boolean localTx = !tx.isActive();
        if (localTx) tx.begin();

        // 1. Utilisateurs
        Utilisateur user1 = new Utilisateur("Alice Dupont", "alice.dupont@example.com", "ORGANISATEUR");
        Utilisateur user2 = new Utilisateur("Bob Martin", "bob.martin@example.com", "MEMBRE");
        Utilisateur user3 = new Utilisateur("Charlie Bennani", "charlie.bennani@example.com", "ADMIN");

        em.persist(user1);
        em.persist(user2);
        em.persist(user3);

        // 2. Salles
        Salle salleA = new Salle("Salle A (Atlas)", 20, "Salle equipée videoprojecteur");
        Salle salleB = new Salle("Salle B (Toubkal)", 50, "Amphitheatre de conference");
        Salle salleC = new Salle("Salle C (Majorelle)", 10, "Box de reunion equipe");
        Salle salleD = new Salle("Salle D (Oukaimeden)", 30, "Salle de formation modulaire");

        em.persist(salleA);
        em.persist(salleB);
        em.persist(salleC);
        em.persist(salleD);

        // 3. Reservations realistes
        LocalDateTime demain = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        Reservation res1 = new Reservation(salleA, user1, demain, demain.plusHours(2));

        LocalDateTime apresDemain = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0);
        Reservation res2 = new Reservation(salleB, user2, apresDemain, apresDemain.plusHours(3));

        em.persist(res1);
        em.persist(res2);

        if (localTx) tx.commit();
        System.out.println("[DataInitializer] Jeu de donnees initialise (3 utilisateurs, 4 salles, 2 reservations).");
    }
}

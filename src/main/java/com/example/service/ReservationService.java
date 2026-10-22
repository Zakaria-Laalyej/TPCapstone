package com.example.service;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ReservationService {

    private static final Logger LOGGER = Logger.getLogger(ReservationService.class.getName());
    private final EntityManager em;

    public ReservationService(EntityManager em) {
        this.em = em;
    }

    /**
     * Creation d'une reservation avec verification de conflit temporel.
     */
    public Reservation createReservation(Long utilisateurId, Long salleId, LocalDateTime debut, LocalDateTime fin) {
        if (!debut.isBefore(fin)) {
            throw new IllegalArgumentException("La date de debut doit preceder la date de fin.");
        }

        EntityTransaction tx = em.getTransaction();
        boolean localTx = !tx.isActive();
        if (localTx) tx.begin();

        try {
            Salle salle = em.find(Salle.class, salleId);
            Utilisateur utilisateur = em.find(Utilisateur.class, utilisateurId);

            if (salle == null || utilisateur == null) {
                throw new IllegalArgumentException("Salle ou Utilisateur introuvable.");
            }

            // Verification de disponibilite : conflit si (debut < r.dateFin ET fin > r.dateDebut)
            String jpqlConflit = "SELECT COUNT(r) FROM Reservation r " +
                    "WHERE r.salle.id = :salleId " +
                    "AND r.statut = 'CONFIRMEE' " +
                    "AND r.dateDebut < :fin AND r.dateFin > :debut";

            Long conflits = em.createQuery(jpqlConflit, Long.class)
                    .setParameter("salleId", salleId)
                    .setParameter("debut", debut)
                    .setParameter("fin", fin)
                    .getSingleResult();

            if (conflits > 0) {
                throw new IllegalStateException("La salle est deja reservee sur ce creneau !");
            }

            Reservation reservation = new Reservation(salle, utilisateur, debut, fin);
            em.persist(reservation);

            if (localTx) tx.commit();
            LOGGER.info("Reservation creee avec succes [ID: " + reservation.getId() + "] pour " + utilisateur.getEmail());
            return reservation;
        } catch (Exception e) {
            if (localTx && tx.isActive()) tx.rollback();
            LOGGER.severe("Erreur creation reservation : " + e.getMessage());
            throw e;
        }
    }

    /**
     * Recherche de salles disponibles (JPQL).
     */
    public List<Salle> rechercherSallesDisponibles(LocalDateTime debut, LocalDateTime fin, int capaciteMin) {
        String jpql = "SELECT s FROM Salle s WHERE s.capacite >= :capaciteMin " +
                "AND s.id NOT IN (" +
                "    SELECT r.salle.id FROM Reservation r " +
                "    WHERE r.statut = 'CONFIRMEE' " +
                "    AND r.dateDebut < :fin AND r.dateFin > :debut" +
                ")";

        return em.createQuery(jpql, Salle.class)
                .setParameter("capaciteMin", capaciteMin)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList();
    }

    /**
     * Recherche multicriteres dynamique avec l'API Criteria.
     */
    public List<Reservation> rechercherMultiCriteres(String nomSalle, LocalDateTime dateDebutApres, String statut) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Reservation> cq = cb.createQuery(Reservation.class);
        Root<Reservation> root = cq.from(Reservation.class);

        List<Predicate> predicates = new ArrayList<>();

        if (nomSalle != null && !nomSalle.trim().isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get("salle").get("nom")), "%" + nomSalle.toLowerCase() + "%"));
        }
        if (dateDebutApres != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("dateDebut"), dateDebutApres));
        }
        if (statut != null && !statut.trim().isEmpty()) {
            predicates.add(cb.equal(root.get("statut"), statut));
        }

        cq.select(root).where(predicates.toArray(new Predicate[0]));
        return em.createQuery(cq).getResultList();
    }

    /**
     * Modification avec gestion du verrouillage optimiste @Version.
     */
    public Reservation modifierReservation(Long reservationId, LocalDateTime newDebut, LocalDateTime newFin) {
        EntityTransaction tx = em.getTransaction();
        boolean localTx = !tx.isActive();
        if (localTx) tx.begin();

        try {
            Reservation r = em.find(Reservation.class, reservationId);
            if (r == null) throw new IllegalArgumentException("Reservation introuvable: " + reservationId);

            r.setDateDebut(newDebut);
            r.setDateFin(newFin);

            em.flush();
            if (localTx) tx.commit();
            LOGGER.info("Reservation modifiee avec succes [Version " + r.getVersion() + "]");
            return r;
        } catch (OptimisticLockException ole) {
            if (localTx && tx.isActive()) tx.rollback();
            LOGGER.warning("Conflit de verrouillage optimiste detecte : " + ole.getMessage());
            throw ole;
        } catch (Exception e) {
            if (localTx && tx.isActive()) tx.rollback();
            throw e;
        }
    }
}

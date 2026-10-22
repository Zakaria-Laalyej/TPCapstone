# TP (Capstone) : Finaliser l'application "Réservation de salles" avec JPA & Hibernate

**Réalisé par :** Zakaria Laalyej (2DSI)

Projet de synthèse (Capstone) réalisé dans le cadre du module JEE / Persistance des données avec Hibernate et JPA.
Il rassemble l'ensemble des compétences et des architectures abordées dans le cursus :
- Conception d'un modèle relationnel complet et intègre avec JPA.
- Script et automatisation de la migration de schéma via JDBC (`MigrationExecutor`).
- Requêtes avancées : détection de conflits et disponibilité de salles en JPQL, recherche multi-critères dynamique avec l'API Criteria.
- Gestion des transactions et résolution des conflits concurrents par verrouillage optimiste (`@Version` / `OptimisticLockException`).
- Activation du cache de second niveau (L2) et analyse des performances avec les statistiques Hibernate.

---

## Objectifs du Capstone

1. **Modélisation métier robuste** :
   - Entités `Utilisateur`, `Salle`, `Reservation`.
   - Gestion stricte de l'intégrité référentielle, contraintes d'unicité et chargement `LAZY`.
2. **Migrations de base de données automatisées** :
   - Exécution de scripts DDL au démarrage (`v2_0_add_description.sql`).
3. **Moteur de réservation intelligent** :
   - Algorithme de vérification de non-chevauchement de créneaux : `dateDebut < :fin AND dateFin > :debut`.
4. **Recherche multi-critères dynamique** :
   - Utilisation de `CriteriaBuilder`, `CriteriaQuery`, `Predicate` pour filtrer par nom de salle, date et statut.
5. **Gestion de la concurrence** :
   - Verrouillage optimiste transparent via l'annotation `@Version`.
6. **Optimisation des performances** :
   - Cache L2 activé sur les entités maîtresses et rapport synthétique `PerformanceReport`.

---

## Technologies

- **Java 17 / 21**
- **Maven**
- **Hibernate 5.6.15.Final** (JPA 2.2, `javax.persistence`)
- **H2 Database** (Mode en mémoire `jdbc:h2:mem:reservationdb`)
- **JUnit 4.13.2**
- **Java Logging API** (`java.util.logging.Logger`)

---

## Diagramme des Entités

```
   ┌────────────────────────────────┐            ┌────────────────────────────────┐
   │          Utilisateur           │            │             Salle              │
   ├────────────────────────────────┤            ├────────────────────────────────┤
   │ - id : Long (PK)               │            │ - id : Long (PK)               │
   │ - nom : String                 │            │ - nom : String (UNIQUE)        │
   │ - email : String (UNIQUE)      │            │ - capacite : int               │
   │ - role : String                │            │ - description : String         │
   │ - version : int (@Version)     │            │ - version : int (@Version)     │
   │ - reservations : List<Res>     │            │ - reservations : List<Res>     │
   └───────────────┬────────────────┘            └────────────────┬───────────────┘
                   │ 1                                            │ 1
                   │                                              │
                   │ *                                            │ *
                   └──────────────────────┬───────────────────────┘
                                          │
                                          ▼
                         ┌─────────────────────────────────┐
                         │           Reservation           │
                         ├─────────────────────────────────┤
                         │ - id : Long (PK)                │
                         │ - salle : Salle (FK)            │
                         │ - utilisateur : Utilisateur(FK) │
                         │ - dateDebut : LocalDateTime     │
                         │ - dateFin : LocalDateTime       │
                         │ - statut : String               │
                         │ - version : int (@Version)      │
                         └─────────────────────────────────┘
```

---

## Fonctionnalités Clés et Extraits de Code

### 1. Migration de base de données (`MigrationExecutor.java`)
Exécution d'un script SQL de migration DDL directement via JDBC :
```sql
-- db/migration/v2_0_add_description.sql
CREATE INDEX IF NOT EXISTS idx_utilisateur_email ON utilisateurs(email);
CREATE INDEX IF NOT EXISTS idx_reservation_statut ON reservations(statut);
```

### 2. Algorithme de détection de chevauchement de réservation
Avant de valider une réservation, `ReservationService` s'assure qu'aucun créneau validé ne chevauche la période demandée :
```java
String jpqlConflit = "SELECT COUNT(r) FROM Reservation r " +
        "WHERE r.salle.id = :salleId " +
        "AND r.statut = 'CONFIRMEE' " +
        "AND r.dateDebut < :fin AND r.dateFin > :debut";
```

### 3. Recherche multicritères avec Criteria API
Construction dynamique de requêtes typées sans concaténation SQL :
```java
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<Reservation> cq = cb.createQuery(Reservation.class);
Root<Reservation> root = cq.from(Reservation.class);
List<Predicate> predicates = new ArrayList<>();

if (nomSalle != null) {
    predicates.add(cb.like(cb.lower(root.get("salle").get("nom")), "%" + nomSalle.toLowerCase() + "%"));
}
if (dateDebutApres != null) {
    predicates.add(cb.greaterThanOrEqualTo(root.get("dateDebut"), dateDebutApres));
}
cq.select(root).where(predicates.toArray(new Predicate[0]));
```

### 4. Concurrence & Verrouillage Optimiste (@Version)
Lorsqu'un conflit de mise à jour concurrente survient, Hibernate intercepte la désynchronisation du numéro de version :
```sql
UPDATE salles SET capacite=?, description=?, nom=?, version=1 WHERE id=1 AND version=0
```
Si le champ `version` en base a déjà été incrémenté par une autre transaction, `OptimisticLockException` est immédiatement levée et traitée.

---

## Rapport d'Exécution et Métriques (`MainApp.java`)

```text
==========================================================================
  TP Capstone : Application Complete de Reservation de Salles (JPA/JEE)
==========================================================================

--- [1] Execution de la migration SQL via JDBC ---
Migration appliquee avec succes !

--- [2] Peuplement du jeu de donnees realiste ---
[DataInitializer] Jeu de donnees initialise (3 utilisateurs, 4 salles, 2 reservations).

--- [3] Recherche JPQL des salles disponibles ---
Salles libres pour le creneau 2026-10-22T09:00 : 3
 -> Salle B (Toubkal) (Capacite: 50)
 -> Salle C (Majorelle) (Capacite: 10)
 -> Salle D (Oukaimeden) (Capacite: 30)

--- [4] Recherche multi-criteres (Criteria API) ---
Reservations trouvees avec critere 'Atlas' : 1
 -> Res #1 dans Salle A (Atlas) statut=CONFIRMEE

--- [5] Demonstration du verrouillage optimiste (@Version) ---
Version initiale Salle: 0
Client 1 commit valide. Nouvelle version: 1
SUCCES DU TEST : OptimisticLockException interceptée !
 -> Message : Conflit detecte, version modifiee par une autre transaction.

==================== RAPPORT DE PERFORMANCE ====================
Requetes executees                 : 2
Cache de second niveau (L2) Hits   : 1
Cache de second niveau (L2) Misses : 1
Cache de second niveau (L2) Puts   : 2
Transactions executees             : 3
Duree max requete (ms)             : 14
=================================================================

Application Capstone executee avec succes !
```

---

## Structure du Projet

```
tp-capstone-reservation-salles/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/example/
    │   │   ├── MainApp.java
    │   │   ├── cache/
    │   │   │   └── SimpleInMemoryRegionFactory.java
    │   │   ├── model/
    │   │   │   ├── Reservation.java
    │   │   │   ├── Salle.java
    │   │   │   └── Utilisateur.java
    │   │   ├── service/
    │   │   │   └── ReservationService.java
    │   │   └── util/
    │   │       ├── DataInitializer.java
    │   │       ├── MigrationExecutor.java
    │   │       └── PerformanceReport.java
    │   └── resources/
    │       ├── META-INF/persistence.xml
    │       ├── ehcache.xml
    │       └── db/migration/
    │           └── v2_0_add_description.sql
    └── test/java/com/example/test/
        └── ReservationTest.java
```

---

## Commandes d'exécution

### Lancer les tests fonctionnels et d'isolation JUnit
```bash
mvn test
```

### Compiler le projet
```bash
mvn clean compile
```

---

## Auteur

**Zakaria Laalyej**, 2DSI, 2026.

-- Migration version 2.0 : Optimisations des index de reservation
CREATE INDEX IF NOT EXISTS idx_utilisateur_email ON utilisateurs(email);
CREATE INDEX IF NOT EXISTS idx_reservation_statut ON reservations(statut);

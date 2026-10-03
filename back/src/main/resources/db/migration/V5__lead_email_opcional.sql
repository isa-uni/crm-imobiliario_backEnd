-- E-mail do lead deixa de ser único (e já era anulável): leads podem chegar só com telefone, e
-- duas pessoas podem compartilhar um e-mail. A constraint veio do "email VARCHAR(255) UNIQUE"
-- da V1, que no PostgreSQL recebe o nome padrão lead_email_key.
ALTER TABLE lead DROP CONSTRAINT IF EXISTS lead_email_key;
UPDATE lead SET email = NULL WHERE email = '';

ALTER TABLE lead ADD COLUMN IF NOT EXISTS equipe_id BIGINT REFERENCES equipe(id);
ALTER TABLE lead ADD COLUMN IF NOT EXISTS status_atribuicao VARCHAR(30) NOT NULL DEFAULT 'ATRIBUIDO';
CREATE INDEX IF NOT EXISTS idx_lead_equipe ON lead(equipe_id);
CREATE INDEX IF NOT EXISTS idx_lead_corretor ON lead(corretor_id);
CREATE INDEX IF NOT EXISTS idx_lead_status_atrib ON lead(status_atribuicao);
-- Backfill equipe_id via corretor
UPDATE lead l SET equipe_id = u.equipe_id FROM usuario u WHERE l.corretor_id = u.id AND l.equipe_id IS NULL;
-- Leads órfãos (sem corretor) ficam na Equipe Geral
UPDATE lead SET equipe_id = (SELECT id FROM equipe WHERE nome='Equipe Geral') WHERE equipe_id IS NULL;

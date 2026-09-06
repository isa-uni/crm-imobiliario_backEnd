CREATE TABLE IF NOT EXISTS lead_responsavel_historico (
    id BIGSERIAL PRIMARY KEY,
    lead_id BIGINT NOT NULL REFERENCES lead(id),
    corretor_id BIGINT REFERENCES usuario(id),
    equipe_id BIGINT REFERENCES equipe(id),
    gestor_id BIGINT REFERENCES usuario(id),
    data_inicio TIMESTAMP NOT NULL,
    data_fim TIMESTAMP,
    motivo VARCHAR(30) NOT NULL,
    usuario_responsavel_id BIGINT REFERENCES usuario(id),
    CONSTRAINT chk_motivo CHECK (motivo IN ('ATRIBUICAO_INICIAL','REDISTRIBUICAO','DESLIGAMENTO_CORRETOR','ALTERACAO_MANUAL','DESLIGAMENTO_GESTOR'))
);
CREATE INDEX IF NOT EXISTS idx_hist_lead_inicio ON lead_responsavel_historico(lead_id, data_inicio);
CREATE INDEX IF NOT EXISTS idx_hist_corretor ON lead_responsavel_historico(corretor_id);
-- Popula histórico inicial para leads já atribuídos
INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
SELECT l.id, l.corretor_id, l.equipe_id, u.gestor_id, COALESCE(l.data_criacao, NOW()), 'ATRIBUICAO_INICIAL', l.corretor_id
FROM lead l LEFT JOIN usuario u ON u.id = l.corretor_id
WHERE l.corretor_id IS NOT NULL
ON CONFLICT DO NOTHING;

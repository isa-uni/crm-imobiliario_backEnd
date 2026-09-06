-- Marca como AGUARDANDO leads cujo corretor já está inativo (migração segura)
UPDATE lead SET status_atribuicao='AGUARDANDO_REDISTRIBUICAO', corretor_id=NULL, corretor_responsavel=NULL
WHERE corretor_id IN (SELECT id FROM usuario WHERE ativo=false);
-- Fecha histórico aberto desses leads
UPDATE lead_responsavel_historico h SET data_fim = NOW(), motivo = 'DESLIGAMENTO_CORRETOR'
WHERE h.data_fim IS NULL AND h.lead_id IN (SELECT id FROM lead WHERE status_atribuicao='AGUARDANDO_REDISTRIBUICAO');
-- Cria registro de desligamento para rastreio
INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, data_inicio, motivo)
SELECT l.id, NULL, l.equipe_id, NOW(), 'DESLIGAMENTO_CORRETOR'
FROM lead l WHERE l.status_atribuicao='AGUARDANDO_REDISTRIBUICAO'
AND NOT EXISTS (SELECT 1 FROM lead_responsavel_historico h WHERE h.lead_id=l.id AND h.motivo='DESLIGAMENTO_CORRETOR' AND h.data_fim IS NULL);

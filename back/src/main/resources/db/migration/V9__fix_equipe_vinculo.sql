-- Corrige vínculo equipe <-> gestor <-> corretor <-> lead (bug report: inativar corretor mostra sem equipe)

-- 1) Garante que gestores com equipe via equipe.gestor_id tenham usuario.equipe_id preenchido
UPDATE usuario u SET equipe_id = e.id
FROM equipe e
WHERE e.gestor_id = u.id AND u.equipe_id IS DISTINCT FROM e.id;

-- 2) Corretores ativos cujo gestor já tem equipe, mas equipe_id diverge ou nulo -> sincroniza
UPDATE usuario u SET equipe_id = g.equipe_id
FROM usuario g
WHERE u.gestor_id = g.id
  AND g.equipe_id IS NOT NULL
  AND u.ativo = true
  AND u.equipe_id IS DISTINCT FROM g.equipe_id;

-- alternativa fallback: equipe via equipe.gestor_id quando g.equipe_id ainda nulo mas existe equipe.gestor_id = g.id
UPDATE usuario u SET equipe_id = e2.id
FROM usuario g JOIN equipe e2 ON e2.gestor_id = g.id
WHERE u.gestor_id = g.id
  AND u.ativo = true
  AND u.equipe_id IS DISTINCT FROM e2.id
  AND g.equipe_id IS NULL;

-- 3) Leads ATRIBUIDO com equipe divergente do corretor -> corrige
UPDATE lead l SET equipe_id = u.equipe_id
FROM usuario u
WHERE l.corretor_id = u.id
  AND l.status_atribuicao = 'ATRIBUIDO'
  AND u.equipe_id IS NOT NULL
  AND l.equipe_id IS DISTINCT FROM u.equipe_id;

-- 4) Leads AGUARDANDO sem equipe -> recupera do último histórico com equipe
UPDATE lead l SET equipe_id = sub.equipe_id
FROM (SELECT DISTINCT ON (h.lead_id) h.lead_id, h.equipe_id FROM lead_responsavel_historico h WHERE h.equipe_id IS NOT NULL ORDER BY h.lead_id, h.data_inicio DESC) AS sub
WHERE l.id = sub.lead_id AND l.status_atribuicao = 'AGUARDANDO_REDISTRIBUICAO' AND l.equipe_id IS NULL;

-- 5) Órfãos ainda nulos vão para Equipe Geral
UPDATE lead SET equipe_id = (SELECT id FROM equipe WHERE nome='Equipe Geral') WHERE equipe_id IS NULL;
UPDATE usuario SET equipe_id = (SELECT id FROM equipe WHERE nome='Equipe Geral') WHERE equipe_id IS NULL;

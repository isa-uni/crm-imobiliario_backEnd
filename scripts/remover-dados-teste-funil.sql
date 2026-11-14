-- Remove tudo que dados-teste-funil.sql criou.
BEGIN;
CREATE TEMP TABLE t_usu ON COMMIT DROP AS SELECT id FROM usuario WHERE email LIKE '%@teste.local';
CREATE TEMP TABLE t_lead ON COMMIT DROP AS SELECT id FROM lead WHERE nome LIKE '[TESTE] %' OR corretor_id IN (SELECT id FROM t_usu);
DELETE FROM tramitacao_status WHERE lead_id IN (SELECT id FROM t_lead) OR usuario_id IN (SELECT id FROM t_usu);
DELETE FROM lead_responsavel_historico WHERE lead_id IN (SELECT id FROM t_lead) OR corretor_id IN (SELECT id FROM t_usu)
  OR gestor_id IN (SELECT id FROM t_usu) OR usuario_responsavel_id IN (SELECT id FROM t_usu);
DELETE FROM notificacao WHERE usuario_id IN (SELECT id FROM t_usu) OR lead_id IN (SELECT id FROM t_lead);
DELETE FROM lead WHERE id IN (SELECT id FROM t_lead);
DELETE FROM meta WHERE usuario_id IN (SELECT id FROM t_usu);
DELETE FROM refresh_token WHERE usuario_id IN (SELECT id FROM t_usu);
UPDATE equipe SET gestor_id = NULL WHERE nome IN ('Equipe Teste A', 'Equipe Teste B');
UPDATE usuario SET gestor_id = NULL, equipe_id = NULL WHERE id IN (SELECT id FROM t_usu);
DELETE FROM usuario WHERE id IN (SELECT id FROM t_usu);
DELETE FROM equipe WHERE nome IN ('Equipe Teste A', 'Equipe Teste B');
COMMIT;

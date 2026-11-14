-- Remove o que dados-indicadores-demonstracao.sql criou: clientes marcados com observacao = 'demo-indicadores',
-- seu histórico, as metas mensais dos corretores de demonstração e as notificações ligadas a esses clientes.
-- Não altera a carteira de dados-carteira-equipe.sql, o administrador nem os empreendimentos.
BEGIN;
CREATE TEMP TABLE t_demo ON COMMIT DROP AS SELECT id FROM lead WHERE observacao = 'demo-indicadores';
DELETE FROM tramitacao_status WHERE lead_id IN (SELECT id FROM t_demo);
DELETE FROM lead_responsavel_historico WHERE lead_id IN (SELECT id FROM t_demo);
DELETE FROM notificacao WHERE lead_id IN (SELECT id FROM t_demo);
DELETE FROM meta WHERE usuario_id IN (SELECT id FROM usuario WHERE email IN (
  'juliana.prado@crm.com', 'thiago.alves@crm.com', 'camila.rocha@crm.com'))
  AND mes_referencia BETWEEN DATE '2026-08-01' AND DATE '2026-11-01';
DELETE FROM lead WHERE id IN (SELECT id FROM t_demo);
COMMIT;

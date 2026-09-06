-- Remove coluna de valor financeiro da meta; meta passa a ser apenas contratos
-- Gerado conforme solicitação: tire o valor da meta
ALTER TABLE meta DROP COLUMN IF EXISTS meta_valor;
-- Opcional: garante NOT NULL em contratos caso tenha sido criada com nullable
-- ALTER TABLE meta ALTER COLUMN meta_contratos SET NOT NULL;

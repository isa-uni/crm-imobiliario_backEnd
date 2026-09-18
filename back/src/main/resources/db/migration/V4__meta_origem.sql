ALTER TABLE meta DROP CONSTRAINT IF EXISTS meta_usuario_id_mes_referencia_key;
ALTER TABLE meta ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'CORRETOR';
ALTER TABLE meta ALTER COLUMN origem DROP DEFAULT;
ALTER TABLE meta ADD CONSTRAINT meta_usuario_mes_origem_key UNIQUE (usuario_id, mes_referencia, origem);

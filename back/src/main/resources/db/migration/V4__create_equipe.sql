CREATE TABLE IF NOT EXISTS equipe (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao TEXT,
    gestor_id BIGINT REFERENCES usuario(id),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_equipe_gestor ON equipe(gestor_id);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS equipe_id BIGINT REFERENCES equipe(id);
CREATE INDEX IF NOT EXISTS idx_usuario_equipe ON usuario(equipe_id);
-- Cria equipe para cada gestor existente
INSERT INTO equipe (nome, descricao, gestor_id, ativo)
SELECT DISTINCT 'Equipe ' || u_gestor.nome, 'Equipe do gestor ' || u_gestor.nome, u_gestor.id, true
FROM usuario u_gestor
WHERE u_gestor.id IN (SELECT DISTINCT gestor_id FROM usuario WHERE gestor_id IS NOT NULL)
ON CONFLICT (nome) DO NOTHING;
-- Atribui equipe aos usuários baseado no gestor_id
UPDATE usuario u SET equipe_id = e.id FROM equipe e WHERE u.gestor_id = e.gestor_id AND u.equipe_id IS NULL;
-- Usuários sem gestor (admins/gestores sem equipe) vão para equipe geral
INSERT INTO equipe (nome, descricao, ativo) VALUES ('Equipe Geral', 'Clientes sem equipe definida', true) ON CONFLICT (nome) DO NOTHING;
UPDATE usuario SET equipe_id = (SELECT id FROM equipe WHERE nome='Equipe Geral') WHERE equipe_id IS NULL;

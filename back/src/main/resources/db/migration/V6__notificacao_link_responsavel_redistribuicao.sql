-- Notificações com título curto e link para a tela onde está a ação esperada (ex.: /redistribuicao).
ALTER TABLE notificacao ADD COLUMN titulo VARCHAR(150);
ALTER TABLE notificacao ADD COLUMN link VARCHAR(255);

-- Quem responde pela redistribuição dos leads de um corretor inativado: o gestor dele ou, se ele não
-- tiver gestor, o administrador que fez a inativação. Garante que nenhum lead fique sem responsável.
ALTER TABLE lead ADD COLUMN responsavel_redistribuicao_id BIGINT REFERENCES usuario(id);

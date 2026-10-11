-- Proporções do funil usadas pelo Relógio de Vendas: quantos clientes em cada etapa equivalem a
-- 1 contrato da meta. Uma única linha (id = 1), com o padrão abaixo; o admin pode ajustar.
CREATE TABLE proporcao_funil (
    id BIGINT PRIMARY KEY,
    leads INTEGER NOT NULL,
    oportunidades INTEGER NOT NULL,
    visitas_agendadas INTEGER NOT NULL,
    visitas_realizadas INTEGER NOT NULL,
    pastas INTEGER NOT NULL,
    aprovados INTEGER NOT NULL,
    data_atualizacao TIMESTAMP,
    atualizado_por_id BIGINT REFERENCES usuario(id)
);

INSERT INTO proporcao_funil (id, leads, oportunidades, visitas_agendadas, visitas_realizadas, pastas, aprovados)
VALUES (1, 33, 20, 10, 8, 4, 2);

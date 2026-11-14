-- Dados fictícios para conferir o Relógio de Vendas e o Dashboard do Gestor.
-- Tudo que este script cria tem e-mail @teste.local ou nome começando com "[TESTE]".
-- Para remover: rode remover-dados-teste-funil.sql.
--
-- Senha de todos os usuários de teste: Teste@SyJU2l9
--   gestor.a@teste.local   (Equipe Teste A: Corretor Teste 1 e 2)
--   gestor.b@teste.local   (Equipe Teste B: Corretor Teste 3)
--   corretor1@teste.local, corretor2@teste.local, corretor3@teste.local
-- IDs de usuario, lead e tramitacao_status vêm das sequências do Hibernate (*_seq), como na aplicação.
-- Datas pensadas para outubro/2026 como mês atual.

BEGIN;

DO $$
DECLARE
  v_gestor BIGINT := (SELECT id FROM papel WHERE papel = 'gestor');
  v_corretor BIGINT := (SELECT id FROM papel WHERE papel = 'corretor');
  u RECORD; v_lead BIGINT; v_eq BIGINT;
BEGIN
  IF EXISTS (SELECT 1 FROM usuario WHERE email LIKE '%@teste.local') THEN
    RAISE EXCEPTION 'Os dados de teste já existem. Rode remover-dados-teste-funil.sql antes.';
  END IF;

  INSERT INTO equipe (nome, descricao, ativo) VALUES ('Equipe Teste A', 'Dados de teste do funil', TRUE);
  INSERT INTO equipe (nome, descricao, ativo) VALUES ('Equipe Teste B', 'Dados de teste do funil', TRUE);
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Gestor Teste A', 'TST-GA', 'gestor.a@teste.local', '$2a$10$LZ8n7DmHYxC35UcO9hfBTubORp83pD9kTG226Ouc3XIpwruaqPdam', '90000000001', 'O', '(43) 90000-0000', DATE '1990-01-01', DATE '2026-09-01', TRUE, FALSE,
            v_gestor, NULL, (SELECT id FROM equipe WHERE nome = 'Equipe Teste A'), 0);
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Gestor Teste B', 'TST-GB', 'gestor.b@teste.local', '$2a$10$LZ8n7DmHYxC35UcO9hfBTubORp83pD9kTG226Ouc3XIpwruaqPdam', '90000000002', 'O', '(43) 90000-0000', DATE '1990-01-01', DATE '2026-09-01', TRUE, FALSE,
            v_gestor, NULL, (SELECT id FROM equipe WHERE nome = 'Equipe Teste B'), 0);
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Corretor Teste 1', 'TST-C1', 'corretor1@teste.local', '$2a$10$LZ8n7DmHYxC35UcO9hfBTubORp83pD9kTG226Ouc3XIpwruaqPdam', '90000000011', 'O', '(43) 90000-0000', DATE '1990-01-01', DATE '2026-09-01', TRUE, FALSE,
            v_corretor, (SELECT id FROM usuario WHERE email = 'gestor.a@teste.local'), (SELECT id FROM equipe WHERE nome = 'Equipe Teste A'), 0);
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Corretor Teste 2', 'TST-C2', 'corretor2@teste.local', '$2a$10$LZ8n7DmHYxC35UcO9hfBTubORp83pD9kTG226Ouc3XIpwruaqPdam', '90000000012', 'O', '(43) 90000-0000', DATE '1990-01-01', DATE '2026-09-01', TRUE, FALSE,
            v_corretor, (SELECT id FROM usuario WHERE email = 'gestor.a@teste.local'), (SELECT id FROM equipe WHERE nome = 'Equipe Teste A'), 0);
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Corretor Teste 3', 'TST-C3', 'corretor3@teste.local', '$2a$10$LZ8n7DmHYxC35UcO9hfBTubORp83pD9kTG226Ouc3XIpwruaqPdam', '90000000013', 'O', '(43) 90000-0000', DATE '1990-01-01', DATE '2026-09-01', TRUE, FALSE,
            v_corretor, (SELECT id FROM usuario WHERE email = 'gestor.b@teste.local'), (SELECT id FROM equipe WHERE nome = 'Equipe Teste B'), 0);
  UPDATE equipe SET gestor_id = (SELECT id FROM usuario WHERE email = 'gestor.a@teste.local') WHERE nome = 'Equipe Teste A';
  UPDATE equipe SET gestor_id = (SELECT id FROM usuario WHERE email = 'gestor.b@teste.local') WHERE nome = 'Equipe Teste B';

  -- Ana: cadastro set, parada em Pasta
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Ana', '43900000000', 'espontaneo', 'novo', 'pasta', 300000, NULL, TRUE, TIMESTAMP '2026-09-05 09:00:00', TIMESTAMP '2026-09-20 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-09-05 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-09-05 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-09-06 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'oportunidade', 'visita-agendada', TIMESTAMP '2026-09-10 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-agendada', 'visita-realizada', TIMESTAMP '2026-09-15 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-realizada', 'pasta', TIMESTAMP '2026-09-20 10:00:00');

  -- Bruno: cadastro set, Aprovado em set, contrato em 05/out
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Bruno', '43900000000', 'digital_lead', 'novo', 'contrato', 400000, NULL, TRUE, TIMESTAMP '2026-09-08 09:00:00', TIMESTAMP '2026-10-05 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-09-08 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-09-08 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-09-09 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'oportunidade', 'visita-agendada', TIMESTAMP '2026-09-12 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-agendada', 'visita-realizada', TIMESTAMP '2026-09-16 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-realizada', 'pasta', TIMESTAMP '2026-09-19 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'pasta', 'aprovado', TIMESTAMP '2026-09-25 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'aprovado', 'contrato', TIMESTAMP '2026-10-05 10:00:00');

  -- Carla: cadastro set, contrato em 28/set (só conta em setembro)
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Carla', '43900000000', 'ganhe_mais', 'novo', 'contrato', 500000, NULL, TRUE, TIMESTAMP '2026-09-10 09:00:00', TIMESTAMP '2026-09-28 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-09-10 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-09-10 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-09-12 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'oportunidade', 'visita-agendada', TIMESTAMP '2026-09-15 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-agendada', 'visita-realizada', TIMESTAMP '2026-09-18 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-realizada', 'pasta', TIMESTAMP '2026-09-20 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'pasta', 'aprovado', TIMESTAMP '2026-09-24 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'aprovado', 'contrato', TIMESTAMP '2026-09-28 10:00:00');

  -- Diego: cadastro set, descartado como Lead em 15/set (só conta em setembro)
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Diego', '43900000000', 'acao_externa', 'novo', 'descarte', 150000, 'Sem interesse', FALSE, TIMESTAMP '2026-09-12 09:00:00', TIMESTAMP '2026-09-15 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-09-12 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-09-12 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'descarte', TIMESTAMP '2026-09-15 10:00:00');

  -- Hugo: descartado em 25/set e voltou em 02/out: descarte conta em set, ativo em out
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Hugo', '43900000000', 'espontaneo', 'novo', 'oportunidade', 200000, NULL, TRUE, TIMESTAMP '2026-09-20 09:00:00', TIMESTAMP '2026-10-02 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-09-20 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-09-20 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'descarte', TIMESTAMP '2026-09-25 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'descarte', 'oportunidade', TIMESTAMP '2026-10-02 10:00:00');

  -- Elisa: cadastro out, Visita agendada
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Elisa', '43900000000', 'digital_lead', 'novo', 'visita-agendada', 250000, NULL, TRUE, TIMESTAMP '2026-10-02 09:00:00', TIMESTAMP '2026-10-06 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-02 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-02 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-10-03 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'oportunidade', 'visita-agendada', TIMESTAMP '2026-10-06 10:00:00');

  -- Fabio: cadastro out, descartado em Oportunidade em 07/out
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Fabio', '43900000000', 'captacao_gerente', 'novo', 'descarte', 200000, 'Sem interesse', FALSE, TIMESTAMP '2026-10-03 09:00:00', TIMESTAMP '2026-10-07 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-03 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-03 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-10-04 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'oportunidade', 'descarte', TIMESTAMP '2026-10-07 10:00:00');

  -- Gabi: cadastro out, só Lead
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Gabi', '43900000000', 'espontaneo', 'novo', 'lead', 180000, NULL, TRUE, TIMESTAMP '2026-10-06 09:00:00', TIMESTAMP '2026-10-06 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-06 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-06 10:00:00');

  -- Iris: descartada e voltou no mesmo mês (out): descarte não conta
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Iris', '43900000000', 'ganhe_mais', 'novo', 'lead', 190000, NULL, TRUE, TIMESTAMP '2026-10-04 09:00:00', TIMESTAMP '2026-10-08 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-04 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-04 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'descarte', TIMESTAMP '2026-10-05 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'descarte', 'lead', TIMESTAMP '2026-10-08 10:00:00');

  -- Joao: cadastro out, pulou direto de Lead para Pasta
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor1@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Joao', '43900000000', 'digital_lead', 'novo', 'pasta', 350000, NULL, TRUE, TIMESTAMP '2026-10-01 09:00:00', TIMESTAMP '2026-10-09 10:00:00', 'Corretor Teste 1', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-01 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-01 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'pasta', TIMESTAMP '2026-10-09 10:00:00');

  -- Karina: cadastro set, Visita agendada em set, Visita realizada em 03/out
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor2@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Karina', '43900000000', 'acao_externa', 'novo', 'visita-realizada', 280000, NULL, TRUE, TIMESTAMP '2026-09-15 09:00:00', TIMESTAMP '2026-10-03 10:00:00', 'Corretor Teste 2', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-09-15 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-09-15 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-09-16 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'oportunidade', 'visita-agendada', TIMESTAMP '2026-09-22 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-agendada', 'visita-realizada', TIMESTAMP '2026-10-03 10:00:00');

  -- Lucas: cadastro out, contrato em 09/out
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor2@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Lucas', '43900000000', 'espontaneo', 'novo', 'contrato', 450000, NULL, TRUE, TIMESTAMP '2026-10-01 09:00:00', TIMESTAMP '2026-10-09 10:00:00', 'Corretor Teste 2', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-01 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-01 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-10-02 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'oportunidade', 'visita-agendada', TIMESTAMP '2026-10-03 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-agendada', 'visita-realizada', TIMESTAMP '2026-10-05 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-realizada', 'pasta', TIMESTAMP '2026-10-06 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'pasta', 'aprovado', TIMESTAMP '2026-10-08 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'aprovado', 'contrato', TIMESTAMP '2026-10-09 10:00:00');

  -- Marina: cadastro out, só Lead
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor2@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Marina', '43900000000', 'digital_lead', 'novo', 'lead', 150000, NULL, TRUE, TIMESTAMP '2026-10-09 09:00:00', TIMESTAMP '2026-10-09 10:00:00', 'Corretor Teste 2', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-09 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-09 10:00:00');

  -- Nina: equipe B: Oportunidade
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor3@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Nina', '43900000000', 'espontaneo', 'novo', 'oportunidade', 220000, NULL, TRUE, TIMESTAMP '2026-10-02 09:00:00', TIMESTAMP '2026-10-05 10:00:00', 'Corretor Teste 3', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-02 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-02 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-10-05 10:00:00');

  -- Otto: equipe B: contrato em 10/out
  SELECT id, equipe_id INTO u FROM usuario WHERE email = 'corretor3@teste.local';
  INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo, data_criacao, data_atualizacao,
                    corretor_responsavel, corretor_id, equipe_id, status_atribuicao)
    VALUES (nextval('lead_seq'), '[TESTE] Otto', '43900000000', 'ganhe_mais', 'novo', 'contrato', 600000, NULL, TRUE, TIMESTAMP '2026-10-03 09:00:00', TIMESTAMP '2026-10-10 10:00:00', 'Corretor Teste 3', u.id, u.equipe_id, 'ATRIBUIDO') RETURNING id INTO v_lead;
  INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
    VALUES (v_lead, u.id, u.equipe_id, (SELECT gestor_id FROM usuario WHERE id = u.id), TIMESTAMP '2026-10-03 09:00:00', 'ATRIBUICAO_INICIAL', u.id);
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, NULL, 'lead', TIMESTAMP '2026-10-03 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'lead', 'oportunidade', TIMESTAMP '2026-10-04 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'oportunidade', 'visita-agendada', TIMESTAMP '2026-10-05 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-agendada', 'visita-realizada', TIMESTAMP '2026-10-06 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'visita-realizada', 'pasta', TIMESTAMP '2026-10-07 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'pasta', 'aprovado', TIMESTAMP '2026-10-08 10:00:00');
  INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao) VALUES (nextval('tramitacao_status_seq'), v_lead, u.id, 'aprovado', 'contrato', TIMESTAMP '2026-10-10 10:00:00');

END $$;

COMMIT;

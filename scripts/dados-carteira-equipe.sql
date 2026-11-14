-- Equipe de demonstração: 1 gestor, 1 equipe, 3 corretores e a carteira de clientes (leads)
-- de cada corretor, com histórico de funil (tramitacao_status) e de atribuição.
-- Empreendimentos usados: London Life, Felicce e Solare Fiore.
--
-- Senha de todos os usuários abaixo: Crm@Demo2026 (sem troca obrigatória no primeiro acesso)
--   rafael.mendes@crm.com   (gestor da Equipe Londrina Norte)
--   juliana.prado@crm.com, thiago.alves@crm.com, camila.rocha@crm.com   (corretores)
--
-- Para remover: rode remover-dados-carteira-equipe.sql.
-- Rodar (com UTF-8 no cliente):  PGCLIENTENCODING=UTF8 psql -h localhost -U postgres -d crm_corretora -f dados-carteira-equipe.sql

BEGIN;

DO $$
DECLARE
  v_papel_gestor   BIGINT := (SELECT id FROM papel WHERE papel = 'gestor');
  v_papel_corretor BIGINT := (SELECT id FROM papel WHERE papel = 'corretor');
  v_hash TEXT := '$2a$10$eTxmrkNVXF2wYW7tfrAtbu1cRVi/lUz6JXdjbbG7QlJkNR8OMDUDa';  -- Crm@Demo2026
  v_gestor BIGINT;
  v_equipe BIGINT;
  v_corretor BIGINT;
  v_emp BIGINT;
  v_lead BIGINT;
  r RECORD;
  v_data TIMESTAMP;
  v_ultima TIMESTAMP;
  v_anterior TEXT;
  i INT;
BEGIN
  IF EXISTS (SELECT 1 FROM usuario WHERE email IN ('rafael.mendes@crm.com', 'juliana.prado@crm.com',
                                                  'thiago.alves@crm.com', 'camila.rocha@crm.com')) THEN
    RAISE EXCEPTION 'A equipe de demonstração já existe. Rode remover-dados-carteira-equipe.sql antes.';
  END IF;

  INSERT INTO equipe (nome, descricao, ativo, data_criacao, data_atualizacao)
    VALUES ('Equipe Londrina Norte', 'Equipe de demonstração', TRUE, TIMESTAMP '2026-01-05 09:00:00', TIMESTAMP '2026-01-05 09:00:00');
  SELECT id INTO v_equipe FROM equipe WHERE nome = 'Equipe Londrina Norte';

  -- Gestor
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Rafael Mendes', '20260101', 'rafael.mendes@crm.com', v_hash, '30982778155', 'M', '43991000001',
            DATE '1985-03-12', DATE '2026-01-05', TRUE, FALSE, v_papel_gestor, NULL, v_equipe, 0);
  SELECT id INTO v_gestor FROM usuario WHERE email = 'rafael.mendes@crm.com';
  UPDATE equipe SET gestor_id = v_gestor WHERE id = v_equipe;

  -- Corretores
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Juliana Prado', '20260102', 'juliana.prado@crm.com', v_hash, '40229418554', 'F', '43991000002',
            DATE '1992-07-21', DATE '2026-01-12', TRUE, FALSE, v_papel_corretor, v_gestor, v_equipe, 0);
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Thiago Alves', '20260103', 'thiago.alves@crm.com', v_hash, '22377553702', 'M', '43991000003',
            DATE '1990-11-02', DATE '2026-01-12', TRUE, FALSE, v_papel_corretor, v_gestor, v_equipe, 0);
  INSERT INTO usuario (id, nome, matricula, email, senha, cpf, genero, telefone, data_nascimento, data_matricula, ativo, trocar_senha, papel_id, gestor_id, equipe_id, token_version)
    VALUES (nextval('usuario_seq'), 'Camila Rocha', '20260104', 'camila.rocha@crm.com', v_hash, '17669968923', 'F', '43991000004',
            DATE '1996-04-18', DATE '2026-02-02', TRUE, FALSE, v_papel_corretor, v_gestor, v_equipe, 0);

  -- Carteira de clientes. Cada linha: cliente, telefone, origem, historico, empreendimento (slug),
  -- corretor (e-mail), data de criação, valor de interesse, motivo do descarte (só se descartado),
  -- caminho do funil (a última etapa vira o status atual do lead).
  FOR r IN
    SELECT * FROM (VALUES
      ('Patrícia Lopes',     '43991100001', 'digital_lead',      'novo',       'london-life',   'juliana.prado@crm.com', TIMESTAMP '2026-08-03 09:00:00', 255000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada','pasta','aprovado','contrato']::text[]),
      ('Eduardo Faria',      '43991100002', 'espontaneo',        'novo',       'felicce',       'juliana.prado@crm.com', TIMESTAMP '2026-08-10 09:00:00', 232000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada','pasta','aprovado']::text[]),
      ('Marcela Dias',       '43991100003', 'ganhe_mais',        'reaquecido', 'solare-fiore',  'juliana.prado@crm.com', TIMESTAMP '2026-08-18 09:00:00', 220000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada','pasta']::text[]),
      ('Renato Souza',       '43991100004', 'captacao_corretor', 'novo',       'london-life',   'juliana.prado@crm.com', TIMESTAMP '2026-09-01 09:00:00', 251000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada']::text[]),
      ('Fernanda Costa',     '43991100005', 'digital_lead',      'novo',       'felicce',       'juliana.prado@crm.com', TIMESTAMP '2026-09-14 09:00:00', 229000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada']::text[]),
      ('Gustavo Lima',       '43991100006', 'oferta_ativa',      'vencido',    'solare-fiore',  'juliana.prado@crm.com', TIMESTAMP '2026-09-25 09:00:00', 218000::bigint, NULL::text,
         ARRAY['lead','oportunidade']::text[]),
      ('Simone Araújo',      '43991100007', 'espontaneo',        'novo',       'london-life',   'juliana.prado@crm.com', TIMESTAMP '2026-08-27 09:00:00', 247000::bigint, 'Desistiu da compra por enquanto',
         ARRAY['lead','oportunidade','descarte']::text[]),
      ('Bianca Moreira',     '43991100008', 'digital_lead',      'novo',       'felicce',       'juliana.prado@crm.com', TIMESTAMP '2026-10-28 09:00:00', 230000::bigint, NULL::text,
         ARRAY['lead']::text[]),

      ('Ricardo Nunes',      '43991100009', 'espontaneo',        'novo',       'solare-fiore',  'thiago.alves@crm.com',  TIMESTAMP '2026-08-05 09:00:00', 222000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada','pasta','aprovado','contrato']::text[]),
      ('Larissa Moraes',     '43991100010', 'ganhe_mais',        'novo',       'felicce',       'thiago.alves@crm.com',  TIMESTAMP '2026-08-22 09:00:00', 231000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada','pasta','aprovado','contrato']::text[]),
      ('Hélio Barbosa',      '43991100011', 'captacao_gerente',  'novo',       'london-life',   'thiago.alves@crm.com',  TIMESTAMP '2026-09-03 09:00:00', 249000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada','pasta','aprovado']::text[]),
      ('Tatiane Brito',      '43991100012', 'digital_lead',      'reaquecido', 'solare-fiore',  'thiago.alves@crm.com',  TIMESTAMP '2026-09-09 09:00:00', 219000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada']::text[]),
      ('Anderson Pires',     '43991100013', 'espontaneo',        'novo',       'felicce',       'thiago.alves@crm.com',  TIMESTAMP '2026-09-21 09:00:00', 228000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada']::text[]),
      ('Vanessa Coelho',     '43991100014', 'oferta_ativa',      'vencido',    'london-life',   'thiago.alves@crm.com',  TIMESTAMP '2026-09-07 09:00:00', 245000::bigint, 'Não atendeu os contatos de retorno',
         ARRAY['lead','oportunidade','visita-agendada','descarte']::text[]),
      ('Leonardo Teixeira',  '43991100015', 'digital_lead',      'novo',       'solare-fiore',  'thiago.alves@crm.com',  TIMESTAMP '2026-10-05 09:00:00', 224000::bigint, NULL::text,
         ARRAY['lead','oportunidade']::text[]),

      ('Débora Fonseca',     '43991100016', 'espontaneo',        'novo',       'london-life',   'camila.rocha@crm.com',  TIMESTAMP '2026-08-12 09:00:00', 254000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada','pasta','aprovado','contrato']::text[]),
      ('Maurício Campos',    '43991100017', 'captacao_corretor', 'novo',       'felicce',       'camila.rocha@crm.com',  TIMESTAMP '2026-08-28 09:00:00', 233000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada','pasta']::text[]),
      ('Carla Mendonça',     '43991100018', 'digital_lead',      'novo',       'solare-fiore',  'camila.rocha@crm.com',  TIMESTAMP '2026-09-16 09:00:00', 217000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada','visita-realizada']::text[]),
      ('Paulo Henrique Assis','43991100019','ganhe_mais',        'reaquecido', 'london-life',   'camila.rocha@crm.com',  TIMESTAMP '2026-09-29 09:00:00', 252000::bigint, NULL::text,
         ARRAY['lead','oportunidade','visita-agendada']::text[]),
      ('Luciana Torres',     '43991100020', 'espontaneo',        'novo',       'felicce',       'camila.rocha@crm.com',  TIMESTAMP '2026-10-02 09:00:00', 230500::bigint, NULL::text,
         ARRAY['lead','oportunidade']::text[]),
      ('Rodrigo Esteves',    '43991100021', 'oferta_ativa',      'novo',       'solare-fiore',  'camila.rocha@crm.com',  TIMESTAMP '2026-08-20 09:00:00', 221000::bigint, 'Comprou em outro empreendimento',
         ARRAY['lead','descarte']::text[]),
      ('Aline Cardoso',      '43991100022', 'digital_lead',      'novo',       'london-life',   'camila.rocha@crm.com',  TIMESTAMP '2026-10-30 09:00:00', 248000::bigint, NULL::text,
         ARRAY['lead']::text[])
    ) AS t(nome, telefone, origem, historico, emp_slug, corretor_email, data_criacao, valor, motivo, caminho)
  LOOP
    SELECT id INTO v_emp FROM empreendimento WHERE slug = r.emp_slug;
    SELECT id INTO v_corretor FROM usuario WHERE email = r.corretor_email;

    INSERT INTO lead (id, nome, telefone, origem, historico, status, valor_interesse, motivo_descarte, ativo,
                      data_criacao, data_atualizacao, corretor_responsavel, corretor_id, equipe_id, status_atribuicao, empreendimento_id)
      VALUES (nextval('lead_seq'), r.nome, r.telefone, r.origem, r.historico, r.caminho[array_upper(r.caminho, 1)], r.valor, r.motivo, TRUE,
              r.data_criacao, r.data_criacao, (SELECT nome FROM usuario WHERE id = v_corretor), v_corretor, v_equipe, 'ATRIBUIDO', v_emp)
      RETURNING id INTO v_lead;

    INSERT INTO lead_responsavel_historico (lead_id, corretor_id, equipe_id, gestor_id, data_inicio, motivo, usuario_responsavel_id)
      VALUES (v_lead, v_corretor, v_equipe, v_gestor, r.data_criacao, 'ATRIBUICAO_INICIAL', v_corretor);

    -- uma movimentação a cada 3 dias a partir da criação
    v_data := r.data_criacao;
    v_anterior := NULL;
    FOR i IN 1..array_length(r.caminho, 1) LOOP
      INSERT INTO tramitacao_status (id, lead_id, usuario_id, status_anterior, status_atual, data_movimentacao)
        VALUES (nextval('tramitacao_status_seq'), v_lead, v_corretor, v_anterior, r.caminho[i], v_data);
      v_anterior := r.caminho[i];
      v_ultima := v_data;
      v_data := v_data + INTERVAL '3 days';
    END LOOP;

    UPDATE lead SET data_atualizacao = v_ultima WHERE id = v_lead;
  END LOOP;
END $$;

COMMIT;

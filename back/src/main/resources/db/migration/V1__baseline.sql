-- V1 baseline consolidado: todas as tabelas gerenciadas por @Entity + junção empreendimento_extracao_documento
-- Substitui schema anteriormente criado via ddl-auto=create-drop. Reset de dev exigido.

-- Papel
CREATE TABLE IF NOT EXISTS papel (
    id BIGSERIAL PRIMARY KEY,
    papel VARCHAR(255) UNIQUE,
    ativo BOOLEAN
);

-- Usuario (sem FK equipe/gestor/papel inline para evitar ciclo; adicionadas via ALTER)
CREATE TABLE IF NOT EXISTS usuario (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    matricula VARCHAR(255) UNIQUE,
    email VARCHAR(255) UNIQUE,
    senha VARCHAR(255),
    cpf VARCHAR(255) UNIQUE,
    genero VARCHAR(255),
    telefone VARCHAR(255),
    data_nascimento DATE,
    data_matricula DATE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    trocar_senha BOOLEAN NOT NULL DEFAULT TRUE,
    papel_id BIGINT,
    gestor_id BIGINT,
    equipe_id BIGINT,
    token_version INT
);

-- Equipe
CREATE TABLE IF NOT EXISTS equipe (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao TEXT,
    gestor_id BIGINT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_equipe_gestor ON equipe(gestor_id);

-- FKs usuario/equipe (circulares) — adicionadas após ambas existirem
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_papel FOREIGN KEY (papel_id) REFERENCES papel(id);
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_gestor FOREIGN KEY (gestor_id) REFERENCES usuario(id);
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_equipe FOREIGN KEY (equipe_id) REFERENCES equipe(id);
ALTER TABLE equipe ADD CONSTRAINT fk_equipe_gestor FOREIGN KEY (gestor_id) REFERENCES usuario(id);
CREATE INDEX IF NOT EXISTS idx_usuario_equipe ON usuario(equipe_id);

-- Imovel
CREATE TABLE IF NOT EXISTS imovel (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(255),
    status VARCHAR(255),
    endereco VARCHAR(255),
    bairro VARCHAR(255),
    cidade VARCHAR(255),
    valor_venda BIGINT,
    area BIGINT,
    quartos BIGINT,
    banheiros BIGINT,
    vagas BIGINT,
    descricao VARCHAR(255),
    ativo BOOLEAN,
    data_cadastro TIMESTAMP,
    data_atualizacao TIMESTAMP
);

-- Lead
CREATE TABLE IF NOT EXISTS lead (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    telefone VARCHAR(255),
    origem VARCHAR(255),
    historico VARCHAR(255),
    status VARCHAR(255),
    valor_interesse BIGINT,
    observacao VARCHAR(255),
    motivo_descarte VARCHAR(255),
    ativo BOOLEAN,
    data_criacao TIMESTAMP,
    data_atualizacao TIMESTAMP,
    corretor_responsavel VARCHAR(255),
    corretor_id BIGINT REFERENCES usuario(id),
    equipe_id BIGINT REFERENCES equipe(id),
    status_atribuicao VARCHAR(30) NOT NULL DEFAULT 'ATRIBUIDO',
    imovel_id BIGINT REFERENCES imovel(id)
);
CREATE INDEX IF NOT EXISTS idx_lead_equipe ON lead(equipe_id);
CREATE INDEX IF NOT EXISTS idx_lead_corretor ON lead(corretor_id);
CREATE INDEX IF NOT EXISTS idx_lead_status_atrib ON lead(status_atribuicao);

-- Meta
CREATE TABLE IF NOT EXISTS meta (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    mes_referencia DATE NOT NULL,
    meta_contratos INT NOT NULL,
    data_criacao TIMESTAMP,
    data_atualizacao TIMESTAMP,
    UNIQUE (usuario_id, mes_referencia)
);

-- Tramitacao
CREATE TABLE IF NOT EXISTS tramitacao_status (
    id BIGSERIAL PRIMARY KEY,
    lead_id BIGINT REFERENCES lead(id),
    usuario_id BIGINT REFERENCES usuario(id),
    status_anterior VARCHAR(255),
    status_atual VARCHAR(255),
    data_movimentacao TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tramitacao_lead_data ON tramitacao_status(lead_id, data_movimentacao);

-- Lead responsavel historico
CREATE TABLE IF NOT EXISTS lead_responsavel_historico (
    id BIGSERIAL PRIMARY KEY,
    lead_id BIGINT NOT NULL REFERENCES lead(id),
    corretor_id BIGINT REFERENCES usuario(id),
    equipe_id BIGINT REFERENCES equipe(id),
    gestor_id BIGINT REFERENCES usuario(id),
    data_inicio TIMESTAMP NOT NULL,
    data_fim TIMESTAMP,
    motivo VARCHAR(30) NOT NULL CHECK (motivo IN ('ATRIBUICAO_INICIAL','REDISTRIBUICAO','DESLIGAMENTO_CORRETOR','ALTERACAO_MANUAL','DESLIGAMENTO_GESTOR')),
    usuario_responsavel_id BIGINT REFERENCES usuario(id)
);
CREATE INDEX IF NOT EXISTS idx_hist_lead_inicio ON lead_responsavel_historico(lead_id, data_inicio);
CREATE INDEX IF NOT EXISTS idx_hist_corretor ON lead_responsavel_historico(corretor_id);

-- Notificacao
CREATE TABLE IF NOT EXISTS notificacao (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(30) NOT NULL,
    mensagem TEXT NOT NULL,
    lead_id BIGINT,
    lead_nome VARCHAR(255),
    lida BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notif_usuario ON notificacao(usuario_id, lida);

-- Refresh token / blacklist
CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    jti VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS token_blacklist (
    id BIGSERIAL PRIMARY KEY,
    jti VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL
);

-- Sincronizacao
CREATE TABLE IF NOT EXISTS sincronizacao (
    id BIGSERIAL PRIMARY KEY,
    inicio TIMESTAMP,
    fim TIMESTAMP,
    total INT,
    sucesso INT,
    falha INT,
    disponiveis INT,
    erro TEXT,
    tipo VARCHAR(255),
    codigo_crm_alvo VARCHAR(255)
);

-- Empreendimento (consolidado com V11)
CREATE TABLE IF NOT EXISTS empreendimento (
    id BIGSERIAL PRIMARY KEY,
    codigo_crm VARCHAR(255) UNIQUE,
    nome VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    codigo_externo VARCHAR(100),
    descricao_curta TEXT,
    descricao_completa TEXT,
    incorporadora VARCHAR(255),
    construtora VARCHAR(255),
    cidade VARCHAR(255),
    uf VARCHAR(255),
    regiao VARCHAR(255),
    bairro VARCHAR(255),
    endereco VARCHAR(255),
    numero VARCHAR(20),
    complemento VARCHAR(255),
    cep VARCHAR(9),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    zoom INT,
    andamento DOUBLE PRECISION,
    total INT,
    disponiveis INT,
    reservadas INT,
    vendidas INT,
    em_processo INT,
    metragem_min DOUBLE PRECISION,
    metragem_max DOUBLE PRECISION,
    preco_min BIGINT,
    preco_max BIGINT,
    tipos_json TEXT,
    quartos_json TEXT,
    status VARCHAR(255),
    tabela_referencia VARCHAR(255),
    tabela_validade VARCHAR(255),
    tabela_hash VARCHAR(255),
    imagem_url TEXT,
    descricao_resumo TEXT,
    condicoes_comerciais TEXT,
    previsao_entrega VARCHAR(255),
    entrega_contratual VARCHAR(255),
    parcelamento_max INT,
    enriquecido BOOLEAN,
    ativo BOOLEAN,
    unmatched_reason TEXT,
    ultima_sincronizacao TIMESTAMP,
    criado_por BIGINT,
    atualizado_por BIGINT,
    data_cadastro TIMESTAMP,
    data_atualizacao TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_emp_slug ON empreendimento(slug);
CREATE INDEX IF NOT EXISTS idx_emp_disponiveis ON empreendimento(disponiveis);
CREATE INDEX IF NOT EXISTS idx_emp_cidade ON empreendimento(cidade);
CREATE UNIQUE INDEX IF NOT EXISTS idx_emp_codigo_ext ON empreendimento(codigo_externo);
CREATE INDEX IF NOT EXISTS idx_emp_status ON empreendimento(status);
CREATE INDEX IF NOT EXISTS idx_emp_ativo ON empreendimento(ativo);

-- Empreendimento Documento / Extracao / Fonte / Junção (V10) — antes de unidade para FK
CREATE TABLE IF NOT EXISTS empreendimento_documento (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT REFERENCES empreendimento(id) ON DELETE SET NULL,
    nome_original VARCHAR(255) NOT NULL,
    nome_armazenado VARCHAR(255) NOT NULL,
    tipo VARCHAR(10),
    tamanho BIGINT,
    caminho VARCHAR(500) NOT NULL,
    hash VARCHAR(64) NOT NULL,
    mime VARCHAR(100),
    data_upload TIMESTAMP NOT NULL DEFAULT NOW(),
    usuario_upload BIGINT REFERENCES usuario(id),
    status_processamento VARCHAR(20) NOT NULL DEFAULT 'pendente' CHECK (status_processamento IN ('pendente','processando','concluido','erro','revisao'))
);
CREATE INDEX IF NOT EXISTS idx_doc_emp ON empreendimento_documento(empreendimento_id);
CREATE INDEX IF NOT EXISTS idx_doc_hash ON empreendimento_documento(hash);
CREATE INDEX IF NOT EXISTS idx_doc_status ON empreendimento_documento(status_processamento);

CREATE TABLE IF NOT EXISTS empreendimento_extracao (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT REFERENCES empreendimento(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pendente' CHECK (status IN ('pendente','processando','concluido','erro','revisao')),
    modelo_ia VARCHAR(50),
    data_processamento TIMESTAMP,
    data_conclusao TIMESTAMP,
    erro TEXT,
    resultado TEXT,
    usuario BIGINT REFERENCES usuario(id),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_extracao_emp ON empreendimento_extracao(empreendimento_id);
CREATE INDEX IF NOT EXISTS idx_extracao_status ON empreendimento_extracao(status);

CREATE TABLE IF NOT EXISTS empreendimento_extracao_documento (
    extracao_id BIGINT NOT NULL REFERENCES empreendimento_extracao(id) ON DELETE CASCADE,
    documento_id BIGINT NOT NULL REFERENCES empreendimento_documento(id) ON DELETE CASCADE,
    PRIMARY KEY (extracao_id, documento_id)
);

CREATE TABLE IF NOT EXISTS empreendimento_fonte (
    id BIGSERIAL PRIMARY KEY,
    extracao_id BIGINT REFERENCES empreendimento_extracao(id) ON DELETE CASCADE,
    documento_id BIGINT REFERENCES empreendimento_documento(id) ON DELETE SET NULL,
    empreendimento_id BIGINT REFERENCES empreendimento(id) ON DELETE SET NULL,
    campo VARCHAR(100) NOT NULL,
    valor_extraido TEXT,
    pagina INT,
    trecho TEXT,
    confianca INT CHECK (confianca >= 0 AND confianca <= 100),
    documento_nome VARCHAR(255),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_fonte_extracao ON empreendimento_fonte(extracao_id);
CREATE INDEX IF NOT EXISTS idx_fonte_campo ON empreendimento_fonte(campo);
CREATE INDEX IF NOT EXISTS idx_fonte_emp_campo ON empreendimento_fonte(empreendimento_id, campo);

-- Unidade (consolidado com V12) — após documento para FK
CREATE TABLE IF NOT EXISTS unidade (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT,
    nome_unidade VARCHAR(255),
    id_unidade_crm VARCHAR(255),
    bloco VARCHAR(255),
    etapa VARCHAR(255),
    andar INT,
    coluna INT,
    posicao VARCHAR(255),
    area_privativa DOUBLE PRECISION,
    area_comum DOUBLE PRECISION,
    outras_areas DOUBLE PRECISION,
    tipologia VARCHAR(255),
    situacao VARCHAR(255),
    situacao_nome VARCHAR(255),
    preco BIGINT,
    preco_hash TEXT,
    garagem VARCHAR(50),
    ato BIGINT,
    subsidio_cohapar BIGINT,
    financiamento BIGINT,
    valor_avaliacao BIGINT,
    observacoes TEXT,
    documento_origem_id BIGINT REFERENCES empreendimento_documento(id) ON DELETE SET NULL,
    linha_origem INT,
    status_validacao VARCHAR(20) NOT NULL DEFAULT 'confirmado' CHECK (status_validacao IN ('confirmado','revisao')),
    ultima_sincronizacao TIMESTAMP,
    data_cadastro TIMESTAMP,
    data_atualizacao TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_unid_emp ON unidade(empreendimento_id);
CREATE INDEX IF NOT EXISTS idx_unid_situacao ON unidade(situacao);
CREATE INDEX IF NOT EXISTS idx_unid_nome ON unidade(nome_unidade);

CREATE TABLE IF NOT EXISTS unidade_historico (
    id BIGSERIAL PRIMARY KEY,
    unidade_id BIGINT NOT NULL REFERENCES unidade(id) ON DELETE CASCADE,
    campo VARCHAR(50) NOT NULL,
    valor_anterior TEXT,
    valor_novo TEXT,
    usuario BIGINT,
    documento_origem_id BIGINT REFERENCES empreendimento_documento(id) ON DELETE SET NULL,
    origem VARCHAR(20) NOT NULL DEFAULT 'usuario' CHECK (origem IN ('extracao','usuario')),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_unid_hist_unid_campo ON unidade_historico(unidade_id, campo);

-- Empreendimento normalizado (V11) - Double -> DOUBLE PRECISION para compatibilidade Hibernate validate/H2
CREATE TABLE IF NOT EXISTS empreendimento_caracteristica (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL UNIQUE REFERENCES empreendimento(id) ON DELETE CASCADE,
    metragem_min DOUBLE PRECISION,
    metragem_max DOUBLE PRECISION,
    quartos_min INT,
    quartos_max INT,
    suites_min INT,
    suites_max INT,
    banheiros_min INT,
    banheiros_max INT,
    vagas_min INT,
    vagas_max INT,
    pavimentos INT,
    unidades_por_andar INT,
    qtd_torres INT,
    possui_elevador BOOLEAN
);

CREATE TABLE IF NOT EXISTS empreendimento_preco (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL REFERENCES empreendimento(id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL DEFAULT 'venda',
    valor_min BIGINT,
    valor_max BIGINT,
    moeda VARCHAR(3) NOT NULL DEFAULT 'BRL',
    data_referencia DATE,
    observacao TEXT,
    criado_por BIGINT REFERENCES usuario(id),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_preco_emp_data ON empreendimento_preco(empreendimento_id, data_referencia DESC);

CREATE TABLE IF NOT EXISTS empreendimento_condicao_comercial (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL UNIQUE REFERENCES empreendimento(id) ON DELETE CASCADE,
    entrada DOUBLE PRECISION,
    ato DOUBLE PRECISION,
    parcelas INT,
    valor_parcela DOUBLE PRECISION,
    baloes TEXT,
    financiamento TEXT,
    subsidio DOUBLE PRECISION,
    fgts BOOLEAN,
    correcao VARCHAR(50),
    condicoes_especiais TEXT,
    observacoes TEXT
);

CREATE TABLE IF NOT EXISTS empreendimento_planta (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL REFERENCES empreendimento(id) ON DELETE CASCADE,
    nome VARCHAR(100),
    tipo VARCHAR(50),
    metragem DOUBLE PRECISION,
    quartos INT,
    suites INT,
    banheiros INT,
    vagas INT,
    descricao TEXT,
    arquivo_id BIGINT REFERENCES empreendimento_documento(id) ON DELETE SET NULL,
    ordem INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_planta_emp ON empreendimento_planta(empreendimento_id);

CREATE TABLE IF NOT EXISTS empreendimento_area_comum (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL REFERENCES empreendimento(id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    descricao TEXT,
    icone VARCHAR(50),
    ordem INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ac_emp_ordem ON empreendimento_area_comum(empreendimento_id, ordem);

CREATE TABLE IF NOT EXISTS empreendimento_diferencial (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL REFERENCES empreendimento(id) ON DELETE CASCADE,
    titulo VARCHAR(150) NOT NULL,
    descricao TEXT,
    ordem INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS empreendimento_ponto_referencia (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL REFERENCES empreendimento(id) ON DELETE CASCADE,
    nome VARCHAR(150) NOT NULL,
    categoria VARCHAR(50),
    distancia DOUBLE PRECISION,
    unidade VARCHAR(10) DEFAULT 'm',
    tempo INT,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    ordem INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS empreendimento_imagem (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL REFERENCES empreendimento(id) ON DELETE CASCADE,
    arquivo_id BIGINT REFERENCES empreendimento_documento(id) ON DELETE SET NULL,
    tipo VARCHAR(30) CHECK (tipo IN ('fachada','implantacao','lazer','planta','interior','localizacao','mapa','outro')),
    legenda VARCHAR(255),
    ordem INT DEFAULT 0,
    destaque BOOLEAN DEFAULT false,
    url TEXT
);
CREATE INDEX IF NOT EXISTS idx_img_emp_tipo ON empreendimento_imagem(empreendimento_id, tipo);

CREATE TABLE IF NOT EXISTS empreendimento_historico (
    id BIGSERIAL PRIMARY KEY,
    empreendimento_id BIGINT NOT NULL REFERENCES empreendimento(id) ON DELETE CASCADE,
    campo VARCHAR(100) NOT NULL,
    valor_anterior TEXT,
    valor_novo TEXT,
    usuario BIGINT,
    origem VARCHAR(20) NOT NULL DEFAULT 'usuario' CHECK (origem IN ('ia','usuario')),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_hist_emp_campo ON empreendimento_historico(empreendimento_id, campo);

-- Sequences para GenerationType.AUTO (Hibernate espera <tabela>_seq)
CREATE SEQUENCE IF NOT EXISTS hibernate_sequence START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS papel_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS usuario_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS imovel_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS lead_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS tramitacao_status_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS refresh_token_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS token_blacklist_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS notificacao_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS lead_responsavel_historico_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS meta_seq START WITH 1 INCREMENT BY 50;

-- Seed inicial - Equipe Geral (Flyway roda só 1x, sem ON CONFLICT para compatibilidade H2)
INSERT INTO equipe (nome, descricao, ativo) VALUES ('Equipe Geral', 'Clientes sem equipe definida', true);

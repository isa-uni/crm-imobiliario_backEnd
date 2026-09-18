-- O "imóvel de interesse" do lead passa a apontar para a tela de Empreendimentos, não mais para a
-- tabela avulsa "imovel" (que continua existindo para outros usos, mas deixa de ser referenciada pelo lead).
ALTER TABLE lead DROP COLUMN IF EXISTS imovel_id;
ALTER TABLE lead ADD COLUMN empreendimento_id BIGINT REFERENCES empreendimento(id);

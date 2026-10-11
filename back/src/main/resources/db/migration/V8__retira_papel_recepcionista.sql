-- Retira o papel "recepcionista" (criado pela antiga tela de papéis; não é usado por nenhuma regra).
-- Sem usuários vinculados, o papel é apagado; se ainda houver algum, é só inativado (some do cadastro
-- e dos filtros), para não apagar usuários nem deixá-los sem papel.
DELETE FROM papel p
WHERE LOWER(p.papel) = 'recepcionista'
  AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.papel_id = p.id);

UPDATE papel SET ativo = FALSE WHERE LOWER(papel) = 'recepcionista';

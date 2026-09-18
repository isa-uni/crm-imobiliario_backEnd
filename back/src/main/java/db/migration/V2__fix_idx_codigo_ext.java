package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * V2: corrige idx_emp_codigo_ext para permitir múltiplos NULL/vazio.
 * Causa do 409: índice UNIQUE total não permitia múltiplos '' (string vazia).
 * Solução profissional: migração Java condicional por vendor.
 * - H2 (usado nos testes com MODE=PostgreSQL) não suporta índice parcial WHERE -> recria índice simples (múltiplos NULL já permitidos).
 * - PostgreSQL (produção) -> recria índice parcial WHERE IS NOT NULL AND <> ''.
 * Em ambos os casos saneia '' -> NULL e backend já normaliza blank->null.
 */
public class V2__fix_idx_codigo_ext extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            // 1. saneia dados legados
            stmt.execute("UPDATE empreendimento SET codigo_externo = NULL WHERE codigo_externo = ''");

            // 2. remove índice antigo (V1) de forma idempotente
            // H2 e PG suportam IF EXISTS
            stmt.execute("DROP INDEX IF EXISTS idx_emp_codigo_ext");

            // 3. identifica vendor
            String productName = "";
            try {
                productName = context.getConnection().getMetaData().getDatabaseProductName();
            } catch (Exception ignored) {}
            String lower = productName != null ? productName.toLowerCase() : "";
            boolean isPostgreSQL = lower.contains("postgresql");

            // H2 com MODE=PostgreSQL reporta productName = "H2" -> cai no else (correto)
            if (isPostgreSQL) {
                // índice parcial verdadeiro - melhor para PG
                stmt.execute("CREATE UNIQUE INDEX idx_emp_codigo_ext ON empreendimento(codigo_externo) WHERE codigo_externo IS NOT NULL AND codigo_externo <> ''");
            } else {
                // H2: índice simples; múltiplos NULL já são permitidos, e backend impede ''.
                stmt.execute("CREATE UNIQUE INDEX idx_emp_codigo_ext ON empreendimento(codigo_externo)");
            }
        }
    }
}

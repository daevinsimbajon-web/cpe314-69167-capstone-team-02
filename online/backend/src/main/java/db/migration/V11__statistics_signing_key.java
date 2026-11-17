package db.migration;

import java.security.SecureRandom;
import java.util.Base64;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** One persistent server secret per database; exports survive server restarts. */
public class V11__statistics_signing_key extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);
        try(var statement=context.getConnection().prepareStatement("INSERT INTO analytics_signing_keys(id,secret) VALUES (1,?)")) {
            statement.setString(1,Base64.getEncoder().encodeToString(secret));statement.executeUpdate();
        }
    }
}

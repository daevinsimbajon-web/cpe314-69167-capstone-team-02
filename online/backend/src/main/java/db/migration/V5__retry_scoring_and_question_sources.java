package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;

/** Additive schema changes; historical scores, balances, questions and rulesets stay untouched. */
public class V5__retry_scoring_and_question_sources extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        var constraints=new ArrayList<String>();
        try(var statement=connection.createStatement();var rows=statement.executeQuery("""
            SELECT tc.constraint_name,cc.check_clause FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc ON tc.constraint_name=cc.constraint_name AND tc.constraint_schema=cc.constraint_schema
            WHERE LOWER(tc.table_name)='answers' AND tc.constraint_type='CHECK'
            """)){while(rows.next()){String clause=rows.getString(2).toLowerCase(java.util.Locale.ROOT);if(clause.contains("points")&&!clause.contains("is not null"))constraints.add(rows.getString(1));}}
        try(var statement=connection.createStatement()){
            for(String name:constraints)statement.execute("ALTER TABLE answers DROP CONSTRAINT \""+name.replace("\"","\"\"")+"\"");
            statement.execute("ALTER TABLE answers ADD CONSTRAINT answer_points_supported CHECK(points IN (0,2,4,5,7,10))");
            statement.execute("ALTER TABLE quiz_rooms ADD COLUMN question_source VARCHAR(12) NOT NULL DEFAULT 'LOCAL' CHECK(question_source IN ('LOCAL','OPENTDB'))");
            statement.execute("ALTER TABLE quiz_rooms ADD COLUMN opentdb_category_id INTEGER CHECK(opentdb_category_id>0)");
            statement.execute("ALTER TABLE quiz_rooms ADD CONSTRAINT retry_v2_allowance CHECK(scoring_version<>'retry-v2' OR retry_allowance BETWEEN 0 AND 3)");
            statement.execute("CREATE TABLE opentdb_category_cache(id INTEGER PRIMARY KEY CHECK(id=1),document TEXT NOT NULL,fetched_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        }
    }
}

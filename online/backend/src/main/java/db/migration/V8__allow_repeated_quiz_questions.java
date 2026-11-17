package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;

/** An ordered quiz may intentionally use the same immutable question twice. */
public class V8__allow_repeated_quiz_questions extends BaseJavaMigration {
    @Override public void migrate(Context context)throws Exception {
        var names=new ArrayList<String>();var connection=context.getConnection();
        try(var s=connection.createStatement();var r=s.executeQuery("""
          SELECT DISTINCT tc.constraint_name FROM information_schema.table_constraints tc
          JOIN information_schema.key_column_usage kc ON tc.constraint_schema=kc.constraint_schema AND tc.constraint_name=kc.constraint_name
          WHERE LOWER(tc.table_name)='room_questions' AND tc.constraint_type='UNIQUE' AND LOWER(kc.column_name)='question_id'
          """)){while(r.next())names.add(r.getString(1));}
        try(var s=connection.createStatement()){for(String name:names)s.execute("ALTER TABLE room_questions DROP CONSTRAINT \""+name.replace("\"","\"\"")+"\"");}
    }
}

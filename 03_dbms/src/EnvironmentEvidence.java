import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;

public class EnvironmentEvidence {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("03_dbms/results"));
        try (var output = new CSVPrinter(Files.newBufferedWriter(Path.of("03_dbms/results/environment.csv")),
                CSVFormat.DEFAULT.builder().setHeader("engine", "setting", "value").get())) {
            for (String engine : List.of("postgres", "opengauss")) {
                try (var database = Database.connect("03_dbms/" + engine + ".properties")) {
                    output.printRecord(engine, "version", Database.scalar(database, "SELECT version()"));
                    output.printRecord(engine, "driver", database.getMetaData().getDriverVersion());
                    for (String setting : List.of("fsync", "synchronous_commit", "shared_buffers", "max_connections", "full_page_writes"))
                        output.printRecord(engine, setting, Database.scalar(database, "SHOW " + setting));
                    for (var row : Database.query(database, "SELECT relname,reloptions FROM pg_class WHERE relname IN ('movies','people') AND relnamespace=(SELECT oid FROM pg_namespace WHERE nspname='public')"))
                        output.printRecord(engine, row.get("relname"), row.get("reloptions"));
                }
            }
        }
    }
}

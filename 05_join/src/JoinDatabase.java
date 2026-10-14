import java.sql.Connection;
import java.util.*;

public class JoinDatabase implements AutoCloseable {
    public final Connection connection;
    private final Map<String, ExperimentTable> tables = new LinkedHashMap<>();
    private int statisticsTarget = -1;
    private boolean countryForeignKey = true;

    public JoinDatabase(Connection connection, JoinData data) throws Exception {
        this.connection = connection;
        create("countries", "(country_code varchar(2) PRIMARY KEY, continent varchar(20) NOT NULL)");
        create("people", "(peopleid integer PRIMARY KEY, first_name varchar(30), surname varchar(30) NOT NULL)");
        create("movies", "(movieid integer PRIMARY KEY, country varchar(2) NOT NULL REFERENCES "
                + table("countries") + "(country_code), year_released integer NOT NULL)");
        create("credits", "(movieid integer REFERENCES " + table("movies") + "(movieid), peopleid integer REFERENCES "
                + table("people") + "(peopleid), credited_as varchar(1), PRIMARY KEY(movieid,peopleid,credited_as))");
        var columns = Map.of("countries", List.of("country_code", "continent"),
                "people", List.of("peopleid", "first_name", "surname"),
                "movies", List.of("movieid", "country", "year_released"),
                "credits", List.of("movieid", "peopleid", "credited_as"));
        for (String name : tables.keySet()) {
            TableData.insert(connection, table(name), data.tables.get(name), columns.get(name),
                    Set.of("peopleid", "movieid", "year_released"));
            Rows.check(data.tables.get(name), Database.query(connection, "SELECT * FROM " + table(name)));
            Database.execute(connection, "ANALYZE " + table(name));
        }
    }

    private void create(String name, String definition) throws Exception { tables.put(name, new ExperimentTable(connection, definition)); }
    private String table(String name) { return tables.get(name).name; }

    public String sql(JoinData.Task task) {
        return "SELECT p.peopleid,p.first_name,p.surname,count(*) AS films FROM " + table("people") + " p JOIN "
                + table("credits") + " c ON c.peopleid=p.peopleid JOIN " + table("movies")
                + " m ON m.movieid=c.movieid JOIN " + table("countries") + " n ON n.country_code=m.country "
                + "WHERE c.credited_as='A' AND m.year_released BETWEEN " + task.start() + " AND " + task.end()
                + (task.continent().equals("ALL") ? "" : " AND n.continent=" + Database.literal(task.continent()))
                + " GROUP BY p.peopleid,p.first_name,p.surname ORDER BY films DESC,p.peopleid ASC LIMIT 20";
    }

    public List<Map<String, String>> query(JoinData.Task task) throws Exception { return Database.query(connection, sql(task)); }

    public void statistics(int target) throws Exception {
        if (statisticsTarget == target) return;
        var columns = Map.of("countries", List.of("country_code", "continent"),
                "people", List.of("peopleid", "first_name", "surname"),
                "movies", List.of("movieid", "country", "year_released"),
                "credits", List.of("movieid", "peopleid", "credited_as"));
        for (String name : tables.keySet()) {
            for (String column : columns.get(name))
                Database.execute(connection, "ALTER TABLE " + table(name) + " ALTER COLUMN " + column + " SET STATISTICS " + target);
            Database.execute(connection, "ANALYZE " + table(name));
        }
        statisticsTarget = target;
    }

    public void countryForeignKey(boolean enabled) throws Exception {
        if (countryForeignKey == enabled) return;
        // 只切换副本的电影到国家外键；数据已核对，不改动数据或主键索引。
        if (enabled) {
            Database.execute(connection, "ALTER TABLE " + table("movies") + " ADD FOREIGN KEY(country) REFERENCES "
                    + table("countries") + "(country_code)");
        } else {
            for (var row : Database.query(connection, "SELECT conname FROM pg_constraint WHERE contype='f' AND conrelid="
                    + Database.literal(table("movies")) + "::regclass"))
                Database.execute(connection, "ALTER TABLE " + table("movies") + " DROP CONSTRAINT \""
                        + row.get("conname").replace("\"", "\"\"") + "\"");
        }
        countryForeignKey = enabled;
    }

    @Override public void close() throws Exception {
        var reversed = new ArrayList<>(tables.values()); Collections.reverse(reversed);
        for (var table : reversed) table.close();
    }
}

import java.sql.*;
import java.util.*;

public class DbCounterStore implements CounterStore {
    private final String config;
    private final Connection admin;
    private final ExperimentTable table;
    private final List<Map<String, String>> initial;

    public DbCounterStore(String engine, List<Integer> ids) throws Exception {
        config = "03_dbms/" + engine + ".properties";
        admin = Database.connect(config);
        table = new ExperimentTable(admin, "(movieid integer PRIMARY KEY, views bigint NOT NULL CHECK(views>=0))");
        initial = ids.stream().map(id -> Map.of("movieid", id.toString(), "views", "0")).toList();
        TableData.insert(admin, table.name, initial, List.of("movieid", "views"), Set.of("movieid", "views"));
        Database.execute(admin, "ANALYZE " + table.name);
    }

    @Override public Session session() throws Exception {
        Connection connection = Database.connect(config);
        // 每线程独立连接。每条语句独立提交，UPDATE 使用库内原子递增，不做 Java 读后覆盖。
        connection.setAutoCommit(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        var select = connection.prepareStatement("SELECT views FROM " + table.name + " WHERE movieid=?");
        var update = connection.prepareStatement("UPDATE " + table.name + " SET views=views+1 WHERE movieid=?");
        return new Session() {
            @Override public long read(int id) throws Exception {
                select.setInt(1, id);
                try (var rows = select.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("查询编号不存在");
                    return rows.getLong(1);
                }
            }
            @Override public void increment(int id) throws Exception {
                update.setInt(1, id);
                if (update.executeUpdate() != 1) throw new IllegalStateException("递增未修改一行");
            }
            @Override public void close() throws Exception { select.close(); update.close(); connection.close(); }
        };
    }

    @Override public void reset() throws Exception {
        // 每批重新装入初始行，避免全表归零 UPDATE 的历史版本累积到后续测量。
        Database.execute(admin, "TRUNCATE TABLE " + table.name);
        TableData.insert(admin, table.name, initial, List.of("movieid", "views"), Set.of("movieid", "views"));
        Database.execute(admin, "ANALYZE " + table.name);
    }

    @Override public Map<Integer, Long> snapshot() throws Exception {
        Map<Integer, Long> values = new HashMap<>();
        for (var row : Database.query(admin, "SELECT movieid,views FROM " + table.name))
            values.put(Integer.parseInt(row.get("movieid")), Long.parseLong(row.get("views")));
        return values;
    }

    @Override public void close() throws Exception { table.close(); admin.close(); }
}

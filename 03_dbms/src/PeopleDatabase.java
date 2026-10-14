import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class PeopleDatabase implements AutoCloseable {
    private final Connection connection;
    private final String table = "public.people_bench_" + UUID.randomUUID().toString().replace("-", "");
    private List<Map<String, String>> expected;
    private long changedCount;
    private String updateSql;

    public PeopleDatabase(Connection connection) {
        this.connection = connection;
    }

    public void prepare() throws Exception {
        // 用普通持久表保留索引、约束与 WAL；不能用临时表代替被测业务表。
        if (!Database.scalar(connection, "SHOW fsync").equals("on"))
            throw new IllegalStateException("本实验比较持久化更新，需要数据库 fsync=on。");
        Database.execute(connection, "SET synchronous_commit = on");
        Database.execute(connection, "CREATE TEMP TABLE people_baseline AS SELECT * FROM public.people");
        var source = Database.query(connection, "SELECT * FROM people_baseline");
        expected = PeopleUpdate.expected(source);
        changedCount = PeopleUpdate.changedCount(source);
        System.out.printf("数据 %d 行，更新 %d 行%n", source.size(), changedCount);
        Database.execute(connection, "CREATE TABLE " + table + " (LIKE public.people INCLUDING ALL)");
        updateSql = updateSql();
        connection.setAutoCommit(false);
    }

    public void restore() throws Exception {
        // 每轮从同一快照开始，恢复与 ANALYZE 均不进入计时。
        Database.execute(connection, "TRUNCATE TABLE " + table);
        Database.execute(connection, "INSERT INTO " + table + " SELECT * FROM people_baseline");
        connection.commit();
        connection.setAutoCommit(true);
        Database.execute(connection, "ANALYZE " + table);
        connection.setAutoCommit(false);
    }

    private String updateSql() {
        String from = Database.literal(PeopleUpdate.FROM), to = Database.literal(PeopleUpdate.TO);
        String assignments = PeopleUpdate.COLUMNS.stream()
                .map(column -> column + " = replace(" + column + ", " + from + ", " + to + ")")
                .collect(Collectors.joining(", "));
        String condition = PeopleUpdate.COLUMNS.stream()
                .map(column -> Database.contains(column, PeopleUpdate.FROM))
                .collect(Collectors.joining(" OR "));
        return "UPDATE " + table + " SET " + assignments + " WHERE " + condition;
    }

    public int update() throws Exception {
        int changed = Database.execute(connection, updateSql);
        connection.commit(); // 返回前完成提交，因此提交等待也在计时内。
        return changed;
    }

    public void verify(int changed) throws Exception {
        PeopleUpdate.checkCount(changedCount, changed);
        Rows.check(expected, Database.query(connection, "SELECT * FROM " + table));
        connection.commit();
    }

    @Override public void close() throws Exception {
        if (!connection.getAutoCommit()) connection.rollback();
        connection.setAutoCommit(true);
        Database.execute(connection, "DROP TABLE IF EXISTS " + table);
        Database.execute(connection, "DROP TABLE IF EXISTS pg_temp.people_baseline");
    }
}

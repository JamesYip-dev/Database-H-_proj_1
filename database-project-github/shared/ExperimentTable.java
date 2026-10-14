import java.sql.Connection;
import java.util.UUID;

// 每次运行独立创建普通表，关闭后清理；不改原始业务数据。
public class ExperimentTable implements AutoCloseable {
    public final Connection connection;
    public final String name = "public.experiment_" + UUID.randomUUID().toString().replace("-", "");

    public ExperimentTable(Connection connection, String definition) throws Exception {
        this.connection = connection;
        Database.execute(connection, "CREATE TABLE " + name + " " + definition);
    }

    @Override public void close() throws Exception {
        if (!connection.getAutoCommit()) connection.rollback();
        connection.setAutoCommit(true);
        Database.execute(connection, "DROP TABLE " + name);
    }
}

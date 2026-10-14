import java.sql.*;
import java.util.*;

// 将 CSV 中的字段以正确类型批量装入实验表；只用于计时外的数据准备。
public class TableData {
    public static void insert(Connection connection, String table, List<Map<String, String>> rows,
                              List<String> columns, Set<String> integerColumns) throws Exception {
        String sql = "INSERT INTO " + table + " (" + String.join(",", columns) + ") VALUES ("
                + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement(sql)) {
            int pending = 0;
            for (var row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    String column = columns.get(i), value = row.get(column);
                    if (value == null) statement.setNull(i + 1, integerColumns.contains(column) ? Types.INTEGER : Types.VARCHAR);
                    else if (integerColumns.contains(column)) statement.setInt(i + 1, Integer.parseInt(value));
                    else statement.setString(i + 1, value);
                }
                statement.addBatch();
                if (++pending == 500) { statement.executeBatch(); pending = 0; }
            }
            if (pending > 0) statement.executeBatch();
            connection.commit();
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }
}

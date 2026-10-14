import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class Database {
    public static Connection connect(String[] args) throws Exception {
        return connect(args.length == 0 ? "shared/database.properties" : args[0]);
    }

    public static Connection connect(String file) throws Exception {
        Path config = Path.of(file);
        Properties settings = new Properties();
        try (var reader = Files.newBufferedReader(config)) { settings.load(reader); }
        String user = settings.getProperty("user");
        String environmentPassword = System.getenv(settings.getProperty("passwordEnv", "DB_PASSWORD"));
        if (environmentPassword != null)
            return DriverManager.getConnection(settings.getProperty("url"), user, environmentPassword);
        var console = System.console();
        if (console == null) throw new IllegalStateException("请在终端运行实验脚本，以便隐藏输入密码。");
        char[] password = console.readPassword("Password for %s: ", user);
        if (password == null) throw new IllegalStateException("已取消密码输入。");
        try {
            return DriverManager.getConnection(settings.getProperty("url"), user, new String(password));
        } finally { Arrays.fill(password, '\0'); }
    }

    public static List<Map<String, String>> query(Connection connection, String sql) throws SQLException {
        List<Map<String, String>> result = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            var columns = rows.getMetaData();
            while (rows.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 1; i <= columns.getColumnCount(); i++)
                    row.put(columns.getColumnLabel(i), rows.getString(i));
                result.add(row);
            }
        }
        return result;
    }

    public static int execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { return statement.executeUpdate(sql); }
    }

    public static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    public static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }

    public static String contains(String column, String text) {
        String escaped = text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return column + " LIKE " + literal("%" + escaped + "%") + " ESCAPE '!'";
    }
}

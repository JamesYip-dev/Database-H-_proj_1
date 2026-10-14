import java.nio.file.Path;
import java.sql.Connection;
import java.util.*;

public class SearchData {
    public static final Path INPUT = Path.of("data", "movies.csv");
    public static final String ALL_MOVIES = "SELECT movieid, title FROM public.movies";

    public static List<Map<String, String>> csv(String keyword) throws Exception {
        return CsvFiles.select(INPUT, row -> Rows.contains(row, "title", keyword));
    }

    public static List<Map<String, String>> database(Connection connection, String keyword) throws Exception {
        // 与 Java contains 一致：区分大小写，关键词中的 % 和 _ 按普通字符处理。
        String sql = ALL_MOVIES + " WHERE " + Database.contains("title", keyword);
        return Database.query(connection, sql);
    }

    public static List<Map<String, String>> expected(List<Map<String, String>> source, String keyword) {
        return source.stream().filter(row -> Rows.contains(row, "title", keyword)).toList();
    }
}

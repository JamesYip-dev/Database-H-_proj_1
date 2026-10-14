import java.nio.file.*;
import java.sql.Connection;
import java.util.*;

public class IndexExperiment {
    public static void main(String[] args) throws Exception {
        String engine = args.length == 0 ? "postgres" : args[0];
        try (var database = Database.connect("03_dbms/" + engine + ".properties")) {
            for (int repeat = 0; repeat < 3; repeat++) compare(database, engine);
        }
    }

    private static void compare(Connection database, String engine) throws Exception {
        var source = CsvFiles.read(SearchData.INPUT);
        String id = source.get(source.size() / 2).get("movieid");
        var expected = source.stream().filter(r -> r.get("movieid").equals(id)).toList();
        // 自建内存索引也能加速；加载文件和建立索引不包含在热查询时间内。
        Map<String, Map<String, String>> memoryIndex = new HashMap<>();
        for (var row : source) memoryIndex.put(row.get("movieid"), row);
        try (var scan = table(database, false); var index = table(database, true)) {
            Path results = Path.of("04_index/results", engine);
            Benchmark.run(results,
                    Benchmark.readOnly("CSV scan", () -> CsvFiles.select(SearchData.INPUT, r -> r.get("movieid").equals(id)), r -> Rows.check(expected, r)),
                    Benchmark.readOnly("Java memory index", () -> List.of(memoryIndex.get(id)), r -> Rows.check(expected, r)),
                    trial("DB scan", scan, id, expected), trial("DB index", index, id, expected));
            String plans = "movieid = " + id + "\n";
            for (var table : List.of(scan, index))
                plans += Database.query(database, "EXPLAIN " + query(table, id)).stream()
                        .map(row -> row.values().iterator().next()).collect(java.util.stream.Collectors.joining("\n")) + "\n\n";
            Files.writeString(results.resolve("plans.txt"), plans);
        }
    }

    private static ExperimentTable table(Connection database, boolean indexed) throws Exception {
        var table = new ExperimentTable(database, "AS SELECT movieid,title FROM public.movies");
        if (indexed) Database.execute(database, "CREATE INDEX " + table.name.substring(7) + "_id ON " + table.name + " (movieid)");
        Database.execute(database, "ANALYZE " + table.name);
        return table;
    }

    private static String query(ExperimentTable table, String id) {
        return "SELECT movieid,title FROM " + table.name + " WHERE movieid=" + Integer.parseInt(id);
    }

    private static Benchmark.Trial<List<Map<String, String>>> trial(String label, ExperimentTable table,
            String id, List<Map<String, String>> expected) {
        return Benchmark.readOnly(label, () -> Database.query(table.connection, query(table, id)),
                rows -> Rows.check(expected, rows));
    }
}

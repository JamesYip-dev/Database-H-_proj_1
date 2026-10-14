import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JoinExperiment {
    public static void main(String[] args) throws Exception {
        try (var pg = Database.connect("03_dbms/postgres.properties");
             var og = Database.connect("03_dbms/opengauss.properties")) {
            compare(pg, og);
        }
    }

    private static void compare(java.sql.Connection pg, java.sql.Connection og) throws Exception {
        var memory = new JoinData();
        Path results = Files.createDirectories(Path.of("05_join/results", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"))));
        try (var postgres = new JoinDatabase(pg, memory); var gauss = new JoinDatabase(og, memory)) {
            for (int group = 1; group <= 3; group++) {
                var tasks = new ArrayList<>(JoinData.TASKS);
                Collections.shuffle(tasks, new Random(20261011L + group));
                for (var task : tasks) {
                    var expected = memory.query(task);
                    Benchmark.run(results.resolve("group-" + group).resolve(task.name()),
                            Benchmark.readOnly("Java CSV", () -> new JoinData().query(task), r -> check(expected, r)),
                            Benchmark.readOnly("Java memory", () -> memory.query(task), r -> check(expected, r)),
                            Benchmark.readOnly("PostgreSQL", () -> postgres.query(task), r -> check(expected, r)),
                            Benchmark.readOnly("openGauss", () -> gauss.query(task), r -> check(expected, r)));
                }
            }
            // 计划及示例结果不参与性能计时，用于解释两库执行关联、分组和排序的方式。
            for (var pair : Map.of("postgres", postgres, "opengauss", gauss).entrySet()) {
                StringBuilder plans = new StringBuilder();
                for (var task : JoinData.TASKS) {
                    plans.append(task.name()).append('\n').append(pair.getValue().sql(task)).append('\n');
                    for (var row : Database.query(pair.getValue().connection, "EXPLAIN ANALYZE " + pair.getValue().sql(task)))
                        plans.append(row.values().iterator().next()).append('\n');
                    plans.append('\n');
                }
                Files.writeString(results.resolve(pair.getKey() + "-plans.txt"), plans);
            }
            StringBuilder example = new StringBuilder();
            for (var task : JoinData.TASKS) example.append(task.name()).append(' ').append(memory.query(task)).append('\n');
            Files.writeString(results.resolve("checked-results.txt"), example);
            Files.writeString(results.resolve("method.txt"), "groups=3\nwarmup=10\nmeasured=30\ntasks=4\n"
                    + "Java CSV includes loading and hash indexes; Java memory excludes those, not aggregation\n"
                    + "diagnostics: Europe/Asia, 6 variants, 3 groups, 10 warmup + 30 measured each\n"
                    + "diagnostic parameters and statistics on experiment copies only, separate from main comparison\n");
            JoinDiagnostics.run(results, "postgres", postgres, memory);
            JoinDiagnostics.run(results, "opengauss", gauss, memory);
        }
        System.out.println("关联统计全部核对通过：" + results.toAbsolutePath());
    }

    private static void check(List<Map<String, String>> expected, List<Map<String, String>> actual) {
        Rows.check(expected, actual);
        if (!expected.equals(actual)) throw new IllegalStateException("前 20 名排列不一致");
    }
}

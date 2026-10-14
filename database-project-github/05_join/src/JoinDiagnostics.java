import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;

public class JoinDiagnostics {
    private record Variant(String name, int statistics, boolean countryForeignKey, Map<String, String> settings) {}
    private static final List<Variant> VARIANTS = List.of(
            new Variant("default", -1, true, Map.of()),
            new Variant("hashjoin_off", -1, true, Map.of("enable_hashjoin", "off", "enable_nestloop", "on")),
            new Variant("nestloop_off", -1, true, Map.of("enable_nestloop", "off")),
            new Variant("statistics_1000", 1000, true, Map.of()),
            new Variant("planner_common", -1, true, Map.of("work_mem", "64MB", "effective_cache_size", "4GB")),
            new Variant("no_country_fk", -1, false, Map.of()));

    public static void run(Path results, String engine, JoinDatabase db, JoinData data) throws Exception {
        var defaults = new LinkedHashMap<String, String>();
        for (String name : List.of("enable_nestloop", "enable_hashjoin", "enable_mergejoin", "enable_indexscan",
                "default_statistics_target", "work_mem", "random_page_cost", "seq_page_cost", "cpu_tuple_cost", "effective_cache_size"))
            defaults.put(name, Database.scalar(db.connection, "SHOW " + name));
        Files.writeString(results.resolve(engine + "-settings.txt"), defaults.toString() + "\n");
        var format = CSVFormat.DEFAULT.builder().setHeader("group", "task", "variant", "phase", "run", "elapsed_ms", "verified").get();
        StringBuilder plans = new StringBuilder();
        try (var writer = Files.newBufferedWriter(results.resolve(engine + "-diagnostic.csv")); var csv = new CSVPrinter(writer, format)) {
            // 参数仅在当前连接内改变；提高统计量只作用于实验副本。诊断不混入默认配置的性能比较。
            for (int group = 1; group <= 3; group++) {
                for (int order = 0; order < VARIANTS.size(); order++) {
                    var variant = VARIANTS.get((order + group - 1) % VARIANTS.size());
                    set(db, defaults, variant);
                    for (var task : JoinData.TASKS.subList(0, 2)) {
                        var expected = data.query(task);
                        for (int run = 0; run < 40; run++) {
                            long begin = System.nanoTime();
                            var actual = db.query(task);
                            double ms = (System.nanoTime() - begin) / 1_000_000.0;
                            Rows.check(expected, actual);
                            if (!expected.equals(actual)) throw new IllegalStateException("诊断查询前 20 名顺序不一致");
                            csv.printRecord(group, task.name(), variant.name(), run < 10 ? "warmup" : "measured",
                                    run < 10 ? run + 1 : run - 9, String.format(Locale.ROOT, "%.6f", ms), true);
                        }
                        plans.append("group=").append(group).append(" task=").append(task.name())
                                .append(" variant=").append(variant.name()).append('\n').append(db.sql(task)).append('\n');
                        for (var row : Database.query(db.connection, "EXPLAIN ANALYZE " + db.sql(task)))
                            plans.append(row.values().iterator().next()).append('\n');
                        plans.append('\n');
                    }
                    csv.flush();
                }
            }
        } finally {
            set(db, defaults, VARIANTS.getFirst());
        }
        Files.writeString(results.resolve(engine + "-diagnostic-plans.txt"), plans);
    }

    private static void set(JoinDatabase db, Map<String, String> defaults, Variant variant) throws Exception {
        for (String name : List.of("enable_nestloop", "enable_hashjoin", "work_mem", "effective_cache_size"))
            Database.execute(db.connection, "SET " + name + " TO " + Database.literal(variant.settings().getOrDefault(name, defaults.get(name))));
        db.statistics(variant.statistics());
        db.countryForeignKey(variant.countryForeignKey());
    }
}

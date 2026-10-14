import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.csv.*;

public class ConcurrencyExperiment {
    private static final int[] WORKERS = {1, 4, 8, 16};
    private record Method(String name, CounterStore store) {}

    public static void main(String[] args) throws Exception {
        var ids = CsvFiles.read(SearchData.INPUT).stream().map(r -> Integer.parseInt(r.get("movieid"))).sorted().toList();
        try (var csv = new CsvCounterStore(ids); var pg = new DbCounterStore("postgres", ids);
             var og = new DbCounterStore("opengauss", ids)) {
            compare(ids, List.of(new Method("Java CSV", csv), new Method("PostgreSQL", pg), new Method("openGauss", og)), args);
        }
    }

    private static void compare(List<Integer> ids, List<Method> methods, String[] args) throws Exception {
        double duration = args.length == 0 ? 30 : Double.parseDouble(args[0]);
        int groups = args.length < 2 ? 3 : Integer.parseInt(args[1]);
        if (duration <= 0 || groups <= 0) throw new IllegalArgumentException("时长、组数必须为正数");
        double warmup = Math.min(3, duration);
        Path results = Files.createDirectories(Path.of("06_concurrency/results", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"))));
        Files.writeString(results.resolve("method.txt"), "rows=" + ids.size() + "\nhot_records=" + Math.min(64, ids.size())
                + "\npatterns=dispersed,hot\nworkers=1,4,8,16\ngroups=" + groups + "\nwarmup_seconds=" + warmup
                + "\nmeasured_seconds=" + duration + "\nread_percent=80\nwrite_percent=20\nseed=20261013\n"
                + "connections_and_restore_excluded=true\nthroughput_seconds_include_finishing_started_batches=true\n"
                + "CSV: immutable in-memory committed snapshots, serialized full-file durable writes\n"
                + "DB: one connection per client, prepared statements, READ COMMITTED, autocommit\n"
                + "correctness: exact persisted counters from completed writes, exact 80/20 ratio, per-client monotonic reads\n"
                + "same per-client deterministic request stream; completed stream lengths differ with throughput\n"
                + "latency medians calculated from every completed request; per-worker summaries and per-record increments saved\n"
                + "scope: one Java process, no process crash, power loss or multiple file-writing processes\n");
        var summaryFormat = CSVFormat.DEFAULT.builder().setHeader("group", "pattern", "workers", "method", "operations",
                "reads", "writes", "seconds", "ops_per_second", "read_median_ms", "write_median_ms", "verified").get();
        try (var writer = Files.newBufferedWriter(results.resolve("summary.csv")); var summary = new CSVPrinter(writer, summaryFormat)) {
            for (int group = 1; group <= groups; group++) {
                for (int scenario = 0; scenario < 2; scenario++) {
                    String pattern = (scenario + group) % 2 == 0 ? "dispersed" : "hot";
                    int targets = pattern.equals("hot") ? Math.min(64, ids.size()) : ids.size();
                    for (int level = 0; level < WORKERS.length; level++) {
                        int workers = WORKERS[(level + group - 1) % WORKERS.length];
                        for (int order = 0; order < methods.size(); order++) {
                            var method = methods.get((order + level + scenario + group - 1) % methods.size());
                            method.store().reset();
                            var warm = ConcurrentWorkload.run(method.store(), ids, workers, targets, warmup, 20261012L);
                            ConcurrentWorkload.verify(method.store(), ids, warm);
                            method.store().reset(); // 恢复零计数、连接建立和正确性检查都在正式计时外。
                            var result = ConcurrentWorkload.run(method.store(), ids, workers, targets, duration, 20261013L);
                            ConcurrentWorkload.verify(method.store(), ids, result);
                            saveDetails(results.resolve("group-" + group + "-" + pattern + "-workers-" + workers + "-"
                                    + method.name().replace(' ', '-')), ids, result);
                            summary.printRecord(group, pattern, workers, method.name(), result.operations(), result.reads(), result.writes(),
                                    decimal(result.seconds()), decimal(result.operations() / result.seconds()),
                                    decimal(result.median(false)), decimal(result.median(true)), true);
                            summary.flush();
                            System.out.println("第 " + group + " 组 " + pattern + " " + workers + " 客户端 " + method.name()
                                    + ": " + decimal(result.operations() / result.seconds()) + " 次/秒，持久计数核对通过。");
                        }
                    }
                }
            }
        }
        System.out.println("并发读写全部核对通过：" + results.toAbsolutePath());
    }

    private static String decimal(double value) { return String.format(Locale.ROOT, "%.6f", value); }
    private static double median(double[] values) { Arrays.sort(values); return BenchmarkResults.percentile(values, .5); }

    private static void saveDetails(Path prefix, List<Integer> ids, ConcurrentWorkload.Result result) throws Exception {
        var format = CSVFormat.DEFAULT.builder().setHeader("worker", "reads", "writes", "seconds", "read_median_ms", "write_median_ms").get();
        try (var writer = Files.newBufferedWriter(Path.of(prefix + "-workers.csv")); var csv = new CSVPrinter(writer, format)) {
            for (var w : result.workers()) csv.printRecord(w.number(), w.reads(), w.writes(), decimal(w.seconds()),
                    decimal(median(w.readTimes())), decimal(median(w.writeTimes())));
        }
        try (var writer = Files.newBufferedWriter(Path.of(prefix + "-increments.csv")); var csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            csv.printRecord("movieid", "completed_writes");
            for (int i = 0; i < ids.size(); i++) {
                long count = 0;
                for (var w : result.workers()) count += w.increments()[i];
                csv.printRecord(ids.get(i), count);
            }
        }
    }
}

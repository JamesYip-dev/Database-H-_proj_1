import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.csv.*;

public class KeywordSearch {
    private static final Path KEYWORDS = Path.of("03_dbms/keywords.txt");
    private static final long SEED = 20261011L;
    private record Timing(int group, int round, String phase, int keywordOrder,
                          int methodOrder, String keyword, int matches, String method, double ms) {}

    public static void main(String[] args) throws Exception {
        try (var pg = Database.connect("03_dbms/postgres.properties");
             var og = Database.connect("03_dbms/opengauss.properties")) {
            run(pg, og);
        }
    }

    public static void run(Connection pg, Connection og) throws Exception {
        var source = CsvFiles.read(SearchData.INPUT);
        var words = Files.readAllLines(KEYWORDS).stream().map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
        if (words.isEmpty() || new HashSet<>(words).size() != words.size())
            throw new IllegalArgumentException("关键词列表必须非空且不重复");
        // 词表在计时前固定；完整数据核对和统计信息准备都不计时。
        for (var c : List.of(pg, og)) {
            Rows.check(source, Database.query(c, SearchData.ALL_MOVIES));
            Database.execute(c, "ANALYZE public.movies");
        }
        Map<String, List<Map<String, String>>> expected = new LinkedHashMap<>();
        for (String word : words) expected.put(word, SearchData.expected(source, word));
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
        Path results = Files.createDirectories(Path.of("03_dbms/results/keywords", stamp));
        saveKeywords(results, expected);
        List<Timing> all = new ArrayList<>();
        for (int group = 1; group <= 3; group++) {
            var timings = compare(pg, og, words, expected, group);
            saveTimings(results.resolve("group-" + group + ".csv"), timings);
            all.addAll(timings);
        }
        saveSummary(results, all);
        System.out.println("全部逐词核对通过；结果：" + results.toAbsolutePath());
    }

    private static List<Timing> compare(Connection pg, Connection og, List<String> words,
            Map<String, List<Map<String, String>>> expected, int group) throws Exception {
        List<Timing> timings = new ArrayList<>();
        Random random = new Random(SEED + group);
        for (int round = 0; round < Benchmark.WARMUP + Benchmark.RUNS; round++) {
            // 每轮查完所有词；固定种子的随机顺序可复现，不按耗时选择关键词。
            var shuffled = new ArrayList<>(words);
            Collections.shuffle(shuffled, random);
            for (int position = 0; position < shuffled.size(); position++) {
                String word = shuffled.get(position);
                var rows = expected.get(word);
                var methods = List.of(
                        Benchmark.readOnly("Java CSV", () -> SearchData.csv(word), r -> Rows.check(rows, r)),
                        Benchmark.readOnly("PostgreSQL", () -> SearchData.database(pg, word), r -> Rows.check(rows, r)),
                        Benchmark.readOnly("openGauss", () -> SearchData.database(og, word), r -> Rows.check(rows, r)));
                // 同一词紧邻比较三种方法，并轮换先后；核对发生在 measure 的计时之外。
                for (int order = 0; order < methods.size(); order++) {
                    var method = methods.get((group - 1 + round + position + order) % methods.size());
                    double ms = Benchmark.measure(method);
                    timings.add(new Timing(group, round + 1,
                            round < Benchmark.WARMUP ? "warmup" : "measured", position + 1,
                            order + 1, word, rows.size(), method.name(), ms));
                }
            }
        }
        System.out.println("关键词查询第 " + group + " 组完成：" + words.size()
                + " 个词，每词预热 " + Benchmark.WARMUP + " 次、正式 " + Benchmark.RUNS + " 次。");
        return timings;
    }

    private static void saveKeywords(Path directory,
            Map<String, List<Map<String, String>>> expected) throws Exception {
        try (var writer = Files.newBufferedWriter(directory.resolve("keywords.csv"));
             var csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader("keyword", "matches").get())) {
            for (var item : expected.entrySet()) csv.printRecord(item.getKey(), item.getValue().size());
        }
        Files.writeString(directory.resolve("method.txt"),
                "case_sensitive=true\nseed_base=" + SEED + "\ngroups=3\nwarmup_per_keyword="
                + Benchmark.WARMUP + "\nmeasured_per_keyword=" + Benchmark.RUNS
                + "\nsummary=median of equally repeated single-query timings; warmup excluded\n");
    }

    private static void saveTimings(Path file, List<Timing> timings) throws Exception {
        var format = CSVFormat.DEFAULT.builder().setHeader("group", "round", "phase", "keyword_order",
                "method_order", "keyword", "matches", "method", "elapsed_ms").get();
        try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW);
             var csv = new CSVPrinter(writer, format)) {
            for (var t : timings) csv.printRecord(t.group(), t.round(), t.phase(), t.keywordOrder(),
                    t.methodOrder(), t.keyword(), t.matches(), t.method(), decimal(t.ms()));
        }
    }

    private static void saveSummary(Path directory, List<Timing> all) throws Exception {
        var format = CSVFormat.DEFAULT.builder().setHeader("group", "method", "queries", "median_ms").get();
        try (var writer = Files.newBufferedWriter(directory.resolve("summary.csv"));
             var csv = new CSVPrinter(writer, format)) {
            for (int group = 1; group <= 3; group++) {
                final int selected = group;
                for (String method : List.of("Java CSV", "PostgreSQL", "openGauss")) {
                    double[] values = all.stream().filter(t -> t.group() == selected
                            && t.phase().equals("measured") && t.method().equals(method))
                            .mapToDouble(Timing::ms).sorted().toArray();
                    double median = BenchmarkResults.percentile(values, .5);
                    csv.printRecord(group, method, values.length, decimal(median));
                    System.out.printf(Locale.ROOT, "第 %d 组 %-12s 中位数 %.3f ms%n", group, method, median);
                }
            }
        }
    }

    private static String decimal(double value) { return String.format(Locale.ROOT, "%.6f", value); }
}

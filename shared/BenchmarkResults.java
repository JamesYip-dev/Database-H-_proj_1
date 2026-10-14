import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.csv.*;

public class BenchmarkResults {
    public static void save(Path directory, List<Benchmark.Timing> timings) throws Exception {
        Files.createDirectories(directory);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
        Path file = directory.resolve(timestamp + ".csv");
        var format = CSVFormat.DEFAULT.builder()
                .setHeader("round", "phase", "order", "method", "elapsed_ms").get();
        try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW);
             var csv = new CSVPrinter(writer, format)) {
            for (var t : timings)
                csv.printRecord(t.round(), t.phase(), t.order(), t.method(),
                        String.format(Locale.ROOT, "%.6f", t.ms()));
        }
        System.out.println("全部核对通过；预热 " + Benchmark.WARMUP + " 轮，正式测量 " + Benchmark.RUNS + " 轮。");
        System.out.printf("%-20s %10s %10s %10s%n", "方法 / ms", "Median", "Q1", "Q3");
        for (String method : timings.stream().map(Benchmark.Timing::method).distinct().toList()) {
            double[] values = timings.stream()
                    .filter(t -> t.method().equals(method) && t.phase().equals("measured"))
                    .mapToDouble(Benchmark.Timing::ms).sorted().toArray();
            System.out.printf(Locale.ROOT, "%-20s %10.3f %10.3f %10.3f%n",
                    method, percentile(values, .5), percentile(values, .25), percentile(values, .75));
        }
        System.out.println("结果：" + file.toAbsolutePath());
    }

    // 用相邻样本线性插值，保持与前面实验的四分位数算法一致。
    public static double percentile(double[] sorted, double p) {
        double position = (sorted.length - 1) * p;
        int left = (int) position, right = (int) Math.ceil(position);
        return sorted[left] + (sorted[right] - sorted[left]) * (position - left);
    }
}

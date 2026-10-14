import java.nio.file.Path;
import java.util.*;

public class Benchmark {
    public static final int WARMUP = 10, RUNS = 30;
    @FunctionalInterface public interface Action { void run() throws Exception; }
    @FunctionalInterface public interface Operation<T> { T run() throws Exception; }
    @FunctionalInterface public interface Check<T> { void check(T result) throws Exception; }

    // 一个实验方法只需提供：每轮准备、被测操作、结果检查。
    public record Trial<T>(String name, Action prepare, Operation<T> operation, Check<T> verify) {}
    public record Timing(int round, String phase, int order, String method, double ms) {}

    public static <T> Trial<T> readOnly(String name, Operation<T> operation, Check<T> verify) {
        return new Trial<>(name, () -> {}, operation, verify);
    }

    public static void run(Path results, Trial<?>... methods) throws Exception {
        List<Timing> timings = new ArrayList<>();
        for (int round = 0; round < WARMUP + RUNS; round++) {
            // 轮换谁先执行，避免某一种方法总占据相同的运行顺序。
            for (int order = 0; order < methods.length; order++) {
                var method = methods[(round + order) % methods.length];
                double ms = measure(method);
                timings.add(new Timing(round + 1, round < WARMUP ? "warmup" : "measured",
                        order + 1, method.name(), ms));
            }
        }
        // 所有轮次核对通过后才保存；预热保留在 CSV 中，但不参与统计。
        BenchmarkResults.save(results, timings);
    }

    public static <T> double measure(Trial<T> trial) throws Exception {
        trial.prepare().run();
        long start = System.nanoTime();
        T result = trial.operation().run();
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        trial.verify().check(result); // 恢复数据和核对结果都在计时之外。
        return ms;
    }
}

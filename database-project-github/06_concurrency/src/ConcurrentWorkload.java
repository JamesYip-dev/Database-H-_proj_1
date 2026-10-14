import java.util.*;
import java.util.concurrent.*;

public class ConcurrentWorkload {
    public record Worker(int number, long reads, long writes, double seconds, double[] readTimes,
                         double[] writeTimes, long[] increments) {}
    public record Result(double seconds, List<Worker> workers) {
        public long reads() { return workers.stream().mapToLong(Worker::reads).sum(); }
        public long writes() { return workers.stream().mapToLong(Worker::writes).sum(); }
        public long operations() { return reads() + writes(); }
        public double median(boolean write) {
            double[] values = workers.stream().flatMapToDouble(w -> Arrays.stream(write ? w.writeTimes() : w.readTimes())).sorted().toArray();
            return BenchmarkResults.percentile(values, .5);
        }
    }
    private static class Times {
        private double[] values = new double[1024];
        private int size;
        void add(double ms) {
            if (size == values.length) values = Arrays.copyOf(values, size * 2);
            values[size++] = ms;
        }
        double[] array() { return Arrays.copyOf(values, size); }
    }

    public static Result run(CounterStore store, List<Integer> ids, int clients, int targets,
                             double duration, long seed) throws Exception {
        var sessions = new ArrayList<CounterStore.Session>();
        try {
            for (int i = 0; i < clients; i++) sessions.add(store.session());
            try (var pool = Executors.newFixedThreadPool(clients)) {
                var ready = new CountDownLatch(clients);
                var start = new CountDownLatch(1);
                long[] clock = new long[2];
                var futures = new ArrayList<Future<Worker>>();
                for (int number = 0; number < clients; number++) {
                    final int worker = number;
                    futures.add(pool.submit(() -> {
                        var random = new Random(seed + 104729L * worker);
                        var session = sessions.get(worker);
                        var readTimes = new Times(); var writeTimes = new Times();
                        long[] increments = new long[ids.size()], lastRead = new long[ids.size()];
                        long reads = 0, writes = 0;
                        ready.countDown(); start.await();
                        // 每个客户端有相同种子的请求流；每五次请求一写四读，完整小批次保证准确的 80/20 比例。
                        // 到期不再开始新批次，已开始的请求完成后才停止；吞吐量分母包含这段收尾时间。
                        while (System.nanoTime() < clock[1]) {
                            for (int operation = 0; operation < 5; operation++) {
                                int position = random.nextInt(targets), id = ids.get(position);
                                long begin = System.nanoTime();
                                if (operation == 0) {
                                    session.increment(id);
                                    writeTimes.add((System.nanoTime() - begin) / 1_000_000.0);
                                    increments[position]++; writes++;
                                } else {
                                    long value = session.read(id);
                                    readTimes.add((System.nanoTime() - begin) / 1_000_000.0);
                                    if (value < lastRead[position]) throw new IllegalStateException("同一客户端读取到回退计数");
                                    lastRead[position] = value; reads++;
                                }
                            }
                        }
                        double seconds = (System.nanoTime() - clock[0]) / 1_000_000_000.0;
                        return new Worker(worker + 1, reads, writes, seconds, readTimes.array(), writeTimes.array(), increments);
                    }));
                }
                ready.await();
                clock[0] = System.nanoTime(); clock[1] = clock[0] + (long) (duration * 1_000_000_000L);
                start.countDown();
                var completed = new ArrayList<Worker>();
                for (var future : futures) completed.add(future.get());
                // 用最晚完成请求的客户端时间，避免把主线程汇总和文件输出计入请求吞吐量。
                return new Result(completed.stream().mapToDouble(Worker::seconds).max().orElseThrow(), completed);
            }
        } finally {
            for (var session : sessions) session.close();
        }
    }

    public static void verify(CounterStore store, List<Integer> ids, Result result) throws Exception {
        Map<Integer, Long> expected = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            long value = 0;
            for (var worker : result.workers()) value += worker.increments()[i];
            expected.put(ids.get(i), value);
        }
        if (!expected.equals(store.snapshot())) throw new IllegalStateException("保存的逐电影计数不一致，存在丢失更新");
        if (result.reads() != 4 * result.writes() || result.writes() == 0)
            throw new IllegalStateException("工作负载比例或完成计数错误");
    }
}

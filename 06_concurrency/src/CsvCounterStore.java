import java.io.BufferedWriter;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class CsvCounterStore implements CounterStore {
    private final List<Integer> ids;
    private final Map<Integer, Integer> positions = new HashMap<>();
    private final AtomicReference<long[]> committed = new AtomicReference<>();
    private final ReentrantLock writerLock = new ReentrantLock();
    private final Path directory, file, pending;

    public CsvCounterStore(List<Integer> ids) throws Exception {
        this.ids = List.copyOf(ids);
        for (int i = 0; i < ids.size(); i++) positions.put(ids.get(i), i);
        directory = Files.createTempDirectory(Files.createDirectories(Path.of("06_concurrency/work")), "csv-");
        file = directory.resolve("views.csv"); pending = directory.resolve("pending.csv");
        reset();
    }

    @Override public Session session() {
        return new Session() {
            @Override public long read(int id) { return committed.get()[positions.get(id)]; }
            @Override public void increment(int id) throws Exception {
                // 读者查询不可变的已保存快照，无需等待刷盘；写者串行替换同一个 CSV。
                writerLock.lock();
                try {
                    long[] next = committed.get().clone();
                    next[positions.get(id)]++;
                    save(next);
                    committed.set(next); // 保存成功后才向本进程读者公布新版本。
                } finally { writerLock.unlock(); }
            }
        };
    }

    private void save(long[] values) throws Exception {
        // 数值列不需要引号；每次写完整快照，包含文件刷盘、原子替换和目录刷盘。
        try (var channel = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var writer = new BufferedWriter(Channels.newWriter(channel, StandardCharsets.UTF_8))) {
            writer.write("movieid,views\n");
            for (int i = 0; i < ids.size(); i++) {
                writer.write(Integer.toString(ids.get(i))); writer.write(',');
                writer.write(Long.toString(values[i])); writer.newLine();
            }
            writer.flush(); channel.force(true);
        }
        Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        try (var folder = FileChannel.open(directory, StandardOpenOption.READ)) { folder.force(true); }
    }

    @Override public void reset() throws Exception {
        long[] initial = new long[ids.size()]; save(initial); committed.set(initial);
    }

    @Override public Map<Integer, Long> snapshot() throws Exception {
        // 从实际保存的文件复读，防止仅核对内存而忽略写入结果。
        Map<Integer, Long> values = new HashMap<>();
        for (var row : CsvFiles.read(file)) values.put(Integer.parseInt(row.get("movieid")), Long.parseLong(row.get("views")));
        return values;
    }

    @Override public void close() throws Exception {
        Files.deleteIfExists(pending); Files.deleteIfExists(file); Files.delete(directory);
    }
}

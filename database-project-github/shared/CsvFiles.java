import java.io.BufferedWriter;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import org.apache.commons.csv.*;

public class CsvFiles {
    // 与导出文件一致：NULL 表示空值，带引号的 "NULL" 是普通文本。
    private static final CSVFormat BASE = CSVFormat.RFC4180.builder()
            .setNullString("NULL").setQuoteMode(QuoteMode.ALL_NON_NULL).get();
    private static final CSVFormat READ = BASE.builder().setHeader().setSkipHeaderRecord(true).get();

    public static List<Map<String, String>> read(Path file) throws Exception {
        return select(file, row -> true);
    }

    public static List<Map<String, String>> select(Path file, Predicate<Map<String, String>> condition) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (var reader = Files.newBufferedReader(file); var csv = READ.parse(reader)) {
            for (var record : csv) {
                var row = record.toMap();
                if (condition.test(row)) rows.add(row);
            }
        }
        return rows;
    }

    public static int rewrite(Path input, Path output, UnaryOperator<Map<String, String>> transform) throws Exception {
        int changed = 0;
        try (var reader = Files.newBufferedReader(input); var csv = READ.parse(reader);
             var channel = FileChannel.open(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var writer = new BufferedWriter(Channels.newWriter(channel, StandardCharsets.UTF_8));
             var printer = new CSVPrinter(writer, BASE.builder()
                     .setHeader(csv.getHeaderNames().toArray(String[]::new)).get())) {
            for (var record : csv) {
                var before = record.toMap();
                var after = transform.apply(before);
                if (!before.equals(after)) changed++;
                for (String column : csv.getHeaderNames()) printer.print(after.get(column));
                printer.println();
            }
            // 计时包含把文件内容刷到磁盘；仅关闭 writer 不等同于数据库提交。
            printer.flush();
            channel.force(true);
        }
        return changed;
    }
}

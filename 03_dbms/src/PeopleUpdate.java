import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;

public class PeopleUpdate {
    public static final Path INPUT = Path.of("data", "people.csv");
    public static final List<String> COLUMNS = List.of("first_name", "surname");
    public static final String FROM = "T", TO = "Ttt";
    public static final UnaryOperator<Map<String, String>> TRANSFORM = Rows.replace(COLUMNS, FROM, TO);

    public static int replaceFile(Path working) throws Exception {
        Path pending = working.resolveSibling("people_pending.csv");
        int changed = CsvFiles.rewrite(working, pending, TRANSFORM);
        // 单写入者：写完并刷盘后替换业务文件；此移动不保证故障时的原子性。
        Files.move(pending, working, StandardCopyOption.REPLACE_EXISTING);
        return changed;
    }

    public static List<Map<String, String>> expected(List<Map<String, String>> source) {
        return source.stream().map(TRANSFORM).toList();
    }

    public static long changedCount(List<Map<String, String>> source) {
        return source.stream().filter(row -> !row.equals(TRANSFORM.apply(row))).count();
    }

    public static void checkCount(long expected, int actual) {
        if (expected != actual) throw new IllegalStateException("更新行数不一致：" + actual + "，预期 " + expected);
    }
}

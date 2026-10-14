import java.util.*;
import java.util.function.UnaryOperator;

public class Rows {
    public static boolean contains(Map<String, String> row, String column, String text) {
        String value = row.get(column);
        return value != null && value.contains(text);
    }

    public static UnaryOperator<Map<String, String>> replace(List<String> columns, String from, String to) {
        return row -> {
            var changed = new LinkedHashMap<>(row);
            for (String column : columns)
                if (row.get(column) != null) changed.put(column, row.get(column).replace(from, to));
            return changed;
        };
    }

    // SQL 不保证返回顺序；核对完整行及重复次数，而不要求排列相同。
    public static <T> void check(List<T> expected, List<T> actual) {
        if (!frequencies(expected).equals(frequencies(actual)))
            throw new IllegalStateException("实验结果不一致：预期 " + expected.size() + " 行，实际 " + actual.size() + " 行");
    }

    private static <T> Map<T, Integer> frequencies(List<T> rows) {
        Map<T, Integer> counts = new HashMap<>();
        for (T row : rows) counts.merge(row, 1, Integer::sum);
        return counts;
    }
}

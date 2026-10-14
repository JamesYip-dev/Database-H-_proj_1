import java.nio.file.*;
import java.util.*;

public class JoinData {
    public record Task(String name, String continent, int start, int end) {}
    public static final List<Task> TASKS = List.of(
            new Task("Europe_1980_1999", "EUROPE", 1980, 1999),
            new Task("Asia_1980_1999", "ASIA", 1980, 1999),
            new Task("America_2000_2019", "AMERICA", 2000, 2019),
            new Task("All_1960_1979", "ALL", 1960, 1979));
    private static final Path INPUT = Path.of("05_join/data");
    public final Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> movies, people;
    private final Map<String, String> continents = new HashMap<>();
    private final Map<String, List<String>> actorsByMovie = new HashMap<>();

    public JoinData() throws Exception {
        for (String name : List.of("countries", "movies", "people", "credits"))
            tables.put(name, CsvFiles.read(INPUT.resolve(name + ".csv")));
        movies = index(tables.get("movies"), "movieid");
        people = index(tables.get("people"), "peopleid");
        for (var row : tables.get("countries")) continents.put(row.get("country_code"), row.get("continent"));
        for (var row : tables.get("credits")) {
            if (row.get("credited_as").equals("A"))
                actorsByMovie.computeIfAbsent(row.get("movieid"), id -> new ArrayList<>()).add(row.get("peopleid"));
        }
    }

    private static Map<String, Map<String, String>> index(List<Map<String, String>> rows, String key) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (var row : rows) result.put(row.get(key), row);
        return result;
    }

    public List<Map<String, String>> query(Task task) {
        Map<String, Integer> counts = new HashMap<>();
        // 先筛电影，再通过“电影编号→演员列表”索引关联；每次重新统计和排序，不缓存查询结果。
        for (var movie : movies.values()) {
            int year = Integer.parseInt(movie.get("year_released"));
            if (year < task.start() || year > task.end()) continue;
            if (!task.continent().equals("ALL") && !task.continent().equals(continents.get(movie.get("country")))) continue;
            for (String person : actorsByMovie.getOrDefault(movie.get("movieid"), List.of()))
                counts.merge(person, 1, Integer::sum);
        }
        return counts.entrySet().stream().sorted(Comparator
                .<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparingInt(e -> Integer.parseInt(e.getKey())))
                .limit(20).map(e -> {
                    var person = people.get(e.getKey());
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("peopleid", e.getKey()); row.put("first_name", person.get("first_name"));
                    row.put("surname", person.get("surname")); row.put("films", e.getValue().toString());
                    return row;
                }).toList();
    }
}

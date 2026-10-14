import java.nio.file.*;
import java.sql.Connection;

public class DbmsComparison {
    public static void main(String[] args) throws Exception {
        try (var pg = Database.connect("03_dbms/postgres.properties");
             var og = Database.connect("03_dbms/opengauss.properties")) {
            verifyInputs(pg, og);
            KeywordSearch.run(pg, og);
            for (int repeat = 0; repeat < 3; repeat++) update(pg, og);
        }
    }

    private static void verifyInputs(Connection pg, Connection og) throws Exception {
        var movies = CsvFiles.read(SearchData.INPUT);
        var people = CsvFiles.read(PeopleUpdate.INPUT);
        for (var c : new Connection[]{pg, og}) {
            Rows.check(movies, Database.query(c, SearchData.ALL_MOVIES));
            Rows.check(people, Database.query(c, "SELECT * FROM public.people"));
            Database.execute(c, "ANALYZE public.movies");
            Database.execute(c, "ANALYZE public.people");
        }
    }

    private static void update(Connection pg, Connection og) throws Exception {
        var source = CsvFiles.read(PeopleUpdate.INPUT);
        var expected = PeopleUpdate.expected(source);
        long changed = PeopleUpdate.changedCount(source);
        Path directory = Files.createTempDirectory(Files.createDirectories(Path.of("03_dbms/work")), "update-");
        Path working = directory.resolve("people.csv");
        try (var postgres = new PeopleDatabase(pg); var gauss = new PeopleDatabase(og)) {
            postgres.prepare(); gauss.prepare();
            // 三种方法都完成持久化更新；恢复与完整结果校验在计时外。
            Benchmark.run(Path.of("03_dbms/results/update"),
                    new Benchmark.Trial<>("Java CSV", () -> Files.copy(PeopleUpdate.INPUT, working, StandardCopyOption.REPLACE_EXISTING),
                            () -> PeopleUpdate.replaceFile(working), r -> {
                                PeopleUpdate.checkCount(changed, r); Rows.check(expected, CsvFiles.read(working));
                            }),
                    new Benchmark.Trial<>("PostgreSQL", postgres::restore, postgres::update, postgres::verify),
                    new Benchmark.Trial<>("openGauss", gauss::restore, gauss::update, gauss::verify));
        } finally {
            Files.deleteIfExists(directory.resolve("people_pending.csv"));
            Files.deleteIfExists(working); Files.delete(directory);
        }
    }
}

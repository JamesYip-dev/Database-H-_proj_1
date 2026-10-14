import java.util.*;

public interface CounterStore extends AutoCloseable {
    interface Session extends AutoCloseable {
        long read(int id) throws Exception;
        void increment(int id) throws Exception;
        @Override default void close() throws Exception {}
    }
    Session session() throws Exception;
    void reset() throws Exception;
    Map<Integer, Long> snapshot() throws Exception;
    @Override void close() throws Exception;
}

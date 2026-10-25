package ser.cliente.infrastructure;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LogUtils {
    private static final Logger LOGGER = Logger.getLogger("ClienteFront");
    private static final int MAX_ENTRIES = 1_000;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Deque<String> LOG_BUFFER = new ArrayDeque<>();

    private LogUtils() {
    }

    public static void info(String message) {
        LOGGER.log(Level.INFO, message);
        append("INFO", message);
    }

    public static void warn(String message) {
        LOGGER.log(Level.WARNING, message);
        append("WARN", message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
        append("ERROR", message + " - " + throwable.getMessage());
    }

    public static synchronized List<String> recentLogs() {
        return new ArrayList<>(LOG_BUFFER);
    }

    private static synchronized void append(String level, String message) {
        String line = "%s [%s] %s".formatted(LocalDateTime.now().format(FORMATTER), level, message);
        LOG_BUFFER.addLast(line);
        while (LOG_BUFFER.size() > MAX_ENTRIES) {
            LOG_BUFFER.removeFirst();
        }
    }
}
package ser.cliente.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FilesUtils {
    private FilesUtils() {
    }

    public static String extension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1);
    }

    public static double toMb(long sizeInBytes) {
        double mb = sizeInBytes / 1_048_576.0;
        return Math.round(mb * 100.0) / 100.0;
    }

    public static String toMbText(long sizeInBytes) {
        return "%.2f mb".formatted(toMb(sizeInBytes));
    }

    public static Path ensureDirectory(String rawPath) throws IOException {
        Path path = Paths.get(rawPath.trim()).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }
}
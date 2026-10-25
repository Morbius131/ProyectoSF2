package ser.cliente.infrastructure;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ser.cliente.model.FileTransferResult;

public final class FileTransferHandler {
    private static final int CHUNK_SIZE_BYTES = 4 * 1024 * 1024;

    private FileTransferHandler() {
    }

    public static List<FileTransferResult> sendFilesConcurrently(List<File> files, int maxParallelism) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        int workers = Math.max(1, Math.min(maxParallelism, files.size()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<FileTransferResult>> futures = new ArrayList<>();

        for (File file : files) {
            futures.add(executor.submit(() -> streamFile(file)));
        }

        List<FileTransferResult> results = new ArrayList<>();
        for (Future<FileTransferResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                results.add(new FileTransferResult("desconocido", 0, false, "Transferencia interrumpida"));
            } catch (ExecutionException ex) {
                results.add(new FileTransferResult("desconocido", 0, false, ex.getCause().getMessage()));
            }
        }

        executor.shutdown();
        return results;
    }

    private static FileTransferResult streamFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return new FileTransferResult("archivo-invalido", 0, false, "Archivo invalido");
        }

        long bytesSent = 0;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
             OutputStream sink = OutputStream.nullOutputStream()) {
            byte[] buffer = new byte[CHUNK_SIZE_BYTES];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                sink.write(buffer, 0, bytesRead);
                bytesSent += bytesRead;
            }
            return new FileTransferResult(file.getName(), bytesSent, true, "Transferencia completada");
        } catch (IOException ex) {
            return new FileTransferResult(file.getName(), bytesSent, false, ex.getMessage());
        }
    }
}
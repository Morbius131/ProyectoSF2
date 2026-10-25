package Cliente_Servidor.V1.Chunk;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Registro thread-safe de transferencias chunked en curso.
 * Un uploadId único identifica cada transferencia.
 */
@Component
public class ChunkRegistry {

    private final ConcurrentHashMap<String, ChunkBuffer> activos = new ConcurrentHashMap<>();

    /** Genera un uploadId único y registra el buffer. */
    public String registrar(ChunkBuffer buffer) {
        activos.put(buffer.getUploadId(), buffer);
        return buffer.getUploadId();
    }

    /** Genera un uploadId aleatorio (util para el controlador). */
    public static String nuevoUploadId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public ChunkBuffer obtener(String uploadId) {
        return activos.get(uploadId);
    }

    public void eliminar(String uploadId) {
        activos.remove(uploadId);
    }

    public boolean existe(String uploadId) {
        return activos.containsKey(uploadId);
    }
}

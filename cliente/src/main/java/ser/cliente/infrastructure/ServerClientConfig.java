package ser.cliente.infrastructure;

final class ServerClientConfig {
    static final int TCP_CHUNK_SIZE = 4 * 1024 * 1024;
    static final long TCP_DIRECT_UPLOAD_LIMIT = 512L * 1024;
    static final int UDP_CHUNK_SIZE = 32_768;
    static final int UDP_BATCH_SIZE = 500;
    static final long UDP_BATCH_PAUSE_MS = 20;
    static final int UDP_END_TIMEOUT_MS = 300_000;
    static final String DOWNLOAD_DIR = "documentos_recibidos";

    private ServerClientConfig() {
    }
}

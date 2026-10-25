package ser.cliente.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ser.cliente.communication.ICommunication;
import ser.cliente.communication.TCPCommunication;
import ser.cliente.communication.UDPCommunications;
import ser.cliente.model.Protocol;
import ser.cliente.model.Request;

public final class ConnectionManager {
    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool();

    private static ICommunication communication;
    private static Protocol activeProtocol;
    private static String host;
    private static int port;
    private static String userName;

    private ConnectionManager() {}

    public static synchronized boolean connect(String hostValue, int portValue, Protocol protocol) {
        disconnect();
        try {
            communication = FactoryCommunication.create(protocol);
            communication.connect(hostValue, portValue);
            activeProtocol = protocol;
            host = hostValue;
            port = portValue;
            LogUtils.info("Conexion establecida %s://%s:%d".formatted(protocol.name(), hostValue, portValue));
            return true;
        } catch (IOException ex) {
            LogUtils.error("No fue posible conectar", ex);
            communication = null;
            activeProtocol = null;
            return false;
        }
    }

    public static synchronized void disconnect() {
        if (communication != null) communication.disconnect();
        communication = null;
        activeProtocol = null;
        host = null;
        port = 0;
    }

    public static synchronized boolean isConnected() {
        return communication != null && communication.isConnected();
    }

    public static synchronized String description() {
        if (!isConnected() || activeProtocol == null || host == null) return "Sin conexion";
        return "%s://%s:%d".formatted(activeProtocol.name(), host, port);
    }

    public static synchronized Protocol getProtocol() { return activeProtocol; }
    public static synchronized String getUserName() { return userName; }
    public static synchronized void setUserName(String name) { userName = name; }

    public static synchronized ICommunication getCommunication() { return communication; }

    /**
     * Send a JSON map request directly — used by ServerClient helper
     */
    public static synchronized Optional<String> sendJson(Map<String, Object> req) throws IOException {
        if (communication == null || !communication.isConnected()) return Optional.empty();
        if (communication instanceof TCPCommunication tcp) {
            return tcp.sendJson(req);
        } else if (communication instanceof UDPCommunications udp) {
            return udp.sendJson(req);
        }
        return Optional.empty();
    }

    // Legacy method used by old code - now just logs
    public static Optional<String> send(Request request) throws IOException {
        LogUtils.info("Legacy send called: " + request.type());
        return Optional.empty();
    }

    public static CompletableFuture<Optional<String>> sendAsync(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(request);
            } catch (IOException ex) {
                LogUtils.error("Error al enviar solicitud", ex);
                return Optional.empty();
            }
        }, IO_EXECUTOR);
    }
}

package ser.cliente.communication;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public final class TCPCommunication implements ICommunication {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 600_000;

    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private final ObjectMapper mapper;

    public TCPCommunication() {
        this.mapper = new ObjectMapper();
        this.mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build());
    }

    public ObjectMapper getMapper() { return mapper; }
    public BufferedReader getReader() { return reader; }
    public BufferedWriter getWriter() { return writer; }

    @Override
    public void connect(String host, int port) throws IOException {
        disconnect();
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void disconnect() {
        closeQuietly(reader);
        closeQuietly(writer);
        closeQuietly(socket);
        reader = null;
        writer = null;
        socket = null;
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public synchronized Optional<String> send(String payload) throws IOException {
        if (!isConnected() || writer == null || reader == null) {
            throw new IOException("No hay conexion TCP activa");
        }
        writer.write(payload);
        writer.newLine();
        writer.flush();
        return Optional.ofNullable(reader.readLine());
    }

    public synchronized Optional<String> sendJson(Map<String, Object> req) throws IOException {
        return send(mapper.writeValueAsString(req));
    }

    public synchronized void writeJson(Map<String, Object> req) throws IOException {
        if (!isConnected()) throw new IOException("No hay conexion TCP activa");
        writer.write(mapper.writeValueAsString(req));
        writer.newLine();
        writer.flush();
    }

    public synchronized String readLine() throws IOException {
        if (reader == null) throw new IOException("No hay conexion TCP activa");
        return reader.readLine();
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Exception ignored) {}
    }
}

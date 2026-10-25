package ser.cliente.communication;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UDPCommunications implements ICommunication {
    private static final int READ_TIMEOUT_MS = 300_000;
    private static final int BUFFER_SIZE = 524_288; // 512 KB

    private DatagramSocket socket;
    private InetAddress remoteAddress;
    private int remotePort;
    private final ObjectMapper mapper;

    public UDPCommunications() {
        this.mapper = new ObjectMapper();
        this.mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build());
    }

    public ObjectMapper getMapper() { return mapper; }
    public DatagramSocket getSocket() { return socket; }
    public InetAddress getRemoteAddress() { return remoteAddress; }
    public int getRemotePort() { return remotePort; }

    @Override
    public void connect(String host, int port) throws IOException {
        disconnect();
        remoteAddress = InetAddress.getByName(host);
        remotePort = port;
        socket = new DatagramSocket();
        socket.setSoTimeout(READ_TIMEOUT_MS);
        socket.setSendBufferSize(4 * 1024 * 1024);
        socket.setReceiveBufferSize(32 * 1024 * 1024);
    }

    @Override
    public void disconnect() {
        if (socket != null && !socket.isClosed()) socket.close();
        socket = null;
        remoteAddress = null;
        remotePort = 0;
    }

    @Override
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && remoteAddress != null;
    }

    @Override
    public Optional<String> send(String payload) throws IOException {
        if (!isConnected()) throw new IOException("No hay conexion UDP activa");
        byte[] outgoing = payload.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(outgoing, outgoing.length, remoteAddress, remotePort));
        byte[] buf = new byte[BUFFER_SIZE];
        DatagramPacket response = new DatagramPacket(buf, buf.length);
        socket.receive(response);
        return Optional.of(new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8));
    }

    public Optional<String> sendJson(Map<String, Object> req) throws IOException {
        return send(mapper.writeValueAsString(req));
    }

    public void sendJsonNoReply(Map<String, Object> req) throws IOException {
        if (!isConnected()) throw new IOException("No hay conexion UDP activa");
        byte[] b = mapper.writeValueAsString(req).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(b, b.length, remoteAddress, remotePort));
    }

    public List<String> sendJsonMultiLine(Map<String, Object> req) throws IOException {
        if (!isConnected()) throw new IOException("No hay conexion UDP activa");
        byte[] b = mapper.writeValueAsString(req).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(b, b.length, remoteAddress, remotePort));

        List<String> lines = new ArrayList<>();
        byte[] buf = new byte[BUFFER_SIZE];
        StringBuilder acum = new StringBuilder();
        boolean fin = false;

        while (!fin) {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);
            acum.append(new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8));

            int idx;
            while ((idx = acum.indexOf("\n")) >= 0) {
                String line = acum.substring(0, idx).trim();
                acum.delete(0, idx + 1);
                if (line.isEmpty()) continue;
                if (line.contains("\"FIN\"")) { fin = true; break; }
                lines.add(line);
            }

            if (!fin && acum.length() > 0) {
                String rest = acum.toString().trim();
                if (!rest.isEmpty()) {
                    if (rest.contains("\"FIN\"")) fin = true;
                    else lines.add(rest);
                    acum.setLength(0);
                }
            }
        }
        return lines;
    }

    public void setTimeout(int ms) {
        if (socket != null) {
            try { socket.setSoTimeout(ms); } catch (Exception ignored) {}
        }
    }
}

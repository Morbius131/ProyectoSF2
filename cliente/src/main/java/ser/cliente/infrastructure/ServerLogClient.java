package ser.cliente.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ser.cliente.communication.TCPCommunication;
import ser.cliente.communication.UDPCommunications;
import ser.cliente.model.Protocol;

final class ServerLogClient {

    private ServerLogClient() {
    }

    static ServerClient.ServerResponse getLogs(String serverId) {
        try {
            Map<String, Object> request = buildRequest(serverId);
            if (ConnectionManager.getProtocol() == Protocol.UDP) {
                return getLogsUDP(request);
            }
            return getLogsTCP(request);
        } catch (Exception e) {
            System.err.println("[ERROR getLogs] " + e.getMessage());
            return ServerClient.ServerResponse.error("Error obteniendo logs: " + e.getMessage());
        }
    }

    private static Map<String, Object> buildRequest(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return ServerJson.request("LOG", "LIST", Map.of());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subtipo", "LOG");
        data.put("servidorId", serverId);
        return ServerJson.request("SERVIDOR", "GET", data);
    }

    private static ServerClient.ServerResponse getLogsTCP(Map<String, Object> request) throws Exception {
        TCPCommunication tcp = ServerTransport.tcp();
        if (tcp == null) {
            return ServerClient.ServerResponse.error("No hay conexion TCP");
        }

        synchronized (tcp) {
            tcp.writeJson(request);
            String headerLine = tcp.readLine();
            if (headerLine == null) {
                return ServerClient.ServerResponse.error("Sin respuesta");
            }

            Map<String, Object> header = ServerJson.readMap(headerLine);
            if (!"OK".equals(header.get("status"))) {
                return ServerClient.ServerResponse.error("" + header.get("message"));
            }

            List<Map<String, Object>> logs = new ArrayList<>();
            String line;
            while ((line = tcp.readLine()) != null) {
                Map<String, Object> entry = ServerJson.readMap(line);
                if ("FIN".equals(entry.get("status"))) {
                    break;
                }
                logs.add(entry);
            }
            return ServerClient.ServerResponse.ok(logs);
        }
    }

    private static ServerClient.ServerResponse getLogsUDP(Map<String, Object> request) throws Exception {
        UDPCommunications udp = ServerTransport.udp();
        if (udp == null) {
            return ServerClient.ServerResponse.error("No hay conexion UDP");
        }

        List<String> lines = udp.sendJsonMultiLine(request);
        if (lines.isEmpty()) {
            return ServerClient.ServerResponse.ok(List.of());
        }

        List<Map<String, Object>> logs = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            logs.add(ServerJson.readMap(lines.get(i)));
        }
        return ServerClient.ServerResponse.ok(logs);
    }
}

package ser.cliente.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ser.cliente.infrastructure.ConnectionManager;
import ser.cliente.infrastructure.ServerClient;
import ser.cliente.model.ClientInfo;
import ser.cliente.model.MessageType;
import ser.cliente.model.Protocol;
import ser.cliente.model.Response;
import ser.cliente.model.ServerInfo;

public final class ConnectionController extends AbstractController {
    public ConnectionController() {
        super(MessageType.CONNECTION_STATUS, MessageType.LIST_CLIENTS, MessageType.LIST_SERVERS);
    }

    public void connect(String ip, int port, Protocol protocol, String userName) {
        boolean connected = ConnectionManager.connect(ip, port, protocol);
        if (!connected) {
            publish(Response.failure(MessageType.CONNECTION_STATUS, "No se pudo establecer la conexion"));
            return;
        }
        // Register user with server
        if (userName != null && !userName.isBlank()) {
            ServerClient.ServerResponse regResp = ServerClient.registerUser(userName);
            if (!regResp.success()) {
                System.err.println("[WARN] Register user: " + regResp.message());
            }
        }
        publish(Response.success(MessageType.CONNECTION_STATUS,
                "Conectado a %s".formatted(ConnectionManager.description()), null));
    }

    public void connect(String ip, int port, Protocol protocol) {
        connect(ip, port, protocol, null);
    }

    public void listConnectedClients() {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.LIST_CLIENTS, "No hay conexion activa con el servidor"));
            return;
        }
        ServerClient.ServerResponse resp = ServerClient.listConnectedClients();
        if (!resp.success()) {
            publish(Response.failure(MessageType.LIST_CLIENTS, resp.message()));
            return;
        }
        List<ClientInfo> clients = parseClients(resp.data());
        publish(Response.success(MessageType.LIST_CLIENTS, "Clientes obtenidos: " + clients.size(), clients));
    }

    public void listServers() {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.LIST_SERVERS, "No hay conexion activa con el servidor"));
            return;
        }
        ServerClient.ServerResponse resp = ServerClient.listServers();
        if (!resp.success()) {
            publish(Response.failure(MessageType.LIST_SERVERS, resp.message()));
            return;
        }
        List<ServerInfo> servers = parseServers(resp.data());
        publish(Response.success(MessageType.LIST_SERVERS, "Servidores obtenidos: " + servers.size(), servers));
    }

    @SuppressWarnings("unchecked")
    private List<ClientInfo> parseClients(Object data) {
        List<ClientInfo> list = new ArrayList<>();
        if (data instanceof List<?> rawList) {
            int fallbackId = 1;
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    Object idObj = map.get("id");
                    int id = idObj instanceof Number n ? n.intValue() : fallbackId++;
                    String nombre = toText(map.get("usuario"));
                    if (nombre.isBlank()) nombre = toText(map.get("nombre"));
                    if (nombre.isBlank()) nombre = toText(map.get("usuarioNombre"));
                    if (nombre.isBlank()) nombre = "?";
                    String ip = toText(map.get("ip"));
                    if (ip.isBlank()) ip = "?";
                    String servidor = toText(map.get("servidor"));
                    if (servidor.isBlank()) servidor = "local";
                    list.add(new ClientInfo(id, nombre, ip, servidor));
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<ServerInfo> parseServers(Object data) {
        List<ServerInfo> list = new ArrayList<>();
        if (data instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    String id = toText(map.get("id"));
                    String nombre = toText(map.get("nombre"));
                    String host = toText(map.get("host"));
                    int puerto = map.get("puerto") instanceof Number n ? n.intValue() : 0;
                    boolean conectado = Boolean.TRUE.equals(map.get("conectado"));
                    List<String> clientes = new ArrayList<>();
                    if (map.get("clientesConectados") instanceof List<?> cl) {
                        for (Object c : cl) clientes.add(c != null ? c.toString() : "");
                    }
                    list.add(new ServerInfo(id, nombre, host, puerto, conectado, clientes));
                }
            }
        }
        return list;
    }

    private String toText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    @SuppressWarnings("unchecked")
    public static List<ClientInfo> extractClients(Response response) {
        if (response.payload() instanceof List<?> list) {
            return (List<ClientInfo>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<ServerInfo> extractServers(Response response) {
        if (response.payload() instanceof List<?> list) {
            return (List<ServerInfo>) list;
        }
        return List.of();
    }
}

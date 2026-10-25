package ser.cliente.infrastructure;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class ServerApiClient {

    private ServerApiClient() {
    }

    static ServerClient.ServerResponse registerUser(String nombre) {
        try {
            Map<String, Object> request = ServerJson.request("USUARIO", "POST",
                    Map.of("nombre", nombre, "tipoUsuario", "NORMAL"));
            String response = ServerTransport.sendSingle(request);
            ConnectionManager.setUserName(nombre);
            return ServerJson.parseResponse(response);
        } catch (Exception e) {
            return ServerClient.ServerResponse.error("Error registrando usuario: " + e.getMessage());
        }
    }

    static ServerClient.ServerResponse listConnectedClients() {
        return sendSimple(
                ServerJson.request("USUARIO", "LIST", Map.of()),
                "Error listando clientes"
        );
    }

    static ServerClient.ServerResponse listDocuments() {
        return sendSimple(
                ServerJson.request("ARCHIVO", "LIST", dataWithCurrentUser()),
                "Error listando documentos"
        );
    }

    static ServerClient.ServerResponse listMessages() {
        return sendSimple(
                ServerJson.request("MENSAJE", "LIST", dataWithCurrentUser()),
                "Error listando mensajes"
        );
    }

    static ServerClient.ServerResponse sendMessage(
            String asunto,
            String contenido,
            String owner,
            String destinatario
    ) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tipo", "MENSAJE");
            data.put("asunto", asunto);
            data.put("contenido", contenido);
            data.put("tipoPropietario", "LOCAL");
            data.put("usuarioNombre", owner != null ? owner : ConnectionManager.getUserName());
            data.put("usuarioId", 0);
            addDestinatario(data, destinatario);

            Map<String, Object> request = ServerJson.request("MENSAJE", "POST", data);
            ServerClient.ServerResponse result = ServerJson.parseResponse(ServerTransport.sendSingle(request));
            if (ServerResponses.isLockWaitTimeout(result)) {
                result = ServerJson.parseResponse(ServerTransport.sendSingle(request));
            }
            return result;
        } catch (Exception e) {
            return ServerClient.ServerResponse.error("Error enviando mensaje: " + e.getMessage());
        }
    }

    static ServerClient.ServerResponse listServers() {
        return sendSimple(
                ServerJson.request("SERVIDOR", "LIST", Map.of()),
                "Error listando servidores"
        );
    }

    static ServerClient.ServerResponse identifyEmotion(byte[] imageBytes, String imageName, String userName) {
        try {
            if (imageBytes == null || imageBytes.length == 0) {
                return ServerClient.ServerResponse.error("La imagen procesada esta vacia");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("imagenBase64", Base64.getEncoder().encodeToString(imageBytes));
            if (imageName != null && !imageName.isBlank()) {
                data.put("nombre", stripExtension(imageName));
            }

            String owner = userName != null && !userName.isBlank() ? userName : ConnectionManager.getUserName();
            if (owner != null && !owner.isBlank()) {
                data.put("usuarioNombre", owner);
            }

            Map<String, Object> request = ServerJson.request("EMOCION", "POST", data);
            return ServerJson.parseResponse(ServerTransport.sendSingle(request));
        } catch (Exception e) {
            return ServerClient.ServerResponse.error("Error identificando emocion: " + e.getMessage());
        }
    }

    static ServerClient.ServerResponse identifyEmotionById(long id) {
        return sendSimple(
                ServerJson.request("EMOCION", "GET", Map.of("id", id)),
                "Error identificando emocion por ID"
        );
    }

    static ServerClient.ServerResponse listFaceImages() {
        return sendSimple(
                ServerJson.request("EMOCION", "LIST", Map.of()),
                "Error listando imagenes de rostro"
        );
    }

    static ServerClient.ServerResponse getServerInfo(String serverId) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("subtipo", "INFO");
            if (serverId != null && !serverId.isBlank()) {
                data.put("servidorId", serverId);
            }
            Map<String, Object> request = ServerJson.request("SERVIDOR", "GET", data);
            return ServerJson.parseResponse(ServerTransport.sendSingle(request));
        } catch (Exception e) {
            return ServerClient.ServerResponse.error("Error obteniendo info del servidor: " + e.getMessage());
        }
    }

    private static ServerClient.ServerResponse sendSimple(Map<String, Object> request, String errorPrefix) {
        try {
            return ServerJson.parseResponse(ServerTransport.sendSingle(request));
        } catch (Exception e) {
            return ServerClient.ServerResponse.error(errorPrefix + ": " + e.getMessage());
        }
    }

    private static Map<String, Object> dataWithCurrentUser() {
        Map<String, Object> data = new LinkedHashMap<>();
        String userName = ConnectionManager.getUserName();
        if (userName != null && !userName.isBlank()) {
            data.put("usuarioNombre", userName);
        }
        return data;
    }

    static void addDestinatario(Map<String, Object> data, String destinatario) {
        if (destinatario != null && !destinatario.isBlank()) {
            data.put("destinatario", destinatario);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }
}

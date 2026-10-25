package ser.cliente.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ser.cliente.dao.DAO;
import ser.cliente.infrastructure.ConnectionManager;
import ser.cliente.infrastructure.ServerClient;
import ser.cliente.model.*;

public final class MessageController extends AbstractController {
    public MessageController() {
        super(MessageType.LIST_DOCUMENTS, MessageType.SEND_MESSAGE, MessageType.RECEIVE_MESSAGE);
    }

    public void listDocuments() {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.LIST_DOCUMENTS, "No hay conexion activa con el servidor"));
            return;
        }
        ServerClient.ServerResponse filesResp = ServerClient.listDocuments();
        if (!filesResp.success()) {
            publish(Response.failure(MessageType.LIST_DOCUMENTS, filesResp.message()));
            return;
        }
        ServerClient.ServerResponse messagesResp = ServerClient.listMessages();
        if (!messagesResp.success()) {
            publish(Response.failure(MessageType.LIST_DOCUMENTS, messagesResp.message()));
            return;
        }
        List<FileDocument> archivos = parseFiles(filesResp.data());
        List<MessageDocument> mensajes = parseMessages(messagesResp.data());
        DocumentCatalog catalog = new DocumentCatalog(archivos, mensajes);
        publish(Response.success(MessageType.LIST_DOCUMENTS, "Documentos cargados", catalog));
    }

    public void sendMessage(String message, String owner, String asuntoParam, String destinatario) {
        if (message == null || message.trim().isEmpty()) {
            publish(Response.failure(MessageType.SEND_MESSAGE, "Debe escribir un mensaje para enviar"));
            return;
        }
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.SEND_MESSAGE, "No hay conexion activa con el servidor"));
            return;
        }
        String asunto = (asuntoParam != null && !asuntoParam.isBlank()) ? asuntoParam : "Mensaje";
        ServerClient.ServerResponse resp = ServerClient.sendMessage(asunto, message, owner, destinatario);
        if (!resp.success()) {
            publish(Response.failure(MessageType.SEND_MESSAGE, resp.message()));
            return;
        }
        DAO.saveSentMessage(message, owner != null ? owner : "");
        publish(Response.success(MessageType.SEND_MESSAGE, "Mensaje enviado al servidor correctamente", null));
    }

    public void sendMessage(String message, String owner) {
        sendMessage(message, owner, null, null);
    }

    public void receiveMessage(int messageId) {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.RECEIVE_MESSAGE, "No hay conexion activa con el servidor"));
            return;
        }
        ServerClient.ServerResponse resp = ServerClient.listMessages();
        if (!resp.success()) {
            publish(Response.failure(MessageType.RECEIVE_MESSAGE, resp.message()));
            return;
        }
        for (MessageDocument md : parseMessages(resp.data())) {
            if (md.id() == messageId) {
                publish(Response.success(MessageType.RECEIVE_MESSAGE, "Mensaje recibido", md.contenido()));
                return;
            }
        }
        publish(Response.failure(MessageType.RECEIVE_MESSAGE, "Mensaje con ID " + messageId + " no encontrado"));
    }

    @SuppressWarnings("unchecked")
    private List<FileDocument> parseFiles(Object data) {
        List<FileDocument> archivos = new ArrayList<>();
        Object normalized = unwrapList(data);
        if (normalized instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    int id = map.containsKey("id") ? ((Number) map.get("id")).intValue() : 0;
                    String nombre = textOrFallback(map.get("nombreVisible"), map.get("nombre"), "?");
                    String ext = textOrFallback(map.get("extension"), "?");
                    double size = 0;
                    if (map.containsKey("tamanio")) size = ((Number) map.get("tamanio")).doubleValue() / 1_048_576.0;
                    String prop = textOrFallback(map.get("tipoPropietario"), "LOCAL");
                    archivos.add(new FileDocument(id, nombre, ext, Math.round(size * 100.0) / 100.0, prop));
                }
            }
        }
        return archivos;
    }

    @SuppressWarnings("unchecked")
    private List<MessageDocument> parseMessages(Object data) {
        List<MessageDocument> mensajes = new ArrayList<>();
        Object normalized = unwrapList(data);
        if (normalized instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    int id = map.containsKey("id") ? ((Number) map.get("id")).intValue() : 0;
                    String contenido = textOrFallback(map.get("contenido"), map.get("asunto"), "");
                    mensajes.add(new MessageDocument(id, contenido));
                }
            }
        }
        return mensajes;
    }

    @SuppressWarnings("unchecked")
    private Object unwrapList(Object data) {
        if (data instanceof Map<?, ?> wrapper) {
            Map<String, Object> map = (Map<String, Object>) wrapper;
            Object nested = map.get("documentos");
            if (nested instanceof List<?>) {
                return nested;
            }
            Object directData = map.get("data");
            if (directData instanceof List<?>) {
                return directData;
            }
        }
        return data;
    }

    private String textOrFallback(Object value, Object fallback, String defaultValue) {
        String text = textOrFallback(value, defaultValue);
        if (!text.equals(defaultValue)) return text;
        return textOrFallback(fallback, defaultValue);
    }

    private String textOrFallback(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }
}

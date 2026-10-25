package ser.cliente.controller;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import ser.cliente.infrastructure.ConnectionManager;
import ser.cliente.infrastructure.ServerClient;
import ser.cliente.model.MessageType;
import ser.cliente.model.Response;

public final class LogController extends AbstractController {
    public LogController() {
        super(MessageType.RECEIVE_LOGS);
    }

    public void receiveLogs(String saveDirectory) {
        receiveLogs(saveDirectory, null);
    }

    public void receiveLogs(String saveDirectory, String serverId) {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.RECEIVE_LOGS, "No hay conexion activa con el servidor"));
            return;
        }
        String target = (serverId != null && !serverId.isBlank()) ? serverId : "local";
        publish(Response.success(MessageType.RECEIVE_LOGS, "Obteniendo logs del servidor " + target + "...", null));

        ServerClient.ServerResponse resp = ServerClient.getLogs(serverId);
        if (!resp.success()) {
            System.err.println("[ERROR getLogs] " + resp.message());
            publish(Response.failure(MessageType.RECEIVE_LOGS, resp.message()));
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = resp.data() instanceof List<?> l ?
                (List<Map<String, Object>>) l : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("Total logs: ").append(logs.size()).append("\n");
        for (Map<String, Object> entry : logs) {
            String fechaHora = entry.getOrDefault("fechaHora", "").toString();
            String tipo = entry.getOrDefault("tipo", "").toString();
            String usuario = entry.getOrDefault("usuario", "(sistema)").toString();
            String mensaje = entry.getOrDefault("mensaje", "").toString();
            String line = "[%s] %-10s %-12s %s".formatted(
                    fechaHora.length() > 19 ? fechaHora.substring(0, 19) : fechaHora,
                    tipo, usuario.isEmpty() ? "(sistema)" : usuario, mensaje);
            sb.append(line).append("\n");
        }

        String content = sb.toString();
        String savedPath = null;
        if (saveDirectory != null && !saveDirectory.isBlank()) {
            try {
                Path dir = Paths.get(saveDirectory);
                Files.createDirectories(dir);
                Path file = dir.resolve("logs.txt");
                Files.writeString(file, content);
                savedPath = file.toAbsolutePath().toString();
                System.out.println("[LOGS] Guardado en: " + savedPath);
            } catch (IOException e) {
                System.err.println("[ERROR] No se pudo guardar logs: " + e.getMessage());
            }
        }

        String msg = savedPath != null ?
                "Logs guardados en: " + savedPath + "\n\n" + content :
                "Logs recibidos:\n\n" + content;
        publish(Response.success(MessageType.RECEIVE_LOGS, msg, savedPath));
    }
}

package ser.cliente.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ser.cliente.communication.TCPCommunication;
import ser.cliente.communication.UDPCommunications;
import ser.cliente.model.Protocol;

final class ServerFileDownloadClient {

    private ServerFileDownloadClient() {
    }

    static ServerClient.ServerResponse downloadDocument(
            long id,
            String saveDirectory,
            ServerClient.DownloadCallback onDone
    ) {
        return downloadDocument(id, saveDirectory, false, "NORMAL", onDone,
                "Error descargando documento");
    }

    static ServerClient.ServerResponse downloadDocumentHash(long id) {
        try {
            Map<String, Object> request = fileGetRequest(id, "SOLO_HASH");
            return ServerJson.parseResponse(ServerTransport.sendSingle(request));
        } catch (Exception e) {
            return ServerClient.ServerResponse.error("Error obteniendo hash: " + e.getMessage());
        }
    }

    static ServerClient.ServerResponse downloadDocumentEncrypted(
            long id,
            String saveDirectory,
            ServerClient.DownloadCallback onDone
    ) {
        return downloadDocument(id, saveDirectory, true, "ENCRIPTADO", onDone,
                "Error descargando doc encriptado");
    }

    private static ServerClient.ServerResponse downloadDocument(
            long id,
            String saveDirectory,
            boolean encrypted,
            String action,
            ServerClient.DownloadCallback onDone,
            String errorPrefix
    ) {
        try {
            Files.createDirectories(Paths.get(ServerClientConfig.DOWNLOAD_DIR));
            Map<String, Object> request = fileGetRequest(id, action);
            if (ConnectionManager.getProtocol() == Protocol.UDP) {
                return downloadUDP(request, saveDirectory, encrypted, onDone);
            }
            return downloadTCP(request, saveDirectory, encrypted, onDone);
        } catch (Exception e) {
            System.err.println("[ERROR downloadDocument] " + e.getMessage());
            return ServerClient.ServerResponse.error(errorPrefix + ": " + e.getMessage());
        }
    }

    private static ServerClient.ServerResponse downloadTCP(
            Map<String, Object> request,
            String saveDirectory,
            boolean encrypted,
            ServerClient.DownloadCallback onDone
    ) throws Exception {
        TCPCommunication tcp = ServerTransport.tcp();
        if (tcp == null) {
            return ServerClient.ServerResponse.error("No hay conexion TCP");
        }

        synchronized (tcp) {
            tcp.writeJson(request);
            String headerLine = tcp.readLine();
            if (headerLine == null) {
                return ServerClient.ServerResponse.error("Sin respuesta del servidor");
            }

            Map<String, Object> header = ServerJson.readMap(headerLine);
            if (!"OK".equals(header.get("status"))) {
                return ServerClient.ServerResponse.error("" + header.get("message"));
            }

            int totalChunks = ((Number) header.get("totalChunks")).intValue();
            String visibleName = (String) header.getOrDefault("nombreVisible", "documento");
            String hash = (String) header.getOrDefault("hash", "");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                String chunkLine = tcp.readLine();
                Map<String, Object> chunkMap = ServerJson.readMap(chunkLine);
                if (!"CHUNK".equals(chunkMap.get("status"))) {
                    System.err.println("[ERROR] chunk inesperado: " + chunkLine);
                    break;
                }
                byte[] data = Base64.getDecoder().decode((String) chunkMap.get("datos"));
                output.write(data);
            }
            tcp.readLine();

            return persistDownload(output.toByteArray(), saveDirectory, visibleName, hash, encrypted, onDone, "[TCP]");
        }
    }

    private static ServerClient.ServerResponse downloadUDP(
            Map<String, Object> request,
            String saveDirectory,
            boolean encrypted,
            ServerClient.DownloadCallback onDone
    ) throws Exception {
        UDPCommunications udp = ServerTransport.udp();
        if (udp == null) {
            return ServerClient.ServerResponse.error("No hay conexion UDP");
        }

        final int maxAttempts = 3;
        String lastIssue = "respuesta vacia";
        udp.setTimeout(30_000);

        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                List<String> lines;
                try {
                    lines = udp.sendJsonMultiLine(request);
                } catch (IOException ex) {
                    lastIssue = "timeout/IO en intento " + attempt + ": " + ex.getMessage();
                    continue;
                }

                if (lines.isEmpty()) {
                    lastIssue = "respuesta vacia";
                    continue;
                }

                Map<String, Object> header = ServerJson.readMap(lines.get(0));
                if (!"OK".equals(header.get("status"))) {
                    return ServerClient.ServerResponse.error("" + header.get("message"));
                }

                int totalChunks = ((Number) header.get("totalChunks")).intValue();
                String visibleName = (String) header.getOrDefault("nombreVisible", "documento");
                String hash = (String) header.getOrDefault("hash", "");
                Map<Integer, byte[]> chunks = decodeUdpChunks(lines);

                if (chunks.size() < totalChunks) {
                    lastIssue = "faltan chunks (recibidos=" + chunks.size() + "/" + totalChunks + ")";
                    continue;
                }

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                boolean missing = false;
                for (int index = 0; index < totalChunks; index++) {
                    byte[] chunk = chunks.get(index);
                    if (chunk == null) {
                        missing = true;
                        lastIssue = "falta chunk #" + index + " (total=" + totalChunks + ")";
                        break;
                    }
                    output.write(chunk);
                }

                if (!missing) {
                    return persistDownload(output.toByteArray(), saveDirectory, visibleName, hash,
                            encrypted, onDone, "[UDP]");
                }
            }
        } finally {
            udp.setTimeout(8_000);
        }

        return ServerClient.ServerResponse.error("Descarga UDP incompleta tras reintentos: " + lastIssue);
    }

    private static Map<Integer, byte[]> decodeUdpChunks(List<String> lines) throws IOException {
        Map<Integer, byte[]> chunksByIndex = new HashMap<>();
        int fallbackIndex = 0;

        for (int i = 1; i < lines.size(); i++) {
            Map<String, Object> chunkMap = ServerJson.readMap(lines.get(i));
            if (!"CHUNK".equals(chunkMap.get("status"))) {
                continue;
            }

            int chunkNumber = chunkMap.get("numero") instanceof Number n ? n.intValue() : fallbackIndex;
            String data = (String) chunkMap.get("datos");
            if (data != null) {
                chunksByIndex.put(chunkNumber, Base64.getDecoder().decode(data));
            }
            fallbackIndex++;
        }

        return chunksByIndex;
    }

    private static ServerClient.ServerResponse persistDownload(
            byte[] content,
            String saveDirectory,
            String visibleName,
            String hash,
            boolean encrypted,
            ServerClient.DownloadCallback onDone,
            String protocolLabel
    ) throws IOException {
        String finalName = encrypted ? visibleName + ".enc" : visibleName;
        String destinationPath = resolveSaveDir(saveDirectory, finalName);
        Files.write(Paths.get(destinationPath), content);
        System.out.println(protocolLabel + " Guardado en: " + destinationPath);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("archivo", destinationPath);
        result.put("nombreVisible", visibleName);
        result.put("hash", hash);
        result.put("tamanio", content.length);
        if (onDone != null) {
            onDone.onDone(destinationPath, visibleName, hash);
        }
        return ServerClient.ServerResponse.ok(result);
    }

    private static Map<String, Object> fileGetRequest(long id, String action) {
        return ServerJson.request("ARCHIVO", "GET", Map.of("id", id, "accion", action));
    }

    private static String resolveSaveDir(String saveDirectory, String fileName) throws IOException {
        String target = saveDirectory;
        if (target == null || target.isBlank()) {
            target = ServerClientConfig.DOWNLOAD_DIR;
        }

        Path path = Paths.get(target);
        if (Files.isDirectory(path)) {
            return path.resolve(fileName).toAbsolutePath().toString();
        }

        Files.createDirectories(path.getParent() != null ? path.getParent() : Paths.get("."));
        return path.toAbsolutePath().toString();
    }
}

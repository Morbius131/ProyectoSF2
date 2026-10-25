package ser.cliente.infrastructure;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import ser.cliente.communication.UDPCommunications;
import ser.cliente.model.Protocol;

final class ServerFileUploadClient {

    private ServerFileUploadClient() {
    }

    static ServerClient.ServerResponse sendFile(
            File file,
            String owner,
            String destinatario,
            ServerClient.ProgressCallback progress
    ) {
        try {
            Files.createDirectories(Paths.get(ServerClientConfig.DOWNLOAD_DIR));
            if (!file.exists()) {
                return ServerClient.ServerResponse.error("Archivo no encontrado: " + file.getPath());
            }

            String userName = owner != null ? owner : ConnectionManager.getUserName();
            if (ConnectionManager.getProtocol() == Protocol.UDP) {
                return sendFileUDP(file, userName, destinatario, progress);
            }
            return sendFileTCP(file, userName, destinatario, progress);
        } catch (Exception e) {
            System.err.println("[ERROR sendFile] " + e.getMessage());
            e.printStackTrace();
            return ServerClient.ServerResponse.error("Error enviando archivo: " + e.getMessage());
        }
    }

    private static ServerClient.ServerResponse sendFileTCP(
            File file,
            String owner,
            String destinatario,
            ServerClient.ProgressCallback progress
    ) throws Exception {
        FileParts parts = FileParts.from(file);
        if (file.length() > ServerClientConfig.TCP_DIRECT_UPLOAD_LIMIT) {
            return sendFileChunkedTCP(file, parts, owner, destinatario, progress);
        }
        return sendFileDirectTCP(file, parts, owner, destinatario);
    }

    private static ServerClient.ServerResponse sendFileDirectTCP(
            File file,
            FileParts parts,
            String owner,
            String destinatario
    ) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        Map<String, Object> data = baseFileData(parts, owner, destinatario);
        data.put("tamanio", bytes.length);
        data.put("contenido", Base64.getEncoder().encodeToString(bytes));

        Map<String, Object> request = ServerJson.request("ARCHIVO", "POST", data);
        ServerClient.ServerResponse result = ServerJson.parseResponse(ServerTransport.sendSingle(request));
        if (ServerResponses.isLockWaitTimeout(result)) {
            ServerResponses.sleepQuietly(800);
            result = ServerJson.parseResponse(ServerTransport.sendSingle(request));
        }
        return result;
    }

    private static ServerClient.ServerResponse sendFileChunkedTCP(
            File file,
            FileParts parts,
            String owner,
            String destinatario,
            ServerClient.ProgressCallback callback
    ) throws Exception {
        long tamanio = file.length();
        int totalChunks = (int) Math.ceil((double) tamanio / ServerClientConfig.TCP_CHUNK_SIZE);
        System.out.println("Enviando archivo en " + totalChunks + " chunks");

        Map<String, Object> initData = initData(parts, owner, destinatario, tamanio, totalChunks,
                ServerClientConfig.TCP_CHUNK_SIZE);
        Map<String, Object> initRequest = ServerJson.request("ARCHIVO", "POST_CHUNK_INIT", initData);

        Map<String, Object> initMap = ServerJson.readMap(ServerTransport.sendSingle(initRequest));
        if (!"OK".equals(initMap.get("status"))) {
            return ServerClient.ServerResponse.error("Error init chunk: " + initMap.get("message"));
        }

        String uploadId = extractUploadId(initMap);
        System.out.println("Upload ID: " + uploadId);

        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[ServerClientConfig.TCP_CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                Map<String, Object> chunkRequest = chunkRequest(uploadId, chunkIndex, totalChunks,
                        Arrays.copyOf(buffer, bytesRead));
                Map<String, Object> chunkMap = ServerJson.readMap(ServerTransport.sendSingle(chunkRequest));
                if (!"OK".equals(chunkMap.get("status"))) {
                    return ServerClient.ServerResponse.error("Error en chunk " + chunkIndex);
                }
                chunkIndex++;
                notifyProgress(callback, chunkIndex, totalChunks);
            }
        }

        Map<String, Object> endRequest = ServerJson.request("ARCHIVO", "POST_CHUNK_END",
                Map.of("uploadId", uploadId));
        return ServerJson.parseResponse(ServerTransport.sendSingle(endRequest));
    }

    private static ServerClient.ServerResponse sendFileUDP(
            File file,
            String owner,
            String destinatario,
            ServerClient.ProgressCallback callback
    ) throws Exception {
        UDPCommunications udp = ServerTransport.udp();
        if (udp == null) {
            return ServerClient.ServerResponse.error("No hay conexion UDP");
        }

        FileParts parts = FileParts.from(file);
        long tamanio = file.length();
        int totalChunks = (int) ((tamanio + ServerClientConfig.UDP_CHUNK_SIZE - 1)
                / ServerClientConfig.UDP_CHUNK_SIZE);

        Map<String, Object> initData = initData(parts, owner, destinatario, tamanio, totalChunks,
                ServerClientConfig.UDP_CHUNK_SIZE);
        Optional<String> initResponse = udp.sendJson(ServerJson.request("ARCHIVO", "POST_CHUNK_INIT", initData));
        if (initResponse.isEmpty()) {
            return ServerClient.ServerResponse.error("Sin respuesta del servidor (UDP init)");
        }

        Map<String, Object> initMap = ServerJson.readMap(initResponse.get());
        if (!"OK".equals(initMap.get("status"))) {
            return ServerClient.ServerResponse.error("Error init UDP: " + initMap.get("message"));
        }

        String uploadId = extractUploadId(initMap);
        System.out.println("[UDP] Upload ID: " + uploadId);

        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file), 65_536)) {
            byte[] buffer = new byte[ServerClientConfig.UDP_CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesRead;
            while ((bytesRead = readFully(input, buffer)) > 0) {
                byte[] data = bytesRead == ServerClientConfig.UDP_CHUNK_SIZE
                        ? buffer.clone()
                        : Arrays.copyOf(buffer, bytesRead);
                udp.sendJsonNoReply(chunkRequest(uploadId, chunkIndex, totalChunks, data));
                chunkIndex++;
                if (chunkIndex % ServerClientConfig.UDP_BATCH_SIZE == 0) {
                    Thread.sleep(ServerClientConfig.UDP_BATCH_PAUSE_MS);
                }
                notifyProgress(callback, chunkIndex, totalChunks);
            }
        }

        udp.setTimeout(ServerClientConfig.UDP_END_TIMEOUT_MS);
        try {
            Optional<String> endResponse = udp.sendJson(ServerJson.request("ARCHIVO", "POST_CHUNK_END",
                    Map.of("uploadId", uploadId)));
            if (endResponse.isEmpty()) {
                return ServerClient.ServerResponse.error("Sin respuesta en END UDP");
            }
            return ServerJson.parseResponse(endResponse.get());
        } finally {
            udp.setTimeout(8_000);
        }
    }

    private static Map<String, Object> baseFileData(FileParts parts, String owner, String destinatario) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tipo", "ARCHIVO");
        data.put("nombre", parts.name());
        data.put("extension", parts.extension());
        data.put("tipoPropietario", "LOCAL");
        data.put("usuarioNombre", owner);
        data.put("usuarioId", 0);
        ServerApiClient.addDestinatario(data, destinatario);
        return data;
    }

    private static Map<String, Object> initData(
            FileParts parts,
            String owner,
            String destinatario,
            long tamanio,
            int totalChunks,
            int chunkSize
    ) {
        Map<String, Object> data = baseFileData(parts, owner, destinatario);
        data.remove("tipo");
        data.put("tamanio", tamanio);
        data.put("totalChunks", totalChunks);
        data.put("chunkSize", chunkSize);
        return data;
    }

    private static Map<String, Object> chunkRequest(
            String uploadId,
            int chunkIndex,
            int totalChunks,
            byte[] chunk
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uploadId", uploadId);
        data.put("chunkIndex", chunkIndex);
        data.put("totalChunks", totalChunks);
        data.put("contenido", Base64.getEncoder().encodeToString(chunk));
        return ServerJson.request("ARCHIVO", "POST_CHUNK", data);
    }

    @SuppressWarnings("unchecked")
    private static String extractUploadId(Map<String, Object> response) {
        return (String) ((Map<String, Object>) response.get("data")).get("uploadId");
    }

    private static void notifyProgress(ServerClient.ProgressCallback callback, int done, int total) {
        if (callback != null) {
            callback.onProgress(done, total);
        }
    }

    private static int readFully(InputStream input, byte[] buffer) throws java.io.IOException {
        int total = 0;
        while (total < buffer.length) {
            int count = input.read(buffer, total, buffer.length - total);
            if (count == -1) {
                break;
            }
            total += count;
        }
        return total;
    }

    private record FileParts(String name, String extension) {
        static FileParts from(File file) {
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String name = dot >= 0 ? fileName.substring(0, dot) : fileName;
            String extension = dot >= 0 ? fileName.substring(dot + 1) : "bin";
            return new FileParts(name, extension);
        }
    }
}

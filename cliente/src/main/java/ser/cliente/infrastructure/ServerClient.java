package ser.cliente.infrastructure;

import java.io.File;

/**
 * Public facade for server operations used by controllers.
 */
public final class ServerClient {

    private ServerClient() {
    }

    public static ServerResponse registerUser(String nombre) {
        return ServerApiClient.registerUser(nombre);
    }

    public static ServerResponse listConnectedClients() {
        return ServerApiClient.listConnectedClients();
    }

    public static ServerResponse listDocuments() {
        return ServerApiClient.listDocuments();
    }

    public static ServerResponse listMessages() {
        return ServerApiClient.listMessages();
    }

    public static ServerResponse sendMessage(String asunto, String contenido, String owner, String destinatario) {
        return ServerApiClient.sendMessage(asunto, contenido, owner, destinatario);
    }

    public static ServerResponse sendMessage(String asunto, String contenido, String owner) {
        return sendMessage(asunto, contenido, owner, null);
    }

    public static ServerResponse sendFile(File file, String owner, String destinatario, ProgressCallback progress) {
        return ServerFileUploadClient.sendFile(file, owner, destinatario, progress);
    }

    public static ServerResponse sendFile(File file, String owner, ProgressCallback progress) {
        return sendFile(file, owner, null, progress);
    }

    public static ServerResponse downloadDocument(long id, String saveDirectory, DownloadCallback onDone) {
        return ServerFileDownloadClient.downloadDocument(id, saveDirectory, onDone);
    }

    public static ServerResponse downloadDocumentHash(long id) {
        return ServerFileDownloadClient.downloadDocumentHash(id);
    }

    public static ServerResponse downloadDocumentEncrypted(long id, String saveDirectory, DownloadCallback onDone) {
        return ServerFileDownloadClient.downloadDocumentEncrypted(id, saveDirectory, onDone);
    }

    public static ServerResponse getLogs() {
        return getLogs(null);
    }

    public static ServerResponse getLogs(String serverId) {
        return ServerLogClient.getLogs(serverId);
    }

    public static ServerResponse listServers() {
        return ServerApiClient.listServers();
    }

    public static ServerResponse identifyEmotion(byte[] imageBytes, String imageName, String userName) {
        return ServerApiClient.identifyEmotion(imageBytes, imageName, userName);
    }

    public static ServerResponse identifyEmotionById(long id) {
        return ServerApiClient.identifyEmotionById(id);
    }

    public static ServerResponse listFaceImages() {
        return ServerApiClient.listFaceImages();
    }

    public static ServerResponse getServerInfo(String serverId) {
        return ServerApiClient.getServerInfo(serverId);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int done, int total);
    }

    @FunctionalInterface
    public interface DownloadCallback {
        void onDone(String filePath, String nombre, String hash);
    }

    public record ServerResponse(boolean success, String message, Object data) {
        public static ServerResponse ok(Object data) {
            return new ServerResponse(true, "OK", data);
        }

        public static ServerResponse error(String msg) {
            return new ServerResponse(false, msg, null);
        }
    }
}

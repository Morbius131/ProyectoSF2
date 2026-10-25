package ser.cliente.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import ser.cliente.dao.DAO;
import ser.cliente.infrastructure.ConnectionManager;
import ser.cliente.infrastructure.ServerClient;
import ser.cliente.model.*;

public final class FileController extends AbstractController {
    public FileController() {
        super(MessageType.SEND_FILE, MessageType.RECEIVE_FILE);
    }

    public void sendFiles(List<File> files, String owner, String destinatario) {
        if (files == null || files.isEmpty()) {
            publish(Response.failure(MessageType.SEND_FILE, "Debe seleccionar al menos un archivo"));
            return;
        }
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.SEND_FILE, "No hay conexion activa con el servidor"));
            return;
        }
        String userName = owner != null && !owner.isBlank() ? owner : ConnectionManager.getUserName();
        List<FileTransferResult> results = new ArrayList<>();

        for (File file : files) {
            publish(Response.success(MessageType.SEND_FILE, "Enviando: " + file.getName() + "...", null));
            ServerClient.ServerResponse resp = ServerClient.sendFile(file, userName, destinatario, (done, total) -> {
                if (done % 10 == 0 || done == total) {
                    int pct = done * 100 / total;
                    System.out.printf("Progreso: %d%% (%d/%d chunks)%n", pct, done, total);
                }
            });
            if (resp.success()) {
                DAO.saveSentFile(file, userName);
                results.add(new FileTransferResult(file.getName(), file.length(), true, "OK"));
                publish(Response.success(MessageType.SEND_FILE, "Archivo enviado: " + file.getName(), resp.data()));
            } else {
                results.add(new FileTransferResult(file.getName(), file.length(), false, resp.message()));
                publish(Response.failure(MessageType.SEND_FILE, "Error enviando " + file.getName() + ": " + resp.message()));
                System.err.println("[ERROR sendFile] " + resp.message());
            }
        }
        publish(Response.success(MessageType.SEND_FILE, "Transferencias finalizadas", results));
    }

    public void sendFiles(List<File> files, String owner) {
        sendFiles(files, owner, null);
    }

    public void receiveFile(int fileId, String saveDirectory, boolean hashEnabled, boolean encrypted) {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.RECEIVE_FILE, "No hay conexion activa con el servidor"));
            return;
        }
        publish(Response.success(MessageType.RECEIVE_FILE, "Descargando archivo ID=" + fileId + "...", null));

        ServerClient.ServerResponse resp;
        if (encrypted) {
            resp = ServerClient.downloadDocumentEncrypted(fileId, saveDirectory, (path, nombre, hash) -> {
                System.out.println("[DOWNLOAD] Guardado en: " + path + " hash=" + hash);
            });
        } else if (hashEnabled) {
            resp = ServerClient.downloadDocumentHash(fileId);
        } else {
            resp = ServerClient.downloadDocument(fileId, saveDirectory, (path, nombre, hash) -> {
                System.out.println("[DOWNLOAD] Guardado en: " + path);
            });
        }

        if (!resp.success()) {
            System.err.println("[ERROR receiveFile] " + resp.message());
            publish(Response.failure(MessageType.RECEIVE_FILE, resp.message()));
            return;
        }
        if (hashEnabled && !encrypted && resp.data() instanceof java.util.Map<?,?> m) {
            Object idValue = m.get("id");
            Object nombreValue = m.get("nombreVisible");
            Object hashValue = m.get("hash");
            String info = "ID: " + (idValue != null ? idValue : fileId) +
                "\nNombre: " + (nombreValue != null ? nombreValue : "?") +
                "\nHash: " + (hashValue != null ? hashValue : "(sin hash)");
            publish(Response.success(MessageType.RECEIVE_FILE, "Informacion del archivo:\n" + info, null));
        } else {
            String path = resp.data() instanceof java.util.Map<?,?> dm ?
                    (String) ((java.util.Map<?,?>)dm).get("archivo") : saveDirectory;
            publish(Response.success(MessageType.RECEIVE_FILE, "Archivo guardado correctamente", path));
        }
    }
}

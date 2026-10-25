package ser.cliente.gui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import ser.cliente.controller.ConnectionController;
import ser.cliente.controller.EmotionController;
import ser.cliente.controller.FileController;
import ser.cliente.controller.LogController;
import ser.cliente.controller.MessageController;
import ser.cliente.infrastructure.ConnectionManager;
import ser.cliente.infrastructure.LogUtils;
import ser.cliente.model.ClientAction;
import ser.cliente.model.DocumentCatalog;
import ser.cliente.model.EmotionResult;
import ser.cliente.model.FaceImage;
import ser.cliente.model.FileTransferResult;
import ser.cliente.model.MessageType;
import ser.cliente.model.Protocol;
import ser.cliente.model.Response;
import ser.cliente.model.ServerInfo;
import ser.cliente.view.ClientView;

public final class GUIController {
    private final ClientView view;
    private final ConnectionController connectionController;
    private final MessageController messageController;
    private final FileController fileController;
    private final LogController logController;
    private final EmotionController emotionController;

    public GUIController(ClientView view) {
        this.view = view;
        this.connectionController = new ConnectionController();
        this.messageController = new MessageController();
        this.fileController = new FileController();
        this.logController = new LogController();
        this.emotionController = new EmotionController();

        subscribeToControllers();
        bindViewEvents();
        view.appendConsole("Cliente listo. Ingrese IP, puerto y nombre de usuario, luego pulse Conectar.");
    }

    private void subscribeToControllers() {
        connectionController.addObserver(this::onControllerResponse);
        messageController.addObserver(this::onControllerResponse);
        fileController.addObserver(this::onControllerResponse);
        logController.addObserver(this::onControllerResponse);
        emotionController.addObserver(this::onControllerResponse);
    }

    private void bindViewEvents() {
        view.addConnectListener(event -> connect());
        view.addActionSelectionListener(event -> view.showActionCard(view.getSelectedAction()));
        view.addSendListener(event -> executeSelectedAction());
        view.addBrowseFilesListener(event -> chooseFiles());
        view.addBrowseReceiveFilePathListener(event -> chooseDirectoryForReceiveFile());
        view.addBrowseLogsPathListener(event -> chooseDirectoryForLogs());
        view.addBrowseEmotionImageListener(event -> view.chooseEmotionImage());
    }

    private void connect() {
        String ip = view.getIp();
        String portText = view.getPortText();
        String name = view.getClientName();
        Protocol protocol = view.isTcpSelected() ? Protocol.TCP : Protocol.UDP;

        if (ip.isBlank()) { view.showError("Ingrese la IP del servidor"); return; }
        if (name.isBlank()) { view.showError("Ingrese su nombre de usuario"); return; }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            view.showError("El puerto debe ser numerico");
            return;
        }

        final int finalPort = port;
        view.appendConsole("Conectando a " + ip + ":" + port + " via " + protocol + "...");
        runAsync(() -> connectionController.connect(ip, finalPort, protocol, name), "Conectando...");
    }

    private void executeSelectedAction() {
        ClientAction action = view.getSelectedAction();
        String owner = view.getClientName().isBlank() ?
                ConnectionManager.getUserName() : view.getClientName();

        switch (action) {
            case LIST_CLIENTS -> runAsync(() -> connectionController.listConnectedClients(), "Obteniendo clientes...");
            case LIST_SERVERS -> runAsync(() -> connectionController.listServers(), "Obteniendo servidores...");
            case LIST_DOCUMENTS -> runAsync(() -> messageController.listDocuments(), "Obteniendo documentos...");
            case SEND_MESSAGE -> {
                String msg = view.getSendMessageText();
                if (msg.isBlank()) { view.showError("Escriba un mensaje"); return; }
                String asunto = view.getAsuntoMsg();
                String destinatario = view.getDestinatarioMsg();
                runAsync(() -> messageController.sendMessage(msg, owner, asunto, destinatario), "Enviando mensaje...");
            }
            case SEND_FILE -> {
                List<File> files = view.getSelectedFiles();
                if (files.isEmpty()) { view.showError("Seleccione archivos"); return; }
                String destinatario = view.getDestinatarioFile();
                long totalSize = files.stream().mapToLong(File::length).sum();
                String sizeStr = totalSize > 1024*1024 ? "%.1f MB".formatted(totalSize / (1024.0*1024.0)) : "%.1f KB".formatted(totalSize / 1024.0);
                runAsync(() -> fileController.sendFiles(files, owner, destinatario), "Subiendo archivo (" + sizeStr + ")...");
            }
            case RECEIVE_MESSAGE -> {
                try {
                    int id = view.getReceiveMessageId();
                    runAsync(() -> messageController.receiveMessage(id), "Recibiendo mensaje...");
                } catch (NumberFormatException e) {
                    view.showError("ID de mensaje invalido");
                }
            }
            case RECEIVE_FILE -> {
                try {
                    int id = view.getReceiveFileId();
                    String path = view.getReceiveFilePath();
                    boolean hash = view.isHashEnabled();
                    boolean enc = view.isEncryptedEnabled();
                    runAsync(() -> fileController.receiveFile(id, path, hash, enc), "Descargando archivo...");
                } catch (NumberFormatException e) {
                    view.showError("ID de archivo invalido");
                }
            }
            case RECEIVE_LOGS -> {
                String path = view.getLogsPath();
                String serverId = view.getServerIdLogs();
                runAsync(() -> logController.receiveLogs(path, serverId), "Obteniendo logs...");
            }
            case LIST_FACE_IMAGES -> runAsync(() -> emotionController.listFaceImages(), "Obteniendo imagenes...");
            case IDENTIFY_EMOTIONS -> runAsync(this::identifyEmotions, "Identificando emociones...");
        }
    }

    private void identifyEmotions() {
        String imageId = view.getEmotionImageId();
        if (!imageId.isBlank()) {
            try {
                long id = Long.parseLong(imageId);
                emotionController.identifyById(id);
            } catch (NumberFormatException ex) {
                SwingUtilities.invokeLater(() -> view.showError("ID de imagen invalido"));
            }
            return;
        } else if (view.getProcessedEmotionImage() == null) {
            SwingUtilities.invokeLater(view::processEmotionCrop);
            try {
                SwingUtilities.invokeAndWait(() -> {});
            } catch (Exception ignored) {
            }
        }
        String owner = view.getClientName().isBlank()
                ? ConnectionManager.getUserName()
                : view.getClientName();
        emotionController.identify(view.getProcessedEmotionImage(), view.getSelectedEmotionImageFile(), owner);
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        int selected = chooser.showOpenDialog(view);
        if (selected == JFileChooser.APPROVE_OPTION) {
            view.setSelectedFiles(List.of(chooser.getSelectedFiles()));
        }
    }

    private void chooseDirectoryForReceiveFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int selected = chooser.showOpenDialog(view);
        if (selected == JFileChooser.APPROVE_OPTION) {
            File directory = chooser.getSelectedFile();
            if (directory != null) {
                view.setReceiveFilePath(directory.getAbsolutePath());
                view.appendConsole("Destino de archivo seleccionado: " + directory.getAbsolutePath());
            }
        }
    }

    private void chooseDirectoryForLogs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int selected = chooser.showOpenDialog(view);
        if (selected == JFileChooser.APPROVE_OPTION) {
            File directory = chooser.getSelectedFile();
            if (directory != null) {
                view.setLogsPath(directory.getAbsolutePath());
                view.appendConsole("Destino de logs seleccionado: " + directory.getAbsolutePath());
            }
        }
    }

    private void runAsync(Runnable action) {
        runAsync(action, "Procesando...");
    }

    private void runAsync(Runnable action, String loadingMessage) {
        SwingUtilities.invokeLater(() -> view.showLoading(loadingMessage));
        CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (Exception ex) {
                LogUtils.error("Error en ejecucion de accion", ex);
                System.err.println("[ERROR] " + ex.getMessage());
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    view.hideLoading();
                    view.showError(ex.getMessage() != null ?
                        ex.getMessage() : ex.getClass().getSimpleName());
                });
            }
        });
    }

    private void onControllerResponse(Response response) {
        SwingUtilities.invokeLater(() -> {
            view.hideLoading();
            applyResponse(response);
        });
    }

    private void applyResponse(Response response) {
        if (response.message() != null && !response.message().isBlank()) {
            // Only log shorter messages to console to avoid flooding with file content
            String msg = response.message();
            if (msg.length() > 500) msg = msg.substring(0, 497) + "...";
            view.appendConsole(msg);
        }

        if (!response.success()) {
            if (response.message() != null && !response.message().isBlank()) {
                view.showError(response.message());
            }
            return;
        }

        if (response.type() == MessageType.LIST_CLIENTS) {
            view.setClients(ConnectionController.extractClients(response));
            return;
        }

        if (response.type() == MessageType.LIST_SERVERS) {
            view.setServers(ConnectionController.extractServers(response));
            return;
        }

        if (response.type() == MessageType.LIST_DOCUMENTS && response.payload() instanceof DocumentCatalog catalog) {
            view.setDocuments(catalog);
            return;
        }

        if (response.type() == MessageType.RECEIVE_MESSAGE && response.payload() instanceof String message) {
            view.setReceivedMessage(message);
            return;
        }

        if (response.type() == MessageType.SEND_FILE && response.payload() instanceof List<?> payload) {
            view.appendConsole(buildFileTransferSummary(payload));
            return;
        }

        if ((response.type() == MessageType.RECEIVE_FILE || response.type() == MessageType.RECEIVE_LOGS)
                && response.payload() instanceof String path) {
            if (isExistingPath(path)) {
                view.appendConsole("Archivo generado en: " + path);
            }
        }

        if (response.type() == MessageType.IDENTIFY_EMOTIONS && response.payload() instanceof EmotionResult result) {
            view.setEmotionResult(result);
            return;
        }

        if (response.type() == MessageType.LIST_FACE_IMAGES && response.payload() instanceof List<?> payload) {
            List<FaceImage> images = payload.stream()
                    .filter(FaceImage.class::isInstance)
                    .map(FaceImage.class::cast)
                    .toList();
            view.setFaceImages(images);
        }
    }

    private boolean isExistingPath(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            return Files.exists(Path.of(value));
        } catch (Exception ex) {
            return false;
        }
    }

    private String buildFileTransferSummary(List<?> payload) {
        int success = 0, failed = 0;
        for (Object item : payload) {
            if (item instanceof FileTransferResult result) {
                if (result.success()) success++; else failed++;
            }
        }
        return "Transferencias completadas: %d, fallidas: %d".formatted(success, failed);
    }
}

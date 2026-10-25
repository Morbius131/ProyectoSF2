package ser.cliente.controller;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import ser.cliente.infrastructure.ConnectionManager;
import ser.cliente.infrastructure.ServerClient;
import ser.cliente.model.EmotionResult;
import ser.cliente.model.FaceImage;
import ser.cliente.model.MessageType;
import ser.cliente.model.Response;

public final class EmotionController extends AbstractController {
    private static final List<String> EMOTIONS = List.of(
            "Angry", "Disgust", "Fear", "Happy", "Sad", "Surprise", "Neutral"
    );

    public EmotionController() {
        super(MessageType.IDENTIFY_EMOTIONS, MessageType.LIST_FACE_IMAGES);
    }

    public void listFaceImages() {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.LIST_FACE_IMAGES, "No hay conexion activa con el servidor"));
            return;
        }

        try {
            ServerClient.ServerResponse resp = ServerClient.listFaceImages();
            if (!resp.success()) {
                publish(Response.failure(MessageType.LIST_FACE_IMAGES, resp.message()));
                return;
            }

            List<FaceImage> images = parseFaceImages(resp.data());
            publish(Response.success(
                    MessageType.LIST_FACE_IMAGES,
                    "Se recibieron " + images.size() + " imagenes de rostro",
                    images
            ));
        } catch (Exception ex) {
            publish(Response.failure(MessageType.LIST_FACE_IMAGES,
                    "Error listando imagenes de rostro: " + ex.getMessage()));
        }
    }

    public void identify(BufferedImage image48Gray, File sourceFile, String userName) {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.IDENTIFY_EMOTIONS, "No hay conexion activa con el servidor"));
            return;
        }
        if (image48Gray == null) {
            publish(Response.failure(MessageType.IDENTIFY_EMOTIONS, "Primero agregue una imagen y procese el recorte"));
            return;
        }

        try {
            byte[] imageBytes = toPngBytes(image48Gray);
            String imageName = sourceFile != null ? sourceFile.getName() : "rostro_48x48.png";
            ServerClient.ServerResponse resp = ServerClient.identifyEmotion(imageBytes, imageName, userName);
            if (!resp.success()) {
                publish(Response.failure(MessageType.IDENTIFY_EMOTIONS, resp.message()));
                return;
            }

            EmotionResult result = parseResult(resp.data());
            publish(Response.success(
                    MessageType.IDENTIFY_EMOTIONS,
                    buildSuccessMessage(result, resp.data()),
                    result
            ));
        } catch (Exception ex) {
            publish(Response.failure(MessageType.IDENTIFY_EMOTIONS,
                    "Error identificando emociones: " + ex.getMessage()));
        }
    }

    public void identifyById(long imageId) {
        if (!ConnectionManager.isConnected()) {
            publish(Response.failure(MessageType.IDENTIFY_EMOTIONS, "No hay conexion activa con el servidor"));
            return;
        }
        if (imageId <= 0) {
            publish(Response.failure(MessageType.IDENTIFY_EMOTIONS, "ID de imagen invalido"));
            return;
        }

        try {
            ServerClient.ServerResponse resp = ServerClient.identifyEmotionById(imageId);
            if (!resp.success()) {
                publish(Response.failure(MessageType.IDENTIFY_EMOTIONS, resp.message()));
                return;
            }

            EmotionResult result = parseResult(resp.data());
            publish(Response.success(
                    MessageType.IDENTIFY_EMOTIONS,
                    buildSuccessMessage(result, resp.data()),
                    result
            ));
        } catch (Exception ex) {
            publish(Response.failure(MessageType.IDENTIFY_EMOTIONS,
                    "Error identificando emocion por ID: " + ex.getMessage()));
        }
    }

    private byte[] toPngBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IllegalStateException("No se pudo convertir la imagen a PNG");
        }
        return output.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private EmotionResult parseResult(Object data) {
        if (!(data instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("Respuesta de emocion sin datos");
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        Object probabilitiesObj = map.get("probabilidades");
        if (!(probabilitiesObj instanceof Map<?, ?> probabilitiesMap)) {
            throw new IllegalStateException("Respuesta sin probabilidades");
        }

        List<String> labels = new ArrayList<>();
        double[] probabilities = new double[EMOTIONS.size()];
        for (int i = 0; i < EMOTIONS.size(); i++) {
            String display = EMOTIONS.get(i);
            labels.add(display);
            probabilities[i] = probabilityOf(probabilitiesMap, display);
        }

        int winner = winnerIndex(probabilities);
        String winnerDisplay = text(map.get("emocionGanadoraDisplay"));
        if (!winnerDisplay.isBlank()) {
            int index = indexOfIgnoreCase(labels, winnerDisplay);
            if (index >= 0) {
                winner = index;
            }
        } else {
            String winnerCode = text(map.get("emocionGanadora"));
            int index = indexOfIgnoreCase(labels, winnerCode);
            if (index >= 0) {
                winner = index;
            }
        }

        long elapsedMs = map.get("tiempoInferenciaMs") instanceof Number n ? n.longValue() : 0;
        return new EmotionResult(labels, probabilities, winner, elapsedMs);
    }

    private List<FaceImage> parseFaceImages(Object data) {
        if (!(data instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Respuesta de listado sin datos");
        }
        Object imagesObj = map.get("imagenes");
        if (!(imagesObj instanceof List<?> imagesRaw)) {
            return List.of();
        }

        List<FaceImage> images = new ArrayList<>();
        for (Object item : imagesRaw) {
            if (!(item instanceof Map<?, ?> imageMap)) {
                continue;
            }
            long sizeBytes = longValue(imageMap.get("tamanio"));
            images.add(new FaceImage(
                    (int) longValue(imageMap.get("id")),
                    text(imageMap.get("nombre")),
                    text(imageMap.get("extension")),
                    sizeBytes / (1024.0 * 1024.0),
                    longValue(imageMap.get("documentoId")),
                    text(imageMap.get("fechaHora")),
                    decodeImage(text(imageMap.get("datosBase64")))
            ));
        }
        return images;
    }

    private BufferedImage decodeImage(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            int comma = base64.indexOf(',');
            String clean = comma >= 0 ? base64.substring(comma + 1) : base64;
            byte[] bytes = Base64.getDecoder().decode(clean);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception ignored) {
            return null;
        }
    }

    private double probabilityOf(Map<?, ?> probabilities, String display) {
        Object value = probabilities.get(display.toUpperCase());
        if (value == null) {
            value = probabilities.get(display);
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private int winnerIndex(double[] probabilities) {
        int winner = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[winner]) {
                winner = i;
            }
        }
        return winner;
    }

    private int indexOfIgnoreCase(List<String> labels, String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).equalsIgnoreCase(value)) {
                return i;
            }
        }
        return -1;
    }

    private long longValue(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private String buildSuccessMessage(EmotionResult result, Object data) {
        String suffix = result.elapsedMillis() > 0 ? " en " + result.elapsedMillis() + " ms" : "";
        String ids = "";
        if (data instanceof Map<?, ?> map) {
            Object imageId = map.get("imagenId");
            Object documentId = map.get("documentoId");
            List<String> parts = new ArrayList<>();
            if (imageId != null) {
                parts.add("imagenId=" + imageId);
            }
            if (documentId != null) {
                parts.add("documentoId=" + documentId);
            }
            if (!parts.isEmpty()) {
                ids = " (" + String.join(", ", parts) + ")";
            }
        }
        return "Emocion detectada: %s (%.2f%%)%s%s".formatted(
                result.winner(),
                result.winnerProbability() * 100,
                suffix,
                ids
        );
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}

package ser.cliente.model;

import java.awt.image.BufferedImage;

public record FaceImage(
        int id,
        String nombre,
        String extension,
        double tamanoMb,
        long documentoId,
        String fechaHora,
        BufferedImage image
) {
}

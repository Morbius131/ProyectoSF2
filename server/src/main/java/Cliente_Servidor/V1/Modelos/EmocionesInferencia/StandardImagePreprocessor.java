package Cliente_Servidor.V1.Modelos.EmocionesInferencia;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

@Component
public class StandardImagePreprocessor implements ImagePreprocessor {

    private static final int TARGET_SIZE = 48;
    private static final int CHANNELS = 3;

    @Override
    public float[] preprocess(byte[] imageBytes) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                throw new IllegalArgumentException("No se pudo decodificar la imagen");
            }

            BufferedImage rgb = toRgb(original);
            BufferedImage resized = resize(rgb, TARGET_SIZE, TARGET_SIZE);

            int w = resized.getWidth();
            int h = resized.getHeight();

            float[] tensor = new float[CHANNELS * h * w];
            int pixelCount = h * w;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgbVal = resized.getRGB(x, y);
                    int r = (rgbVal >> 16) & 0xFF;
                    int g = (rgbVal >> 8) & 0xFF;
                    int b = rgbVal & 0xFF;

                    tensor[0 * pixelCount + y * w + x] = r / 255.0f;
                    tensor[1 * pixelCount + y * w + x] = g / 255.0f;
                    tensor[2 * pixelCount + y * w + x] = b / 255.0f;
                }
            }

            return tensor;

        } catch (Exception e) {
            throw new RuntimeException("Error preprocesando imagen: " + e.getMessage(), e);
        }
    }

    private BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(src, 0, 0, null);
        return rgb;
    }

    private BufferedImage resize(BufferedImage src, int targetW, int targetH) {
        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(
                src.getScaledInstance(targetW, targetH, java.awt.Image.SCALE_SMOOTH),
                0, 0, null);
        return resized;
    }
}

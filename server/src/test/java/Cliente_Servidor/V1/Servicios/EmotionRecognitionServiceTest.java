package Cliente_Servidor.V1.Servicios;

import Cliente_Servidor.V1.Modelos.EmotionResult;
import Cliente_Servidor.V1.Modelos.EmocionesInferencia.ImagePreprocessor;
import Cliente_Servidor.V1.Modelos.EmocionesInferencia.InferenceEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmotionRecognitionServiceTest {

    @Test
    void predecirConvertsLogitsToProbabilities() {
        InferenceEngine engine = new InferenceEngine() {
            @Override
            public float[] infer(float[] tensorInput) {
                return new float[] {-2.0f, 0.5f, 3.0f, -1.0f, 1.2f, 0.0f, 4.5f};
            }

            @Override
            public void close() {
            }
        };
        ImagePreprocessor preprocessor = imageBytes -> new float[1 * 3 * 48 * 48];
        EmotionRecognitionService service = new EmotionRecognitionService(engine, preprocessor);

        EmotionResult result = service.predecir(new byte[] {1, 2, 3});

        double sum = result.getProbabilidades().values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        assertEquals(1.0, sum, 1e-6);
        assertTrue(result.getProbabilidades().values().stream()
                .allMatch(p -> p >= 0.0 && p <= 1.0));
    }
}

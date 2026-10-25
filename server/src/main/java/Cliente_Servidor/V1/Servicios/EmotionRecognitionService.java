package Cliente_Servidor.V1.Servicios;

import Cliente_Servidor.V1.Modelos.EmotionResult;
import Cliente_Servidor.V1.Modelos.EmocionesInferencia.ImagePreprocessor;
import Cliente_Servidor.V1.Modelos.EmocionesInferencia.InferenceEngine;
import Cliente_Servidor.V1.Modelos.EmocionesInferencia.Softmax;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import org.springframework.stereotype.Service;

@Service
public class EmotionRecognitionService {

    private final InferenceEngine engine;
    private final ImagePreprocessor preprocessor;

    public EmotionRecognitionService(InferenceEngine engine,
                                     ImagePreprocessor preprocessor) {
        this.engine = engine;
        this.preprocessor = preprocessor;
    }

    public EmotionResult predecir(byte[] imageBytes) {
        float[] tensor = preprocessor.preprocess(imageBytes);
        long t0 = System.currentTimeMillis();
        float[] logits = engine.infer(tensor);
        long elapsed = System.currentTimeMillis() - t0;
        float[] probabilidades = Softmax.apply(logits);
        EmotionResult result = EmotionResult.fromRaw(probabilidades, elapsed);
        ConsolaUtil.info("Predicción ONNX: " + result.getEmocionGanadora()
                + " (" + String.format("%.1f%%",
                        result.getProbabilidades().get(result.getEmocionGanadora()) * 100)
                + ") en " + elapsed + "ms");
        return result;
    }

    public boolean isModelLoaded() {
        try {
            engine.infer(new float[1 * 3 * 48 * 48]);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

package Cliente_Servidor.V1.Modelos.EmocionesInferencia;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Component
public class OnnxEmotionEngine implements InferenceEngine {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    public OnnxEmotionEngine(
            @Value("${emotion.model.path:server/src/main/java/Cliente_Servidor/V1/Modelos/EmocionesInferencia/modelo_emociones_final.onnx}") String modelPath) {
        try {
            this.env = OrtEnvironment.getEnvironment();

            Path path = Paths.get(modelPath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                ConsolaUtil.warn("Modelo ONNX no encontrado en: " + path
                        + ". El servicio de emociones no estará disponible.");
                this.session = null;
                this.inputName = null;
                return;
            }

            ConsolaUtil.info("Cargando modelo ONNX desde: " + path);

            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            this.session = env.createSession(path.toString(), options);
            this.inputName = session.getInputNames().iterator().next();
            ConsolaUtil.ok("Modelo ONNX cargado: " + path.getFileName()
                    + " (input: " + inputName + ")");

        } catch (OrtException e) {
            throw new RuntimeException("Error cargando modelo ONNX: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] infer(float[] tensorInput) {
        if (session == null) {
            throw new IllegalStateException("Modelo ONNX no cargado");
        }
        try {
            long[] shape = {1, 3, 48, 48};
            FloatBuffer buffer = FloatBuffer.wrap(tensorInput);
            OnnxTensor tensor = OnnxTensor.createTensor(env, buffer, shape);
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, tensor);

            var results = session.run(inputs);
            float[][] output = (float[][]) results.get(0).getValue();
            return output[0];

        } catch (OrtException e) {
            throw new RuntimeException("Error en inferencia ONNX: " + e.getMessage(), e);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (OrtException e) {
            ConsolaUtil.warn("Error cerrando ONNX: " + e.getMessage());
        }
    }
}

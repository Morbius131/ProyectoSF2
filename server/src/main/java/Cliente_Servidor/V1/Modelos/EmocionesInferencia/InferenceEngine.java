package Cliente_Servidor.V1.Modelos.EmocionesInferencia;

public interface InferenceEngine {
    float[] infer(float[] tensorInput);
    void close();
}

package Cliente_Servidor.V1.Modelos;

import java.util.EnumMap;
import java.util.Map;

public class EmotionResult {

    private final Map<Emotion, Double> probabilidades;
    private final Emotion emocionGanadora;
    private final long tiempoInferenciaMs;

    public EmotionResult(Map<Emotion, Double> probabilidades,
                         Emotion emocionGanadora,
                         long tiempoInferenciaMs) {
        this.probabilidades = probabilidades;
        this.emocionGanadora = emocionGanadora;
        this.tiempoInferenciaMs = tiempoInferenciaMs;
    }

    public static EmotionResult fromRaw(float[] probs, long tiempoMs) {
        Map<Emotion, Double> map = new EnumMap<>(Emotion.class);
        int maxIdx = 0;
        double maxVal = 0;
        for (int i = 0; i < probs.length && i < Emotion.values().length; i++) {
            double p = probs[i];
            map.put(Emotion.fromIndex(i), p);
            if (p > maxVal) {
                maxVal = p;
                maxIdx = i;
            }
        }
        return new EmotionResult(map, Emotion.fromIndex(maxIdx), tiempoMs);
    }

    public Map<Emotion, Double> getProbabilidades() {
        return probabilidades;
    }

    public Emotion getEmocionGanadora() {
        return emocionGanadora;
    }

    public long getTiempoInferenciaMs() {
        return tiempoInferenciaMs;
    }
}

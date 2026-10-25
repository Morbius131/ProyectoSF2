package Cliente_Servidor.V1.Modelos.EmocionesInferencia;

public final class Softmax {

    private Softmax() {}

    public static float[] apply(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > max) max = v;
        }
        float[] exps = new float[logits.length];
        double sum = 0;
        for (int i = 0; i < logits.length; i++) {
            exps[i] = (float) Math.exp(logits[i] - max);
            sum += exps[i];
        }
        for (int i = 0; i < exps.length; i++) {
            exps[i] = (float) (exps[i] / sum);
        }
        return exps;
    }
}

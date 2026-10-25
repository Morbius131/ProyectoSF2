package ser.cliente.model;

import java.util.List;

public record EmotionResult(List<String> emotions, double[] probabilities, int winnerIndex, long elapsedMillis) {
    public String winner() {
        return emotions.get(winnerIndex);
    }

    public double winnerProbability() {
        return probabilities[winnerIndex];
    }
}

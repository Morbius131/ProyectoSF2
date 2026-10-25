package Cliente_Servidor.V1.Modelos;

public enum Emotion {
    ANGRY(0),
    DISGUST(1),
    FEAR(2),
    HAPPY(3),
    SAD(4),
    SURPRISE(5),
    NEUTRAL(6);

    private final int index;

    Emotion(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public static Emotion fromIndex(int index) {
        for (Emotion e : values()) {
            if (e.index == index) return e;
        }
        throw new IllegalArgumentException("Índice de emoción inválido: " + index);
    }

    public String toDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}

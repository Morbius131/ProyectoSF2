package ser.cliente.model;

public enum Protocol {
    TCP,
    UDP;

    public static Protocol fromLabel(String label) {
        if (label == null) {
            return TCP;
        }
        return "UDP".equalsIgnoreCase(label.trim()) ? UDP : TCP;
    }
}
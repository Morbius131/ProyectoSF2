package ser.cliente.model;

public final class Response {
    private final MessageType type;
    private final boolean success;
    private final String message;
    private final Object payload;

    public Response(MessageType type, boolean success, String message, Object payload) {
        this.type = type;
        this.success = success;
        this.message = message;
        this.payload = payload;
    }

    public static Response success(MessageType type, String message, Object payload) {
        return new Response(type, true, message, payload);
    }

    public static Response failure(MessageType type, String message) {
        return new Response(type, false, message, null);
    }

    public MessageType type() {
        return type;
    }

    public boolean success() {
        return success;
    }

    public String message() {
        return message;
    }

    public Object payload() {
        return payload;
    }
}
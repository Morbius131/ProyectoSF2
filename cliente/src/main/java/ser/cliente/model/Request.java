package ser.cliente.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public final class Request {
    private final MessageType type;
    private final Map<String, String> data;

    public Request(MessageType type, Map<String, String> data) {
        this.type = type;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public static Request of(MessageType type) {
        return new Request(type, Map.of());
    }

    public static Request of(MessageType type, Map<String, String> data) {
        return new Request(type, data);
    }

    public MessageType type() {
        return type;
    }

    public Map<String, String> data() {
        return data;
    }

    public String serialize() {
        StringJoiner joiner = new StringJoiner(";", type.name() + "|", "");
        data.forEach((key, value) -> joiner.add(key + "=" + value));
        return joiner.toString();
    }
}
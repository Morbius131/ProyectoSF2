package ser.cliente.infrastructure;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class ServerJson {
    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build());
    }

    private ServerJson() {
    }

    static String write(Object value) throws IOException {
        return MAPPER.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readMap(String json) throws IOException {
        return MAPPER.readValue(json, Map.class);
    }

    static Map<String, Object> request(String type, String method, Map<String, Object> data) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", type);
        req.put("method", method);
        req.put("data", data);
        return req;
    }

    static ServerClient.ServerResponse parseResponse(String json) {
        try {
            Map<String, Object> map = readMap(json);
            String status = (String) map.getOrDefault("status", "ERROR");
            if ("OK".equals(status)) {
                return ServerClient.ServerResponse.ok(map.get("data"));
            }
            return ServerClient.ServerResponse.error("" + map.getOrDefault("message", json));
        } catch (Exception e) {
            return ServerClient.ServerResponse.error("Parse error: " + e.getMessage());
        }
    }
}

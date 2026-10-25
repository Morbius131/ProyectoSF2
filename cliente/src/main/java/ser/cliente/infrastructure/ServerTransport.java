package ser.cliente.infrastructure;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import ser.cliente.communication.ICommunication;
import ser.cliente.communication.TCPCommunication;
import ser.cliente.communication.UDPCommunications;

final class ServerTransport {

    private ServerTransport() {
    }

    static String sendSingle(Map<String, Object> request) throws IOException {
        ICommunication communication = ConnectionManager.getCommunication();
        if (communication == null || !communication.isConnected()) {
            throw new IOException("Sin conexion activa");
        }
        Optional<String> response = communication.send(ServerJson.write(request));
        return response.orElseThrow(() -> new IOException("Sin respuesta del servidor"));
    }

    static TCPCommunication tcp() {
        ICommunication communication = ConnectionManager.getCommunication();
        return communication instanceof TCPCommunication tcp ? tcp : null;
    }

    static UDPCommunications udp() {
        ICommunication communication = ConnectionManager.getCommunication();
        return communication instanceof UDPCommunications udp ? udp : null;
    }
}

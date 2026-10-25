package ser.cliente.communication;

import java.io.IOException;
import java.util.Optional;

public interface ICommunication {
    void connect(String host, int port) throws IOException;
    void disconnect();
    boolean isConnected();
    Optional<String> send(String payload) throws IOException;
}

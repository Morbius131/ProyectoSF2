package ser.cliente.infrastructure;

import ser.cliente.communication.ICommunication;
import ser.cliente.communication.TCPCommunication;
import ser.cliente.communication.UDPCommunications;
import ser.cliente.model.Protocol;

public final class FactoryCommunication {
    private FactoryCommunication() {
    }

    public static ICommunication create(Protocol protocol) {
        return protocol == Protocol.UDP ? new UDPCommunications() : new TCPCommunication();
    }
}
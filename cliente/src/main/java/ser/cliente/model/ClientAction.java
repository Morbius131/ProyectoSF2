package ser.cliente.model;

public enum ClientAction {
    LIST_CLIENTS("1. Listar los clientes conectados", MessageType.LIST_CLIENTS),
    LIST_SERVERS("2. Listar servidores", MessageType.LIST_SERVERS),
    LIST_DOCUMENTS("3. Listar los documentos (archivos y mensajes)", MessageType.LIST_DOCUMENTS),
    SEND_MESSAGE("4. Enviar mensajes", MessageType.SEND_MESSAGE),
    SEND_FILE("5. Enviar archivo", MessageType.SEND_FILE),
    RECEIVE_MESSAGE("6. Recibir mensaje", MessageType.RECEIVE_MESSAGE),
    RECEIVE_FILE("7. Recibir archivo", MessageType.RECEIVE_FILE),
    RECEIVE_LOGS("8. Recibir logs", MessageType.RECEIVE_LOGS),
    LIST_FACE_IMAGES("9. Listar imagenes de rostros", MessageType.LIST_FACE_IMAGES),
    IDENTIFY_EMOTIONS("10. Identificar emociones", MessageType.IDENTIFY_EMOTIONS);

    private final String label;
    private final MessageType messageType;

    ClientAction(String label, MessageType messageType) {
        this.label = label;
        this.messageType = messageType;
    }

    public String label() {
        return label;
    }

    public MessageType messageType() {
        return messageType;
    }

    @Override
    public String toString() {
        return label;
    }
}

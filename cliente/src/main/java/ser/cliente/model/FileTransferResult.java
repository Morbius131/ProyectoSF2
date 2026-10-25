package ser.cliente.model;

public record FileTransferResult(String fileName, long bytesSent, boolean success, String detail) {
}
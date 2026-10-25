package ser.cliente.model;

import java.util.List;

public record DocumentCatalog(List<FileDocument> archivos, List<MessageDocument> mensajes) {
}
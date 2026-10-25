package Cliente_Servidor.V1.Enums;

public enum MethodHTTP {
    POST,
    GET,
    LIST,
    /** Lista documentos para sincronización entre servidores (incluye hashes) */
    LIST_SYNC,
    /** Inicia una transferencia de archivo por chunks: envía metadatos y recibe uploadId */
    POST_CHUNK_INIT,
    /** Envía un chunk de datos (base64 parcial) */
    POST_CHUNK,
    /** Finaliza la transferencia: ensambla, hashea, encripta y persiste */
    POST_CHUNK_END,
    /** Desconecta un usuario del pool */
    DELETE,
    /** Identifica la emoción de una imagen */
    PREDICT,
    /** Obtiene una imagen de rostro con datos para sincronización */
    GET_IMAGE
}

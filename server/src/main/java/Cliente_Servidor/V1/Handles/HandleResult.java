package Cliente_Servidor.V1.Handles;

import Cliente_Servidor.V1.Modelos.Usuario;
import java.io.PrintWriter;
import java.util.List;
import java.util.function.Consumer;

public class HandleResult {
    private final String       responseJson;   // respuesta única (null si multilinea)
    private final List<String> lineas;         // respuesta multilinea (null si única)
    private final Usuario      usuario;
    private final Consumer<PrintWriter> streamingCallback; // para streaming progresivo

    /** Respuesta de una sola línea (uso normal). */
    public HandleResult(String responseJson, Usuario usuario) {
        this.responseJson = responseJson;
        this.lineas       = null;
        this.usuario      = usuario;
        this.streamingCallback = null;
    }

    /** Respuesta multilinea — para transferencia de chunks al cliente (todo en memoria). */
    public HandleResult(List<String> lineas, Usuario usuario) {
        this.responseJson = null;
        this.lineas       = lineas;
        this.usuario      = usuario;
        this.streamingCallback = null;
    }

    /** Respuesta streaming — el callback escribe directamente al PrintWriter sin cargar todo en memoria. */
    public HandleResult(Consumer<PrintWriter> streamingCallback) {
        this.responseJson = null;
        this.lineas = null;
        this.usuario = null;
        this.streamingCallback = streamingCallback;
    }

    public boolean isMultilinea()       { return lineas != null; }
    public boolean isStreaming()        { return streamingCallback != null; }
    public String getResponseJson()     { return responseJson; }
    public List<String> getLineas()     { return lineas; }
    public Usuario getUsuario()         { return usuario; }
    public Consumer<PrintWriter> getStreamingCallback() { return streamingCallback; }
}

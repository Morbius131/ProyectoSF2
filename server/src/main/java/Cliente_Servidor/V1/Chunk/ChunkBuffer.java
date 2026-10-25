package Cliente_Servidor.V1.Chunk;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Acumula los chunks de un archivo en un fichero temporal.
 *
 * Soporta escritura DESORDENADA (por índice) usando RandomAccessFile,
 * lo que permite que el cliente envíe chunks en UDP puro sin garantía
 * de orden y el servidor los ensambla correctamente.
 *
 * Ciclo de vida:
 *  1. new ChunkBuffer(...)          → crea el fichero temporal pre-allocado
 *  2. writeChunk(index, bytes) × N  → escribe en la posición correcta
 *  3. finish()                      → cierra y retorna la ruta
 *  4. delete()                      → limpia el fichero temporal
 */
public class ChunkBuffer {

    private static final String DIR_TMP = "./archivos_temp/";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_");

    private final String uploadId;
    private final String nombre;
    private final String extension;
    private final long   tamanioEsperado;
    private final int    totalChunks;
    private final long   usuarioId;
    private final String usuarioNombre;
    private final String tipoPropietario;
    private final String destinatario;
    private final String idUnico;
    private final int    chunkSize; // tamaño de cada chunk excepto el último

    private final File             archivoTemp;
    private final RandomAccessFile raf;

    private int     chunksRecibidos = 0;
    private long    bytesEscritos   = 0;
    private boolean cerrado         = false;

    /**
     * @param chunkSize tamaño en bytes de cada chunk (el último puede ser menor).
     *                  Necesario para calcular el offset de cada índice.
     *                  Si se usa escritura secuencial (appendChunk), pasar 0.
     */
    public ChunkBuffer(String uploadId, String nombre, String extension,
                       long tamanioEsperado, int totalChunks,
                       long usuarioId, String usuarioNombre,
                       String tipoPropietario, String destinatario,
                       String idUnico, int chunkSize) throws IOException {

        this.uploadId        = uploadId;
        this.nombre          = nombre;
        this.extension       = extension;
        this.tamanioEsperado = tamanioEsperado;
        this.totalChunks     = totalChunks;
        this.usuarioId       = usuarioId;
        this.usuarioNombre   = usuarioNombre;
        this.tipoPropietario = tipoPropietario;
        this.destinatario    = destinatario;
        this.idUnico         = idUnico;
        this.chunkSize       = chunkSize;

        new File(DIR_TMP).mkdirs();
        String ts = LocalDateTime.now().format(FMT);
        archivoTemp = new File(DIR_TMP + ts + uploadId + "_" + nombre + "." + extension + ".tmp");
        raf = new RandomAccessFile(archivoTemp, "rw");

        // Pre-alocar el espacio para escritura aleatoria eficiente
        if (tamanioEsperado > 0) {
            raf.setLength(tamanioEsperado);
        }
    }

    /** Constructor de compatibilidad sin idUnico. */
    public ChunkBuffer(String uploadId, String nombre, String extension,
                       long tamanioEsperado, int totalChunks,
                       long usuarioId, String usuarioNombre,
                       String tipoPropietario, String destinatario,
                       int chunkSize) throws IOException {
        this(uploadId, nombre, extension, tamanioEsperado, totalChunks,
             usuarioId, usuarioNombre, tipoPropietario, destinatario, null, chunkSize);
    }

    /** Constructor de compatibilidad sin destinatario ni chunkSize. */
    public ChunkBuffer(String uploadId, String nombre, String extension,
                       long tamanioEsperado, int totalChunks,
                       long usuarioId, String usuarioNombre,
                       String tipoPropietario) throws IOException {
        this(uploadId, nombre, extension, tamanioEsperado, totalChunks,
             usuarioId, usuarioNombre, tipoPropietario, null, null, 0);
    }

    /** Constructor de compatibilidad sin destinatario (con chunkSize). */
    public ChunkBuffer(String uploadId, String nombre, String extension,
                       long tamanioEsperado, int totalChunks,
                       long usuarioId, String usuarioNombre,
                       String tipoPropietario, int chunkSize) throws IOException {
        this(uploadId, nombre, extension, tamanioEsperado, totalChunks,
             usuarioId, usuarioNombre, tipoPropietario, null, null, chunkSize);
    }

    /**
     * Escribe los bytes de un chunk en su posición correcta dentro del archivo.
     * Thread-safe. Soporta llegada desordenada.
     *
     * @param chunkIndex índice del chunk (0-based)
     * @param bytes      datos binarios del chunk (ya decodificados de Base64)
     */
    public synchronized void writeChunk(int chunkIndex, byte[] bytes) throws IOException {
        if (cerrado) throw new IllegalStateException("Buffer cerrado para uploadId=" + uploadId);
        long offset = (chunkSize > 0)
                ? (long) chunkIndex * chunkSize
                : calcularOffsetSecuencial();
        raf.seek(offset);
        raf.write(bytes);
        bytesEscritos += bytes.length;
        chunksRecibidos++;
    }

    /**
     * Escritura secuencial (compatibilidad hacia atrás con appendChunk).
     * Agrega al final del archivo en el orden en que se llama.
     */
    public synchronized void appendChunk(byte[] bytes) throws IOException {
        if (cerrado) throw new IllegalStateException("Buffer cerrado para uploadId=" + uploadId);
        raf.seek(raf.length());
        raf.write(bytes);
        bytesEscritos += bytes.length;
        chunksRecibidos++;
    }

    private long calcularOffsetSecuencial() throws IOException {
        return raf.length();
    }

    /**
     * Cierra el RandomAccessFile. Llamar antes de leer el fichero.
     * @return ruta absoluta del fichero temporal ensamblado
     */
    public synchronized String finish() throws IOException {
        if (!cerrado) {
            raf.close();
            cerrado = true;
        }
        return archivoTemp.getAbsolutePath();
    }

    /** Elimina el fichero temporal del disco. */
    public void delete() {
        if (archivoTemp.exists()) archivoTemp.delete();
    }

    public boolean estaCompleto() {
        return chunksRecibidos >= totalChunks;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public String getUploadId()        { return uploadId; }
    public String getNombre()          { return nombre; }
    public String getExtension()       { return extension; }
    public long   getTamanioEsperado() { return tamanioEsperado; }
    public int    getTotalChunks()     { return totalChunks; }
    public int    getChunksRecibidos() { return chunksRecibidos; }
    public long   getBytesEscritos()   { return bytesEscritos; }
    public long   getUsuarioId()       { return usuarioId; }
    public String getUsuarioNombre()   { return usuarioNombre; }
    public String getTipoPropietario() { return tipoPropietario; }
    public String getDestinatario()    { return destinatario; }
    public String getIdUnico()         { return idUnico; }
    public File   getArchivoTemp()     { return archivoTemp; }
}

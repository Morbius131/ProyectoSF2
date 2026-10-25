package Cliente_Servidor.V1.Util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class AlmacenamientoArchivos {

    private static final String DIR_BASE = "./archivos_originales/";
    private static final String DIR_ENC  = "./archivos_encriptados/";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_");

    static { crearDirectorios(); }

    private AlmacenamientoArchivos() {}

    private static void crearDirectorios() {
        new File(DIR_BASE).mkdirs();
        new File(DIR_ENC).mkdirs();
    }

    public static String guardarArchivo(String nombre, String extension, String contenidoBase64) throws IOException {
        crearDirectorios();
        String ts = LocalDateTime.now().format(FMT);
        Path ruta = Paths.get(DIR_BASE, ts + nombre + "." + extension);
        byte[] bytes = Base64.getDecoder().decode(contenidoBase64);
        try (FileOutputStream fos = new FileOutputStream(ruta.toFile())) {
            int offset = 0, bloque = 8192;
            while (offset < bytes.length) {
                int len = Math.min(bloque, bytes.length - offset);
                fos.write(bytes, offset, len);
                offset += len;
            }
        }
        return ruta.toAbsolutePath().toString();
    }

    public static String guardarMensaje(String asunto, String contenido) throws IOException {
        crearDirectorios();
        String ts = LocalDateTime.now().format(FMT);
        Path ruta = Paths.get(DIR_BASE, ts + "msg_" + asunto.replaceAll("[^a-zA-Z0-9]", "_") + ".txt");
        Files.writeString(ruta, contenido);
        return ruta.toAbsolutePath().toString();
    }

    public static String leerComoBase64(String rutaAbsoluta) throws IOException {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(rutaAbsoluta)));
    }

    public static String moverDesdeTemp(File archivoTemp, String nombre, String extension) throws IOException {
        crearDirectorios();
        String ts = LocalDateTime.now().format(FMT);
        Path destino = Paths.get(DIR_BASE, ts + nombre + "." + extension);
        try {
            Files.move(archivoTemp.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try (InputStream is  = new BufferedInputStream(new FileInputStream(archivoTemp), 65536);
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(destino.toFile()), 65536)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            }
            archivoTemp.delete();
        }
        return destino.toAbsolutePath().toString();
    }

    /**
     * Guarda el archivo encriptado en disco (archivos_encriptados/) en streaming.
     * Lee desde encTempFile y escribe al destino sin cargar en RAM.
     *
     * @param encTempFile  archivo temporal ya encriptado
     * @param nombre       nombre lógico (sin extensión)
     * @param extension    extensión original
     * @return ruta absoluta del archivo .enc guardado
     */
    public static String guardarEncriptado(File encTempFile, String nombre, String extension) throws IOException {
        crearDirectorios();
        String ts = LocalDateTime.now().format(FMT);
        Path destino = Paths.get(DIR_ENC, ts + nombre + "." + extension + ".enc");
        try {
            Files.move(encTempFile.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try (InputStream is  = new BufferedInputStream(new FileInputStream(encTempFile), 65536);
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(destino.toFile()), 65536)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            }
            encTempFile.delete();
        }
        return destino.toAbsolutePath().toString();
    }
}

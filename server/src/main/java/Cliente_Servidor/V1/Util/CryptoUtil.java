package Cliente_Servidor.V1.Util;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utilidad para hash SHA-256 y encriptación AES-128.
 * La clave AES es fija para simplificar el ejemplo (en producción usar keystore).
 *
 * Métodos *Stream: procesan datos en bloques de 64 KB sin cargar el archivo
 * completo en RAM; ideales para archivos grandes enviados por chunks.
 */
public class CryptoUtil {

    // Clave AES de 16 bytes (128 bits)
    private static final byte[] AES_KEY = "C1i3n5t7e9S0r2v4".getBytes(StandardCharsets.UTF_8);

    // IV fijo de 16 bytes para AES/CTR (en producción generar IV aleatorio y guardarlo junto al cifrado)
    private static final byte[] AES_IV  = "I1v3F5i7j9K0l2m4".getBytes(StandardCharsets.UTF_8);

    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB

    private CryptoUtil() {}

    // ── Hash ──────────────────────────────────────────────────────────────────

    /** Calcula SHA-256 de un String (texto o base64). */
    public static String sha256(String contenido) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(contenido.getBytes(StandardCharsets.UTF_8));
            return hexString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    /** Calcula SHA-256 sobre un arreglo de bytes (archivos binarios). */
    public static String sha256Bytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hexString(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    /**
     * Calcula SHA-256 leyendo desde un InputStream en bloques de 64 KB.
     * No carga el archivo completo en RAM.
     */
    public static String sha256Stream(InputStream is) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = is.read(buf)) != -1) {
            digest.update(buf, 0, n);
        }
        return hexString(digest.digest());
    }

    /** Calcula SHA-256 leyendo un fichero en bloques de 64 KB (sin cargar en RAM). */
    public static String sha256Archivo(File archivo) {
        try (InputStream is = new BufferedInputStream(new FileInputStream(archivo), BUFFER_SIZE)) {
            return sha256Stream(is);
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular hash de archivo: " + e.getMessage(), e);
        }
    }

    // ── Encriptación AES ──────────────────────────────────────────────────────

    /** Encripta un String con AES/ECB/PKCS5Padding y retorna Base64 (mensajes de texto). */
    public static String encriptar(String texto) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encBytes = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al encriptar: " + e.getMessage(), e);
        }
    }

    /** Desencripta un Base64 con AES/ECB/PKCS5Padding (mensajes de texto). */
    public static String desencriptar(String textoCifrado) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(textoCifrado);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error al desencriptar: " + e.getMessage(), e);
        }
    }

    /**
     * Encripta bytes binarios en streaming usando AES/CTR/NoPadding.
     * Lee desde {@code is} y escribe el resultado cifrado en {@code os}
     * en bloques de 64 KB → memoria máxima usada: ~128 KB (doble buffer).
     *
     * @param is InputStream con los bytes originales
     * @param os OutputStream donde se escriben los bytes cifrados
     */
    public static void encriptarStream(InputStream is, OutputStream os) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(AES_IV);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding"); // CTR ideal para streaming
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        try (CipherOutputStream cos = new CipherOutputStream(new BufferedOutputStream(os, BUFFER_SIZE), cipher)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = is.read(buf)) != -1) {
                cos.write(buf, 0, n);
            }
        }
    }

    /**
     * Desencripta bytes binarios en streaming usando AES/CTR/NoPadding.
     *
     * @param is InputStream con los bytes cifrados
     * @param os OutputStream donde se escriben los bytes originales
     */
    public static void desencriptarStream(InputStream is, OutputStream os) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(AES_IV);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        try (CipherOutputStream cos = new CipherOutputStream(new BufferedOutputStream(os, BUFFER_SIZE), cipher)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = is.read(buf)) != -1) {
                cos.write(buf, 0, n);
            }
        }
    }

    // ── Utilidad ──────────────────────────────────────────────────────────────

    private static String hexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}

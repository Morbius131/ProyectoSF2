package Cliente_Servidor.V1.Util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilidad para mostrar información de actividad en la consola del servidor
 * de forma constante mientras esté en ejecución.
 */
public class ConsolaUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SEP = "─".repeat(70);

    private ConsolaUtil() {}

    public static void encabezado(String titulo) {
        String ts = LocalDateTime.now().format(FMT);
        System.out.println("\n" + SEP);
        System.out.printf("  [%s]  %s%n", ts, titulo.toUpperCase());
        System.out.println(SEP);
    }

    public static void info(String mensaje) {
        System.out.printf("[%s] ℹ  %s%n", ts(), mensaje);
    }

    public static void ok(String mensaje) {
        System.out.printf("[%s] ✔  %s%n", ts(), mensaje);
    }

    public static void warn(String mensaje) {
        System.out.printf("[%s] ⚠  %s%n", ts(), mensaje);
    }

    public static void error(String mensaje) {
        System.err.printf("[%s] ✖  %s%n", ts(), mensaje);
    }

    public static void conexion(String ip, String protocolo, String usuario) {
        System.out.printf("[%s] 🔌 CONEXIÓN  protocolo=%-3s  ip=%-15s  usuario=%s%n",
                ts(), protocolo, ip, usuario);
    }

    public static void desconexion(String ip, String usuario) {
        System.out.printf("[%s] 🔌 DESCONEXIÓN  ip=%-15s  usuario=%s%n", ts(), ip, usuario);
    }

    public static void documento(String accion, String tipo, String nombre, String usuario) {
        System.out.printf("[%s] 📄 %-20s  tipo=%-8s  doc=%-30s  usuario=%s%n",
                ts(), accion, tipo, nombre, usuario);
    }

    public static void poolEstado(int activos, int max) {
        System.out.printf("[%s] 🏊 POOL  activos=%d / max=%d%n", ts(), activos, max);
    }

    // ── Sincronización con barra de progreso ──────────────────────────────────

    public static void syncInicio(String peerId, int totalDocs) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║  🔄 SINCRONIZACIÓN INICIADA con %-37s ║%n", peerId);
        System.out.printf("║     Documentos a revisar: %-43d ║%n", totalDocs);
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    public static void syncProgreso(int actual, int total, String nombreDoc, String estado) {
        int porcentaje = total > 0 ? (actual * 100) / total : 0;
        int barraLen = 30;
        int llenos = (porcentaje * barraLen) / 100;
        String barra = "█".repeat(llenos) + "░".repeat(barraLen - llenos);

        String estadoIcono = switch (estado) {
            case "DESCARGANDO" -> "⬇";
            case "YA_EXISTE" -> "✓";
            case "ERROR" -> "✖";
            case "OMITIDO" -> "⊘";
            default -> "•";
        };

        String nombreCorto = nombreDoc.length() > 25
                ? nombreDoc.substring(0, 22) + "..."
                : nombreDoc;

        System.out.printf("\r[%s] [%s] %3d%% │ %s %-25s (%d/%d)    ",
                ts(), barra, porcentaje, estadoIcono, nombreCorto, actual, total);
        System.out.flush();
    }

    public static void syncDescargando(String nombreDoc, int chunkActual, int totalChunks, long bytesDescargados) {
        String nombreCorto = nombreDoc.length() > 20
                ? nombreDoc.substring(0, 17) + "..."
                : nombreDoc;

        int porcentaje = totalChunks > 0 ? (chunkActual * 100) / totalChunks : 0;
        String tamano = formatearTamano(bytesDescargados);

        System.out.printf("\r[%s]     ⬇ %-20s chunk %d/%d (%3d%%) - %s    ",
                ts(), nombreCorto, chunkActual, totalChunks, porcentaje, tamano);
        System.out.flush();
    }

    public static void syncDocumentoOk(String nombreDoc) {
        System.out.printf("\r[%s] ✔  Sincronizado: %-50s%n", ts(), nombreDoc);
    }

    public static void syncDocumentoYaExiste(String nombreDoc) {
        System.out.printf("\r[%s] ✓  Ya existe: %-52s%n", ts(), nombreDoc);
    }

    public static void syncDocumentoError(String nombreDoc, String error) {
        System.out.printf("\r[%s] ✖  Error: %-20s - %s%n", ts(), nombreDoc, error);
    }

    public static void syncFin(String peerId, int nuevos, int yaExistian, int errores, long tiempoMs) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║  ✅ SINCRONIZACIÓN COMPLETADA con %-35s ║%n", peerId);
        System.out.printf("║     Nuevos: %-3d │ Ya existían: %-3d │ Errores: %-3d │ Tiempo: %.1fs   ║%n",
                nuevos, yaExistian, errores, tiempoMs / 1000.0);
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    public static void syncSinDocumentos(String peerId) {
        System.out.printf("[%s] ℹ  Sync con %s: sin documentos nuevos%n", ts(), peerId);
    }

    private static String formatearTamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String ts() {
        return LocalDateTime.now().format(FMT);
    }
}

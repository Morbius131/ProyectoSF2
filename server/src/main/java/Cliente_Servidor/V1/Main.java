package Cliente_Servidor.V1;
import java.util.Scanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient          
public class Main {
    public static void main(String[] args) {
        configurarDesdeConsola();
        SpringApplication.run(Main.class, args);
    }

    private static void configurarDesdeConsola() {
        if (System.getProperty("servidor.nombre") != null) return;

        Scanner sc = new Scanner(System.in);
        System.out.println();
        System.out.println("+------------------------------------------------+");
        System.out.println("|        CONFIGURACION DEL SERVIDOR              |");
        System.out.println("|  Presiona Enter para aceptar el valor [def]    |");
        System.out.println("+------------------------------------------------+");
        System.out.println();

        String nombre       = leer(sc, "Nombre del servidor", "Servidor-A");
        String puertoTCPStr = leer(sc, "Puerto TCP         ", "5000");
        String puertoUDPStr = leer(sc, "Puerto UDP         ", "6000");
        String db           = leer(sc, "Base de datos      ", "cliente_servidor_db");

        System.out.println();
        String conectar = leer(sc, "Conectar a la red distribuida? (s/n)", "n");
        String amigos = "";
        if (conectar.equalsIgnoreCase("s") || conectar.equalsIgnoreCase("si")) {
            amigos = leer(sc, "  Peer de bootstrap (host:puerto)", "localhost:5000");
        }

        // ── Eureka ────────────────────────────────────────────────────────────
        String usarEureka = leer(sc, "Conectar a Eureka? (s/n)      ", "n");
        String eurekaIp = "";
        if (usarEureka.equalsIgnoreCase("s") || usarEureka.equalsIgnoreCase("si")) {
            eurekaIp = leer(sc, "  IP del servidor Eureka       ", "localhost");
            System.setProperty("eureka.client.service-url.defaultZone",
                    "http://" + eurekaIp + ":8761/eureka/");
            System.setProperty("spring.application.name", nombre);
        } else {
            System.setProperty("eureka.client.enabled", "false");
            System.setProperty("spring.cloud.discovery.enabled", "false");
        }
        // ─────────────────────────────────────────────────────────────────────

        int puertoTCP = parseIntSafe(puertoTCPStr, 5000);
        int puertoUDP = parseIntSafe(puertoUDPStr, 6000);

        System.setProperty("servidor.nombre",     nombre);
        System.setProperty("servidor.host",       "localhost");
        System.setProperty("servidor.tcp.puerto", String.valueOf(puertoTCP));
        System.setProperty("servidor.udp.puerto", String.valueOf(puertoUDP));
        System.setProperty("servidor.puerto",     String.valueOf(puertoTCP));
        System.setProperty("servidor.amigos",     amigos);
        System.setProperty("spring.datasource.url",
                "jdbc:mysql://localhost:3306/" + db
                + "?createDatabaseIfNotExist=true&useSSL=false"
                + "&serverTimezone=UTC&rewriteBatchedStatements=true"
                + "&allowMultiQueries=true&allowPublicKeyRetrieval=true");

        System.out.println();
        System.out.println("+------------------------------------------------+");
        System.out.printf("|  Servidor : %-34s|%n", nombre);
        System.out.printf("|  TCP: %-5d   UDP: %-5d   BD: %-12s|%n", puertoTCP, puertoUDP, db);
        System.out.printf("|  Peers    : %-34s|%n", amigos.isBlank() ? "(standalone)" : amigos);
        if (!eurekaIp.isEmpty()) {
            System.out.printf("|  Eureka   : %-34s|%n", eurekaIp + ":8761");
        }
        System.out.println("+------------------------------------------------+");
        System.out.println();
    }

    private static String leer(Scanner sc, String etiqueta, String defecto) {
        System.out.printf("  %-36s [%s]: ", etiqueta, defecto);
        String linea = sc.nextLine().trim();
        return linea.isEmpty() ? defecto : linea;
    }

    private static int parseIntSafe(String s, int defecto) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return defecto; }
    }
}
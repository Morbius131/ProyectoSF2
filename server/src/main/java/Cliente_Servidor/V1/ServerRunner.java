package Cliente_Servidor.V1;

import Cliente_Servidor.V1.Pool.UsuarioConexionPool;
import Cliente_Servidor.V1.Servidor.ServidorTCP;
import Cliente_Servidor.V1.Servidor.ServidorUDP;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Arranca los servidores TCP y UDP en hilos separados.
 * Los puertos se leen de application.properties para poder
 * ejecutar múltiples instancias sin tocar el código.
 */
@Component
public class ServerRunner {

    @Value("${servidor.tcp.puerto:5000}") private int puertoTCP;
    @Value("${servidor.udp.puerto:6000}") private int puertoUDP;

    private final ServidorTCP tcpServer;
    private final ServidorUDP udpServer;
    private final UsuarioConexionPool pool;

    public ServerRunner(ServidorTCP tcpServer, ServidorUDP udpServer, UsuarioConexionPool pool) {
        this.tcpServer = tcpServer;
        this.udpServer = udpServer;
        this.pool = pool;
    }

    @PostConstruct
    public void startServers() {
        ConsolaUtil.encabezado("Sistema Cliente/Servidor iniciando");

        new Thread(() -> {
            try { tcpServer.start(puertoTCP); }
            catch (Exception e) { ConsolaUtil.error("TCP: " + e.getMessage()); }
        }, "hilo-tcp").start();

        new Thread(() -> {
            try { udpServer.start(puertoUDP); }
            catch (Exception e) { ConsolaUtil.error("UDP: " + e.getMessage()); }
        }, "hilo-udp").start();

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30_000);
                    ConsolaUtil.poolEstado(pool.totalActivas(), pool.getMaxConexiones());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "hilo-monitor").start();

        ConsolaUtil.info("Servidores TCP (" + puertoTCP + ") y UDP (" + puertoUDP + ") en ejecución.");
        ConsolaUtil.info("Directorio de archivos originales: ./archivos_originales/");
    }
}

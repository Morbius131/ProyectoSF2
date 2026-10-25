package Cliente_Servidor.V1.Pool;

import Cliente_Servidor.V1.Modelos.ServidorAmigo;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ServidorAmigoPool {

    private final Map<String, ServidorAmigo> servidores = new ConcurrentHashMap<>();
    private final Set<String> transferenciasActivas = ConcurrentHashMap.newKeySet();

    public void registrarOActualizar(ServidorAmigo s) {
        s.setConectado(true);
        s.setUltimaConexion(LocalDateTime.now());

        // Verificar si ya existe un peer equivalente con diferente representación de host
        ServidorAmigo existente = buscarEquivalente(s.getHost(), s.getPuerto());
        if (existente != null && !existente.getId().equals(s.getId())) {
            // Actualizar el existente en lugar de agregar duplicado
            existente.setConectado(true);
            existente.setUltimaConexion(LocalDateTime.now());
            existente.setClientesConectados(s.getClientesConectados());
            if (s.getNombre() != null && !s.getNombre().isBlank()) {
                existente.setNombre(s.getNombre());
            }
            return;
        }

        servidores.put(s.getId(), s);
    }

    /**
     * Busca un servidor equivalente que puede estar registrado con una representación
     * diferente del host (localhost vs IP vs hostname).
     */
    private ServidorAmigo buscarEquivalente(String host, int puerto) {
        for (ServidorAmigo peer : servidores.values()) {
            if (peer.getPuerto() != puerto) continue;
            if (sonHostsEquivalentes(host, peer.getHost())) {
                return peer;
            }
        }
        return null;
    }

    /**
     * Compara dos hosts considerando equivalencias como localhost/127.0.0.1
     * y resolución DNS.
     */
    private boolean sonHostsEquivalentes(String host1, String host2) {
        if (host1 == null || host2 == null) return false;

        String h1 = normalizarHost(host1);
        String h2 = normalizarHost(host2);
        if (h1.equals(h2)) return true;

        // Verificar si ambos son locales
        if (esHostLocal(host1) && esHostLocal(host2)) return true;

        // Intentar resolver IPs
        try {
            InetAddress addr1 = InetAddress.getByName(host1);
            InetAddress addr2 = InetAddress.getByName(host2);
            return addr1.equals(addr2);
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizarHost(String host) {
        if (host == null) return "";
        host = host.trim().toLowerCase();
        if (host.equals("127.0.0.1") || host.equals("::1")) {
            return "localhost";
        }
        return host;
    }

    private boolean esHostLocal(String host) {
        if (host == null) return false;
        host = host.trim().toLowerCase();
        return host.equals("localhost") ||
               host.equals("127.0.0.1") ||
               host.equals("::1") ||
               host.startsWith("127.");
    }

    private static final int MAX_FALLOS_ANTES_DESCONECTAR = 3;

    // ── Control de transferencias activas ─────────────────────────────────────

    /** Marca que hay una transferencia activa con este peer (no desconectar). */
    public void iniciarTransferencia(String peerId) {
        transferenciasActivas.add(peerId);
    }

    /** Marca que terminó la transferencia con este peer. */
    public void finalizarTransferencia(String peerId) {
        transferenciasActivas.remove(peerId);
    }

    /** Verifica si hay transferencia activa con este peer. */
    public boolean tieneTransferenciaActiva(String peerId) {
        return transferenciasActivas.contains(peerId);
    }

    // ── Gestión de conexiones ─────────────────────────────────────────────────

    public void marcarDesconectado(String id) {
        // NO desconectar si hay transferencia activa
        if (transferenciasActivas.contains(id)) {
            return;
        }
        ServidorAmigo s = servidores.get(id);
        if (s != null) {
            s.setConectado(false);
            s.setUltimaConexion(LocalDateTime.now());
            s.setClientesConectados(new ArrayList<>());
        }
    }

    /**
     * Registra un fallo de heartbeat. Retorna true si se alcanzó el límite
     * y el peer debe marcarse como desconectado.
     * NO cuenta fallos si hay transferencia activa.
     */
    public boolean registrarFallo(String id) {
        // Si hay transferencia activa, ignorar el fallo
        if (transferenciasActivas.contains(id)) {
            return false;
        }

        ServidorAmigo s = servidores.get(id);
        if (s == null) {
            // Buscar por equivalencia
            String[] parts = id.split(":");
            if (parts.length == 2) {
                try {
                    s = buscarEquivalente(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (s != null) {
            s.incrementarFallos();
            return s.getFallosConsecutivos() >= MAX_FALLOS_ANTES_DESCONECTAR;
        }
        return false;
    }

    /**
     * Resetea el contador de fallos cuando el heartbeat es exitoso.
     */
    public void resetearFallos(String id) {
        ServidorAmigo s = servidores.get(id);
        if (s != null) {
            s.resetearFallos();
        }
    }

    public void actualizarClientes(String id, List<String> clientes) {
        ServidorAmigo s = servidores.get(id);
        if (s != null) {
            s.setClientesConectados(clientes != null ? clientes : new ArrayList<>());
        }
    }

    public boolean isConectado(String id) {
        // Primero búsqueda exacta
        ServidorAmigo s = servidores.get(id);
        if (s != null) return s.isConectado();

        // Si no, buscar por equivalencia de host
        String[] parts = id.split(":");
        if (parts.length == 2) {
            try {
                int puerto = Integer.parseInt(parts[1]);
                ServidorAmigo equiv = buscarEquivalente(parts[0], puerto);
                return equiv != null && equiv.isConectado();
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    public boolean isKnown(String id) {
        if (servidores.containsKey(id)) return true;

        // Verificar por equivalencia de host
        String[] parts = id.split(":");
        if (parts.length == 2) {
            try {
                int puerto = Integer.parseInt(parts[1]);
                return buscarEquivalente(parts[0], puerto) != null;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    /**
     * Busca un servidor por host:puerto considerando equivalencias.
     * Útil cuando el mismo servidor puede estar registrado con localhost o su IP.
     */
    public Optional<ServidorAmigo> buscarPorHostPuerto(String host, int puerto) {
        // Primero búsqueda exacta
        String id = host + ":" + puerto;
        ServidorAmigo s = servidores.get(id);
        if (s != null) return Optional.of(s);

        // Si no, buscar por equivalencia
        return Optional.ofNullable(buscarEquivalente(host, puerto));
    }

    public List<ServidorAmigo> listarConectados() {
        return servidores.values().stream()
                .filter(ServidorAmigo::isConectado)
                .collect(Collectors.toList());
    }

    public List<ServidorAmigo> listarDesconectados() {
        return servidores.values().stream()
                .filter(s -> !s.isConectado())
                .collect(Collectors.toList());
    }

    public List<ServidorAmigo> listarTodos() {
        return new ArrayList<>(servidores.values());
    }

    public Optional<ServidorAmigo> buscarPorId(String id) {
        return Optional.ofNullable(servidores.get(id));
    }

    /** Devuelve todos los clientes externos (de peers conectados) formateados para la respuesta. */
    public List<Map<String, String>> obtenerClientesExternos() {
        List<Map<String, String>> resultado = new ArrayList<>();
        for (ServidorAmigo s : servidores.values()) {
            if (!s.isConectado()) continue;
            for (String cliente : s.getClientesConectados()) {
                Map<String, String> e = new LinkedHashMap<>();
                e.put("usuario", cliente);
                e.put("ip", s.getHost());
                e.put("protocolo", "TCP");
                e.put("horaInicio", s.getUltimaConexion() != null
                        ? s.getUltimaConexion().toString() : "N/D");
                e.put("servidor", s.getNombre() + " (" + s.getId() + ")");
                resultado.add(e);
            }
        }
        return resultado;
    }

    /** Devuelve el id del servidor que tiene conectado al cliente, o null si es local/desconocido. */
    public String encontrarServidorDeCliente(String nombreCliente) {
        for (ServidorAmigo s : servidores.values()) {
            if (s.isConectado() && s.getClientesConectados().contains(nombreCliente)) {
                return s.getId();
            }
        }
        return null;
    }
}

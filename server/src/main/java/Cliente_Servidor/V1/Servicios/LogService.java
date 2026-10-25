package Cliente_Servidor.V1.Servicios;

import Cliente_Servidor.V1.DTO.LogDTO;
import Cliente_Servidor.V1.Enums.TipoLog;
import Cliente_Servidor.V1.Modelos.Logs;
import Cliente_Servidor.V1.Modelos.Usuario;
import Cliente_Servidor.V1.Repository.LogsRepository;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class LogService {

    private final LogsRepository logRepository;

    public LogService(LogsRepository logRepository) {
        this.logRepository = logRepository;
    }

    public void registrar(LogDTO logDTO) {
        Logs log = new Logs();
        log.setUsuario(logDTO.getUsuario());
        log.setTipoLog(logDTO.getTipo());
        log.setMensaje(logDTO.getMensaje());
        log.setFechaHora(LocalDateTime.now());
        logRepository.save(log);
        ConsolaUtil.info("LOG [" + logDTO.getTipo() + "] " + logDTO.getMensaje());
    }

    public void registrar(Usuario usuario, TipoLog tipo, String mensaje) {
        Logs log = new Logs();
        log.setUsuario(usuario);
        log.setTipoLog(tipo);
        log.setMensaje(mensaje);
        log.setFechaHora(LocalDateTime.now());
        logRepository.save(log);
        ConsolaUtil.info("LOG [" + tipo + "] " + mensaje);
    }

    public List<Logs> listarTodos() {
        return logRepository.findAll();
    }

    public List<LogDTO> listarComoDTO() {
        return logRepository.findAll()
                .stream()
                .map(LogDTO::desde)
                .collect(Collectors.toList());
    }
}

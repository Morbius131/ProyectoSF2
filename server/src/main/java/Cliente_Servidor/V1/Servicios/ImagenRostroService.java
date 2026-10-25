package Cliente_Servidor.V1.Servicios;

import org.springframework.stereotype.Service;

import Cliente_Servidor.V1.Modelos.ImagenRostro;
import Cliente_Servidor.V1.Repository.ImagenRostroRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ImagenRostroService {

    private final ImagenRostroRepository repository;

    public ImagenRostroService(ImagenRostroRepository repository) {
        this.repository = repository;
    }

    public ImagenRostro guardar(String nombre, String extension, Long tamanio, Long documentoId) {
        ImagenRostro imagen = new ImagenRostro(nombre, extension, tamanio, documentoId, LocalDateTime.now());
        return repository.save(imagen);
    }

    public ImagenRostro guardar(String nombre, String extension, Long tamanio, Long documentoId, String emocion) {
        ImagenRostro imagen = new ImagenRostro(nombre, extension, tamanio, documentoId, LocalDateTime.now(), emocion);
        return repository.save(imagen);
    }

    public ImagenRostro actualizarEmocion(Long id, String emocion) {
        ImagenRostro imagen = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Imagen de rostro no encontrada: " + id));
        imagen.setEmocion(emocion);
        return repository.save(imagen);
    }

    public List<ImagenRostro> listar() {
        return repository.findAllByOrderByFechaHoraDesc();
    }

    public Optional<ImagenRostro> buscarPorId(Long id) {
        return repository.findById(id);
    }

    public long contar() {
        return repository.count();
    }

    public boolean existePorDocumentoId(Long documentoId) {
        return repository.existsByDocumentoId(documentoId);
    }
}

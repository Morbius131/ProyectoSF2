package Cliente_Servidor.V1.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Cliente_Servidor.V1.Modelos.ImagenRostro;

@Repository
public interface ImagenRostroRepository extends JpaRepository<ImagenRostro, Long> {
    List<ImagenRostro> findAllByOrderByFechaHoraDesc();

    boolean existsByDocumentoId(Long documentoId);
}

package Cliente_Servidor.V1.Repository;

import Cliente_Servidor.V1.Modelos.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    /** Verifica si existe un documento con el hash dado (eficiente, no carga datos). */
    @Query("SELECT COUNT(d) > 0 FROM Documento d WHERE d.hash = :hash")
    boolean existsByHash(@Param("hash") String hash);

    /** Busca un documento por su hash. */
    @Query("SELECT d FROM Documento d WHERE d.hash = :hash")
    java.util.Optional<Documento> findByHash(@Param("hash") String hash);

    /** Verifica si existe un documento con el idUnico dado. */
    @Query("SELECT COUNT(d) > 0 FROM Documento d WHERE d.idUnico = :idUnico")
    boolean existsByIdUnico(@Param("idUnico") String idUnico);

    /** Busca un documento por su idUnico. */
    @Query("SELECT d FROM Documento d WHERE d.idUnico = :idUnico")
    java.util.Optional<Documento> findByIdUnico(@Param("idUnico") String idUnico);
}

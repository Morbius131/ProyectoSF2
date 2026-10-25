package Cliente_Servidor.V1.Repository;

import Cliente_Servidor.V1.Modelos.ArchivoChunk;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchivoChunkRepository extends JpaRepository<ArchivoChunk, Long> {

    @Query("SELECT c FROM ArchivoChunk c WHERE c.documento.id = :docId ORDER BY c.numeroChunk ASC")
    List<ArchivoChunk> findByDocumentoIdOrdenado(@Param("docId") Long docId);

    /** Cuenta chunks sin cargar los datos BLOB. */
    @Query("SELECT COUNT(c) FROM ArchivoChunk c WHERE c.documento.id = :docId")
    int contarChunks(@Param("docId") Long docId);

    /** Obtiene un chunk específico por documento y número. */
    @Query("SELECT c FROM ArchivoChunk c WHERE c.documento.id = :docId AND c.numeroChunk = :numChunk")
    Optional<ArchivoChunk> findByDocumentoIdYNumero(@Param("docId") Long docId, @Param("numChunk") int numChunk);

    /** Paginación para lectura progresiva. */
    @Query("SELECT c FROM ArchivoChunk c WHERE c.documento.id = :docId ORDER BY c.numeroChunk ASC")
    Page<ArchivoChunk> findByDocumentoIdPaginado(@Param("docId") Long docId, Pageable pageable);
}

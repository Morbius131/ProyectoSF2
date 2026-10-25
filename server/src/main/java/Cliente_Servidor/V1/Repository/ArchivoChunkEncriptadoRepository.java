package Cliente_Servidor.V1.Repository;

import Cliente_Servidor.V1.Modelos.ArchivoChunkEncriptado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchivoChunkEncriptadoRepository extends JpaRepository<ArchivoChunkEncriptado, Long> {

    @Query("SELECT c FROM ArchivoChunkEncriptado c WHERE c.documento.id = :docId ORDER BY c.numeroChunk ASC")
    List<ArchivoChunkEncriptado> findByDocumentoIdOrdenado(@Param("docId") Long docId);

    /** Cuenta chunks sin cargar los datos BLOB. */
    @Query("SELECT COUNT(c) FROM ArchivoChunkEncriptado c WHERE c.documento.id = :docId")
    int contarChunks(@Param("docId") Long docId);

    /** Obtiene un chunk específico por documento y número. */
    @Query("SELECT c FROM ArchivoChunkEncriptado c WHERE c.documento.id = :docId AND c.numeroChunk = :numChunk")
    Optional<ArchivoChunkEncriptado> findByDocumentoIdYNumero(@Param("docId") Long docId, @Param("numChunk") int numChunk);

    /** Paginación para lectura progresiva. */
    @Query("SELECT c FROM ArchivoChunkEncriptado c WHERE c.documento.id = :docId ORDER BY c.numeroChunk ASC")
    Page<ArchivoChunkEncriptado> findByDocumentoIdPaginado(@Param("docId") Long docId, Pageable pageable);
}

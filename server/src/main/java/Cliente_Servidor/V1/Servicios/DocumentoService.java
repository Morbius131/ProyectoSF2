package Cliente_Servidor.V1.Servicios;

import Cliente_Servidor.V1.Chunk.ChunkBuffer;
import Cliente_Servidor.V1.DTO.DocumentoDTO;
import Cliente_Servidor.V1.Enums.TipoPropietario;
import Cliente_Servidor.V1.Modelos.Archivo;
import Cliente_Servidor.V1.Modelos.ArchivoChunk;
import Cliente_Servidor.V1.Modelos.ArchivoChunkEncriptado;
import Cliente_Servidor.V1.Modelos.Documento;
import Cliente_Servidor.V1.Modelos.Mensaje;
import Cliente_Servidor.V1.Modelos.Usuario;
import Cliente_Servidor.V1.Repository.ArchivoChunkRepository;
import Cliente_Servidor.V1.Repository.DocumentoRepository;
import Cliente_Servidor.V1.Util.AlmacenamientoArchivos;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import Cliente_Servidor.V1.Util.CryptoUtil;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import Cliente_Servidor.V1.Repository.ArchivoChunkEncriptadoRepository;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class DocumentoService {

    private static final DateTimeFormatter ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    // Chunk para BD: 4 MB — mejor balance para batching eficiente en MySQL.
    private static final int CHUNK_SIZE  = 4 * 1024 * 1024;
    private static final int BUFFER_SIZE = 512 * 1024;

    // Pool de hilos para inserciones paralelas (original + encriptado)
    private static final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    private final DocumentoRepository             documentoRepository;
    private final ArchivoChunkRepository          chunkOriginalRepo;
    private final ArchivoChunkEncriptadoRepository chunkEncRepo;
    private final TransactionTemplate             txTemplate;
    private final DataSource                      dataSource;

    public DocumentoService(DocumentoRepository documentoRepository,
                            ArchivoChunkRepository chunkOriginalRepo,
                            ArchivoChunkEncriptadoRepository chunkEncRepo,
                            org.springframework.transaction.PlatformTransactionManager txManager,
                            DataSource dataSource) {
        this.documentoRepository = documentoRepository;
        this.chunkOriginalRepo   = chunkOriginalRepo;
        this.chunkEncRepo        = chunkEncRepo;
        this.txTemplate          = new TransactionTemplate(txManager);
        this.dataSource          = dataSource;
    }

    // ── POST directo (sin chunks) ─────────────────────────────────────────────

    @Transactional(timeout = 10)
    public Documento procesarYGuardar(DocumentoDTO dto, Usuario usuario) {
        try {
            // Verificar duplicado por idUnico antes de procesar (solo si viene de replicación)
            String idUnicoDto = dto.getIdUnico();
            boolean esExterno = "EXTERNO".equalsIgnoreCase(dto.getTipoPropietario());
            if (esExterno && idUnicoDto != null && !idUnicoDto.isBlank()) {
                if (documentoRepository.existsByIdUnico(idUnicoDto)) {
                    ConsolaUtil.info("Documento ya existe (idUnico=" + idUnicoDto + "), omitiendo");
                    return null;
                }
            }

            Documento doc = dto.toEntidad(usuario);
            String contenido = dto.getContenido();

            if (doc instanceof Archivo archivo) {
                AlmacenamientoArchivos.guardarArchivo(
                        archivo.getNombre(), archivo.getExtension(), contenido);
            } else if (doc instanceof Mensaje mensaje) {
                mensaje.setContenido(contenido);
                AlmacenamientoArchivos.guardarMensaje(
                        dto.getAsunto() != null ? dto.getAsunto() : "sin_asunto", contenido);

                String hash = CryptoUtil.sha256(contenido);
                doc.setHash(hash);

                // Generar idUnico si no viene (mensaje local)
                if (doc.getIdUnico() == null || doc.getIdUnico().isBlank()) {
                    doc.setIdUnico(generarIdUnico(doc.getNombreVisible()));
                }

                try {
                    documentoRepository.save(doc);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    ConsolaUtil.info("Documento ya existe (constraint), omitiendo");
                    return null;
                }
                return doc;
            }

            // contenido llega en base64 desde el cliente; decodificar para obtener los bytes reales
            byte[] contenidoBytes = Base64.getDecoder().decode(contenido);

            String hash = CryptoUtil.sha256Bytes(contenidoBytes);
            doc.setHash(hash);

            // Generar idUnico si no viene (documento local)
            if (doc.getIdUnico() == null || doc.getIdUnico().isBlank()) {
                doc.setIdUnico(generarIdUnico(doc.getNombreVisible()));
            }

            try {
                documentoRepository.saveAndFlush(doc);  // flush solo para obtener el ID generado
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                ConsolaUtil.info("Documento ya existe (constraint), omitiendo");
                return null;
            }
            Long docId = doc.getId();

            // Guardar bytes originales en chunks
            insertarChunksBatch(contenidoBytes, docId, "archivo_chunks_original");

            // Encriptar y guardar en chunks encriptados
            ByteArrayOutputStream encOut = new ByteArrayOutputStream();
            CryptoUtil.encriptarStream(new ByteArrayInputStream(contenidoBytes), encOut);
            insertarChunksBatch(encOut.toByteArray(), docId, "archivo_chunks_encriptado");

            return doc;

        } catch (Exception e) {
            ConsolaUtil.error("Error al procesar documento: " + e.getMessage());
            throw new RuntimeException("Error procesando documento: " + e.getMessage(), e);
        }
    }

    // ── POST chunked ──────────────────────────────────────────────────────────

    public Documento procesarDesdeChunkBuffer(ChunkBuffer buffer, Usuario usuario) {
        File encTemp = null;
        try {
            buffer.finish();
            File archivoTemp = buffer.getArchivoTemp();

            ConsolaUtil.info("Ensamblado: " + buffer.getChunksRecibidos()
                    + " chunks, " + archivoTemp.length() + " bytes");

            // 1. Hash
            final String hash;
            try (InputStream is = new BufferedInputStream(new FileInputStream(archivoTemp), BUFFER_SIZE)) {
                hash = CryptoUtil.sha256Stream(is);
            }
            ConsolaUtil.info("SHA-256: " + hash.substring(0, 16) + "...");

            // 1.1 Solo verificar idUnico si es replicación externa (evita duplicados en sincronización)
            boolean esExterno = "EXTERNO".equalsIgnoreCase(buffer.getTipoPropietario());
            String idUnicoBuffer = buffer.getIdUnico();
            if (esExterno && idUnicoBuffer != null && documentoRepository.existsByIdUnico(idUnicoBuffer)) {
                ConsolaUtil.info("Documento ya existe (idUnico=" + idUnicoBuffer + "), omitiendo replicación");
                buffer.delete();
                return null; // Indica que ya existía
            }

            // 2. Encriptar en streaming → temp en disco
            encTemp = File.createTempFile("enc_", ".tmp");
            try (InputStream  is = new BufferedInputStream(new FileInputStream(archivoTemp), BUFFER_SIZE);
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(encTemp), BUFFER_SIZE)) {
                CryptoUtil.encriptarStream(is, os);
            }
            ConsolaUtil.info("Encriptado: " + encTemp.length() + " bytes");

            // 3. Guardar en disco
            String rutaOriginal = AlmacenamientoArchivos.moverDesdeTemp(
                    archivoTemp, buffer.getNombre(), buffer.getExtension());
            String rutaEnc = AlmacenamientoArchivos.guardarEncriptado(
                    encTemp, buffer.getNombre(), buffer.getExtension());
            encTemp = null;
            ConsolaUtil.ok("Original:   " + rutaOriginal);
            ConsolaUtil.ok("Encriptado: " + rutaEnc);

            // 4. Metadatos en BD
            final Archivo archivoParcial = new Archivo();
            archivoParcial.setNombre(buffer.getNombre());
            archivoParcial.setExtension(buffer.getExtension());
            archivoParcial.setTamanio(new File(rutaOriginal).length());
            archivoParcial.setHash(hash);
            archivoParcial.setTipoPropietario(TipoPropietario.valueOf(buffer.getTipoPropietario()));
            archivoParcial.setUsuario(usuario);
            archivoParcial.setFechaHora(LocalDateTime.now());
            archivoParcial.setDestinatario(buffer.getDestinatario());

            // Usar idUnico del buffer si viene (replicación) o generar uno nuevo (local)
            String idUnico = buffer.getIdUnico();
            if (idUnico == null || idUnico.isBlank()) {
                idUnico = generarIdUnico(buffer.getNombre());
            }
            archivoParcial.setIdUnico(idUnico);

            final Long[] idRef = new Long[1];
            final String idUnicoFinal = idUnico;
            try {
                txTemplate.execute(status -> {
                    documentoRepository.save(archivoParcial);
                    documentoRepository.flush();
                    idRef[0] = archivoParcial.getId();
                    return null;
                });
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Race condition: otro hilo insertó el mismo documento
                ConsolaUtil.info("Documento ya existe (idUnico=" + idUnicoFinal + "), detectado por constraint");
                buffer.delete();
                return null; // Indica que ya existía
            }
            ConsolaUtil.info("Metadatos guardados id=" + idRef[0]
                    + " destinatario=" + (archivoParcial.getDestinatario() != null
                            ? archivoParcial.getDestinatario() : "(null)"));

            // 5. Batch insert chunks original + encriptado EN PARALELO
            final File archivoOriginal = new File(rutaOriginal);
            final File archivoEnc = new File(rutaEnc);
            final long docId = idRef[0];

            CompletableFuture<Void> futureOriginal = CompletableFuture.runAsync(() -> {
                try {
                    insertarChunksBatchParalelo(archivoOriginal, docId, "archivo_chunks_original");
                } catch (Exception e) {
                    throw new RuntimeException("Error insertando chunks originales: " + e.getMessage(), e);
                }
            }, dbExecutor);

            CompletableFuture<Void> futureEnc = CompletableFuture.runAsync(() -> {
                try {
                    insertarChunksBatchParalelo(archivoEnc, docId, "archivo_chunks_encriptado");
                } catch (Exception e) {
                    throw new RuntimeException("Error insertando chunks encriptados: " + e.getMessage(), e);
                }
            }, dbExecutor);

            // Esperar ambos
            CompletableFuture.allOf(futureOriginal, futureEnc).join();

            ConsolaUtil.documento("GUARDADO COMPLETO", "ARCHIVO",
                    archivoParcial.getNombreVisible(), usuario.getNombre());

            return archivoParcial;

        } catch (Exception e) {
            ConsolaUtil.error("Error: " + e.getMessage());
            throw new RuntimeException("Error procesando archivo por chunks: " + e.getMessage(), e);
        } finally {
            try { if (encTemp != null && encTemp.exists()) encTemp.delete(); }
            catch (Exception ignore) {}
        }
    }

    // ── Batch insert desde File ───────────────────────────────────────────────

    // Flush every 150 rows
    private static final int BATCH_FLUSH_SIZE = 150;
    // Sin sub-división interna para evitar OOM (solo paralelo entre tablas)
    private static final int PARALLEL_INSERTERS = 2;

    private void insertarChunksBatch(File archivo, long documentoId, String tabla) throws Exception {
        String sql = "INSERT INTO " + tabla + " (documento_id, numero_chunk, datos) VALUES (?, ?, ?)";
        try (Connection        con = dataSource.getConnection();
             PreparedStatement ps  = con.prepareStatement(sql);
             InputStream        is = new BufferedInputStream(new FileInputStream(archivo), BUFFER_SIZE)) {
            con.setAutoCommit(false);
            byte[] buf = new byte[CHUNK_SIZE];
            int chunkNum = 0, bytesLeidos;
            while ((bytesLeidos = leerFragmento(is, buf)) > 0) {
                byte[] frag = Arrays.copyOf(buf, bytesLeidos);
                ps.setLong(1, documentoId);
                ps.setInt(2, chunkNum);
                ps.setBytes(3, frag);
                ps.addBatch();
                chunkNum++;
                if (chunkNum % BATCH_FLUSH_SIZE == 0) {
                    ps.executeBatch();
                    ps.clearBatch();
                }
            }
            ps.executeBatch(); // flush remaining
            con.commit();
            ConsolaUtil.ok("Batch insert " + tabla + ": " + chunkNum + " chunks");
        }
    }

    /**
     * Versión paralela: divide el archivo en segmentos y usa múltiples conexiones.
     */
    private void insertarChunksBatchParalelo(File archivo, long documentoId, String tabla) throws Exception {
        long fileSize = archivo.length();
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);

        if (totalChunks <= PARALLEL_INSERTERS * 10) {
            // Archivo pequeño: usar método normal
            insertarChunksBatch(archivo, documentoId, tabla);
            return;
        }

        int chunksPerThread = totalChunks / PARALLEL_INSERTERS;
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[PARALLEL_INSERTERS];

        for (int t = 0; t < PARALLEL_INSERTERS; t++) {
            final int threadId = t;
            final int startChunk = t * chunksPerThread;
            final int endChunk = (t == PARALLEL_INSERTERS - 1) ? totalChunks : (t + 1) * chunksPerThread;

            futures[t] = CompletableFuture.runAsync(() -> {
                try {
                    insertarSegmento(archivo, documentoId, tabla, startChunk, endChunk);
                } catch (Exception e) {
                    throw new RuntimeException("Error en segmento " + threadId + ": " + e.getMessage(), e);
                }
            }, dbExecutor);
        }

        CompletableFuture.allOf(futures).join();
        ConsolaUtil.ok("Batch insert paralelo " + tabla + ": " + totalChunks + " chunks (" + PARALLEL_INSERTERS + " hilos)");
    }

    private void insertarSegmento(File archivo, long documentoId, String tabla, int startChunk, int endChunk) throws Exception {
        String sql = "INSERT INTO " + tabla + " (documento_id, numero_chunk, datos) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             RandomAccessFile raf = new RandomAccessFile(archivo, "r")) {

            con.setAutoCommit(false);
            byte[] buf = new byte[CHUNK_SIZE];

            for (int chunkNum = startChunk; chunkNum < endChunk; chunkNum++) {
                long offset = (long) chunkNum * CHUNK_SIZE;
                raf.seek(offset);
                int bytesLeidos = raf.read(buf);
                if (bytesLeidos <= 0) break;

                byte[] frag = (bytesLeidos == CHUNK_SIZE) ? buf.clone() : Arrays.copyOf(buf, bytesLeidos);
                ps.setLong(1, documentoId);
                ps.setInt(2, chunkNum);
                ps.setBytes(3, frag);
                ps.addBatch();

                if ((chunkNum - startChunk + 1) % BATCH_FLUSH_SIZE == 0) {
                    ps.executeBatch();
                    ps.clearBatch();
                }
            }
            ps.executeBatch();
            con.commit();
        }
    }

    // ── Batch insert desde byte[] ─────────────────────────────────────────────

    private void insertarChunksBatch(byte[] datos, long documentoId, String tabla) throws Exception {
        String sql = "INSERT INTO " + tabla + " (documento_id, numero_chunk, datos) VALUES (?, ?, ?)";
        // Use the connection already bound to the current Spring transaction so the FK
        // check on documento_id can see the not-yet-committed documento row.
        Connection con = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int chunkNum = 0;
            for (int offset = 0; offset < datos.length; offset += CHUNK_SIZE) {
                int len = Math.min(CHUNK_SIZE, datos.length - offset);
                byte[] frag = Arrays.copyOfRange(datos, offset, offset + len);
                ps.setLong(1, documentoId);
                ps.setInt(2, chunkNum);
                ps.setBytes(3, frag);
                ps.addBatch();
                chunkNum++;
            }
            ps.executeBatch();
            ConsolaUtil.ok("Batch insert " + tabla + ": " + chunkNum + " chunks");
        }
        // Do NOT close con — the outer @Transactional owns its lifecycle.
    }

    private int leerFragmento(InputStream is, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = is.read(buf, total, buf.length - total);
            if (n == -1) break;
            total += n;
        }
        return total;
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    @Transactional
    public Documento buscarPorId(Long id) {
        return documentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + id));
    }

    @Transactional
    public List<DocumentoDTO> listarComoDTO() {
        return documentoRepository.findAll().stream()
                .map(DocumentoDTO::desde)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<DocumentoDTO> listarArchivosComoDTO(String usuarioNombre) {
        ConsolaUtil.info("LIST archivos para usuario: " + (usuarioNombre != null ? usuarioNombre : "(null)"));
        List<Documento> todos = documentoRepository.findAll();
        List<DocumentoDTO> resultado = todos.stream()
                .filter(doc -> doc.getTipo() == Cliente_Servidor.V1.Enums.TipoDocumento.ARCHIVO)
                .filter(doc -> {
                    boolean visible = esVisiblePara(doc, usuarioNombre);
                    if (!visible) {
                        ConsolaUtil.warn("  Filtrado: id=" + doc.getId() + " dest=" + doc.getDestinatario()
                                + " remitente=" + (doc.getUsuario() != null ? doc.getUsuario().getNombre() : "null"));
                    }
                    return visible;
                })
                .map(DocumentoDTO::desde)
                .collect(Collectors.toList());
        ConsolaUtil.info("  Total archivos: " + todos.size() + " → visibles: " + resultado.size());
        return resultado;
    }

    @Transactional
    public List<DocumentoDTO> listarMensajesComoDTO(String usuarioNombre) {
        return documentoRepository.findAll().stream()
                .filter(doc -> doc.getTipo() == Cliente_Servidor.V1.Enums.TipoDocumento.MENSAJE)
                .filter(doc -> esVisiblePara(doc, usuarioNombre))
                .map(DocumentoDTO::desde)
                .collect(Collectors.toList());
    }

    /**
     * Un documento es visible para un usuario si:
     *  - No hay filtro (usuarioNombre null/blank) → sin restricción
     *  - El destinatario es null, vacío o "TODOS" → público
     *  - El destinatario coincide con el usuario solicitante
     *  - El usuario es el remitente (puede ver lo que envió)
     */
    private boolean esVisiblePara(Documento doc, String usuarioNombre) {
        if (usuarioNombre == null || usuarioNombre.isBlank()) return true;
        String dest = doc.getDestinatario();
        if (dest == null || dest.isBlank() || "TODOS".equalsIgnoreCase(dest)) return true;
        if (dest.equalsIgnoreCase(usuarioNombre)) return true;
        return doc.getUsuario() != null
                && doc.getUsuario().getNombre().equalsIgnoreCase(usuarioNombre);
    }

    @Transactional
    public DocumentoDTO buscarMensajeComoDTO(Long id) {
        Documento doc = buscarPorId(id);
        if (!(doc instanceof Mensaje)) {
            throw new RuntimeException("El documento con ID " + id + " no es un mensaje");
        }
        return DocumentoDTO.desde(doc);
    }

    /** Número total de documentos almacenados (sin cargar contenido). */
    @Transactional
    public long contarDocumentos() {
        return documentoRepository.count();
    }

    /** Lista todos los documentos con metadatos para sincronización (sin contenido). */
    @Transactional
    public List<DocumentoDTO> listarParaSincronizacion() {
        return documentoRepository.findAll().stream()
                .map(DocumentoDTO::desde)
                .collect(Collectors.toList());
    }

    /** Verifica si existe un documento con el hash dado. */
    @Transactional(readOnly = true)
    public boolean existePorHash(String hash) {
        if (hash == null || hash.isBlank()) return false;
        return documentoRepository.existsByHash(hash);
    }

    /** Verifica si existe un documento con el idUnico dado. */
    @Transactional(readOnly = true)
    public boolean existePorIdUnico(String idUnico) {
        if (idUnico == null || idUnico.isBlank()) return false;
        return documentoRepository.existsByIdUnico(idUnico);
    }

    /** Busca un documento por idUnico. */
    @Transactional(readOnly = true)
    public java.util.Optional<Documento> buscarPorIdUnico(String idUnico) {
        if (idUnico == null || idUnico.isBlank()) return java.util.Optional.empty();
        return documentoRepository.findByIdUnico(idUnico);
    }

    /** Genera un ID único para el documento: timestamp + nombre + UUID corto. */
    public String generarIdUnico(String nombre) {
        String ts = LocalDateTime.now().format(ID_FMT);
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String nombreLimpio = nombre != null ? nombre.replaceAll("[^a-zA-Z0-9]", "") : "doc";
        if (nombreLimpio.length() > 20) nombreLimpio = nombreLimpio.substring(0, 20);
        return ts + "_" + nombreLimpio + "_" + uuid;
    }

    /** Devuelve los chunks originales ordenados para el doc dado. */
    @Transactional
    public List<ArchivoChunk> obtenerChunksOriginal(Long documentoId) {
        return chunkOriginalRepo.findByDocumentoIdOrdenado(documentoId);
    }

    /** Devuelve los chunks encriptados ordenados para el doc dado. */
    @Transactional
    public List<ArchivoChunkEncriptado> obtenerChunksEncriptado(Long documentoId) {
        return chunkEncRepo.findByDocumentoIdOrdenado(documentoId);
    }

    // ── Acceso progresivo a chunks (evita OOM en archivos grandes) ────────────

    /** Cuenta chunks originales sin cargar los BLOBs. */
    @Transactional(readOnly = true)
    public int contarChunksOriginal(Long documentoId) {
        return chunkOriginalRepo.contarChunks(documentoId);
    }

    /** Cuenta chunks encriptados sin cargar los BLOBs. */
    @Transactional(readOnly = true)
    public int contarChunksEncriptado(Long documentoId) {
        return chunkEncRepo.contarChunks(documentoId);
    }

    /** Obtiene UN chunk original por número (carga solo ese BLOB). */
    @Transactional(readOnly = true)
    public ArchivoChunk obtenerChunkOriginal(Long documentoId, int numeroChunk) {
        return chunkOriginalRepo.findByDocumentoIdYNumero(documentoId, numeroChunk)
                .orElse(null);
    }

    /** Obtiene UN chunk encriptado por número (carga solo ese BLOB). */
    @Transactional(readOnly = true)
    public ArchivoChunkEncriptado obtenerChunkEncriptado(Long documentoId, int numeroChunk) {
        return chunkEncRepo.findByDocumentoIdYNumero(documentoId, numeroChunk)
                .orElse(null);
    }
}

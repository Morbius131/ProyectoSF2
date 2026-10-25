package Cliente_Servidor.V1.Modelos;

import jakarta.persistence.*;

@Entity
@Table(name = "archivo_chunks_original", indexes = {
    @Index(name = "idx_chunk_orig_doc_num", columnList = "documento_id, numero_chunk")
})
public class ArchivoChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @Column(name = "numero_chunk", nullable = false)
    private int numeroChunk;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "datos", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] datos;

    public ArchivoChunk() {}

    public ArchivoChunk(Documento documento, int numeroChunk, byte[] datos) {
        this.documento   = documento;
        this.numeroChunk = numeroChunk;
        this.datos       = datos;
    }

    public Long getId()                    { return id; }
    public Documento getDocumento()        { return documento; }
    public void setDocumento(Documento d)  { this.documento = d; }
    public int getNumeroChunk()            { return numeroChunk; }
    public void setNumeroChunk(int n)      { this.numeroChunk = n; }
    public byte[] getDatos()               { return datos; }
    public void setDatos(byte[] datos)     { this.datos = datos; }
}

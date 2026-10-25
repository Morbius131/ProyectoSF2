export interface ApiResponse<T> {
  status: string;
  data?: T;
  message?: string;
  totalLogs?: number;
}

export interface UsuarioConectado {
  usuario: string;
  ip: string;
  protocolo: string;
  horaInicio: string;
  servidor: string;
}

export interface DocumentoItem {
  id: number;
  nombreVisible: string;
  tipo: string;
  tipoPropietario: string;
  destinatario: string;
  servidor?: string;
  asunto?: string;
}

export interface LogItem {
  id: number;
  tipo: string;
  mensaje: string;
  fechaHora: string;
  usuario: string;
}

export interface ImagenRostroItem {
  id: number;
  nombre: string;
  extension: string;
  tamanio: number;
  documentoId: number;
  fechaHora: string;
  emocion?: string;
  datosBase64?: string;
}

export interface ImagenRostroDetalle extends ImagenRostroItem {
  datosBase64?: string;
}

export interface ImagenesRostroData {
  total: number;
  imagenes: ImagenRostroItem[];
}

export interface EmotionResult {
  emocion: string;
  confianza: number;
  todas: Record<string, number>;
}

export interface ServerInstance {
  urlGateway: string;
  endpoints: string;
}

export interface ResumenEmocionesData {
  totalImagenes: number;
  porEmocion: Record<string, number>;
  emocionMasFrecuente: string;
  ultimasImagenes: {
    id: number;
    nombre: string;
    emocion: string;
    fechaHora: string;
  }[];
}

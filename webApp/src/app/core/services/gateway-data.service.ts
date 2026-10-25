import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  DocumentoItem,
  EmotionResult,
  ImagenRostroDetalle,
  ImagenesRostroData,
  LogItem,
  ResumenEmocionesData,
  ServerInstance,
  UsuarioConectado,
} from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class GatewayDataService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl.replace(/\/+$/, '');

  getServidores(): Observable<ServerInstance[]> {
    const url = `${this.apiBaseUrl}/gateway/servers`;
    console.log(`[GET] ${url}`);
    return this.http.get<ServerInstance[]>(url);
  }

  getUsuariosConectados(serverPath: string): Observable<ApiResponse<UsuarioConectado[]>> {
    return this.get<UsuarioConectado[]>(serverPath, '/api/usuarios/conectados');
  }

  getDocumentos(serverPath: string): Observable<ApiResponse<DocumentoItem[]>> {
    return this.get<DocumentoItem[]>(serverPath, '/api/documentos');
  }

  getMensajes(serverPath: string): Observable<ApiResponse<DocumentoItem[]>> {
    return this.get<DocumentoItem[]>(serverPath, '/api/documentos/mensajes');
  }

  getLogs(serverPath: string): Observable<ApiResponse<LogItem[]>> {
    return this.get<LogItem[]>(serverPath, '/api/logs');
  }

  getImagenesRostro(serverPath: string): Observable<ApiResponse<ImagenesRostroData>> {
    return this.get<ImagenesRostroData>(serverPath, '/api/emociones/imagenes');
  }

  getImagenRostro(serverPath: string, id: number): Observable<ApiResponse<ImagenRostroDetalle>> {
    return this.get<ImagenRostroDetalle>(serverPath, `/api/emociones/imagenes/${id}`);
  }

  getResumenEmociones(serverPath: string): Observable<ApiResponse<ResumenEmocionesData>> {
    return this.get<ResumenEmocionesData>(serverPath, '/api/emociones/resumen');
  }

  identificarEmocion(serverPath: string, id: number): Observable<ApiResponse<EmotionResult>> {
    const url = `${this.apiBaseUrl}${serverPath}/api/emociones/identificar/${id}`;
    console.log(`[POST] ${url}`);
    return this.http.post<ApiResponse<EmotionResult>>(url, {});
  }

  private get<T>(serverPath: string, path: string): Observable<ApiResponse<T>> {
    const url = `${this.apiBaseUrl}${serverPath}${path}`;
    console.log(`[GET] ${url}`);
    return this.http.get<ApiResponse<T>>(url);
  }
}

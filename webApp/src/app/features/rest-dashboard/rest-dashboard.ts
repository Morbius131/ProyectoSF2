import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Observable, finalize, map } from 'rxjs';

import {
  ApiResponse,
  DocumentoItem,
  ImagenRostroItem,
  LogItem,
  ResumenEmocionesData,
  ServerInstance,
  UsuarioConectado,
} from '../../core/models/api.models';
import { GatewayDataService } from '../../core/services/gateway-data.service';
import {
  CellValue,
  DASHBOARD_VIEWS,
  DataViewKey,
  LoadedRows,
  TableRow,
  isDataViewKey,
} from './models/rest-dashboard.models';

@Component({
  selector: 'app-rest-dashboard',
  templateUrl: './rest-dashboard.html',
  styleUrl: './rest-dashboard.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestDashboardComponent implements OnInit {
  private readonly gatewayData = inject(GatewayDataService);

  readonly views = DASHBOARD_VIEWS;
  readonly servers = signal<ServerInstance[]>([]);
  readonly selectedServer = signal<ServerInstance | null>(null);
  readonly selectedViewKey = signal<DataViewKey>('usuarios');
  readonly rows = signal<TableRow[]>([]);
  readonly status = signal('Sin consultar');
  readonly total = signal<number | null>(null);
  readonly loading = signal(false);
  readonly loadingServers = signal(false);
  readonly error = signal('');
  readonly resumenExtra = signal<{ totalImagenes: number; emocionMasFrecuente: string; ultimasImagenes: { id: number; nombre: string; emocion: string; fechaHora: string }[] } | null>(null);

  readonly pageSize = 20;
  readonly currentPage = signal(1);

  readonly selectedView = computed(
    () => this.views.find((view) => view.key === this.selectedViewKey()) ?? this.views[0],
  );
  readonly totalRecords = computed(() => this.total() ?? this.rows().length);
  readonly serverPath = computed(() => this.selectedServer()?.urlGateway ?? '');
  readonly totalPages = computed(() => Math.ceil(this.rows().length / this.pageSize) || 1);
  readonly paginatedRows = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.rows().slice(start, start + this.pageSize);
  });

  ngOnInit(): void {
    this.loadServers();
  }

  loadServers(): void {
    this.loadingServers.set(true);
    this.gatewayData
      .getServidores()
      .pipe(finalize(() => this.loadingServers.set(false)))
      .subscribe({
        next: (servers) => {
          this.servers.set(servers);
          if (servers.length > 0) {
            this.selectedServer.set(servers[0]);
            this.loadSelectedView();
          }
        },
        error: (error: unknown) => {
          this.error.set(this.formatHttpError(error));
        },
      });
  }

  onServerChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const server = this.servers().find((s) => s.urlGateway === select.value);
    if (server) {
      this.selectedServer.set(server);
      this.loadSelectedView();
    }
  }

  serverLabel(server: ServerInstance): string {
    const parts = server.urlGateway.split(':');
    return parts.length >= 2 ? parts[1] : server.urlGateway;
  }

  onViewChange(event: Event): void {
    const select = event.target as HTMLSelectElement;

    if (!isDataViewKey(select.value)) {
      return;
    }

    this.selectedViewKey.set(select.value);
    this.currentPage.set(1);
    this.resumenExtra.set(null);
    this.loadSelectedView();
  }

  nextPage(): void {
    if (this.currentPage() < this.totalPages()) {
      this.currentPage.set(this.currentPage() + 1);
    }
  }

  prevPage(): void {
    if (this.currentPage() > 1) {
      this.currentPage.set(this.currentPage() - 1);
    }
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages()) {
      this.currentPage.set(page);
    }
  }

  loadSelectedView(): void {
    if (!this.selectedServer()) {
      return;
    }

    this.error.set('');
    this.loading.set(true);
    this.currentPage.set(1);

    this.fetchSelectedView()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (result) => {
          this.status.set(result.status);
          this.rows.set(result.rows);
          this.total.set(result.total ?? result.rows.length);

          if (!this.isOkStatus(result.status)) {
            this.error.set(`El servidor respondio con estado ${result.status}.`);
          }
        },
        error: (error: unknown) => {
          this.status.set('ERROR');
          this.rows.set([]);
          this.total.set(0);
          this.error.set(this.formatHttpError(error));
        },
      });
  }

  rowId(row: TableRow): number | null {
    const value = row['id'];

    if (typeof value === 'number') {
      return value;
    }

    if (typeof value === 'string') {
      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : null;
    }

    return null;
  }

  formatCell(value: CellValue): string {
    if (value === null || value === undefined || value === '') {
      return 'Sin dato';
    }

    return String(value);
  }

  private fetchSelectedView(): Observable<LoadedRows> {
    const path = this.serverPath();
    switch (this.selectedViewKey()) {
      case 'usuarios':
        return this.gatewayData
          .getUsuariosConectados(path)
          .pipe(map((response) => this.toLoadedRows(response, this.userToRow)));
      case 'documentos':
        return this.gatewayData
          .getDocumentos(path)
          .pipe(map((response) => this.toLoadedRows(response, this.documentToRow)));
      case 'mensajes':
        return this.gatewayData
          .getMensajes(path)
          .pipe(map((response) => this.toLoadedRows(response, this.messageToRow)));
      case 'logs':
        return this.gatewayData
          .getLogs(path)
          .pipe(map((response) => this.toLoadedRows(response, this.logToRow)));
      case 'imagenes':
        return this.gatewayData
          .getImagenesRostro(path)
          .pipe(map((response) => this.toLoadedRowsImagenes(response)));
      case 'resumenEmociones':
        return this.gatewayData
          .getResumenEmociones(path)
          .pipe(map((response) => this.toLoadedRowsResumen(response)));
    }
  }

  private toLoadedRows<T>(response: ApiResponse<T[]>, mapper: (item: T) => TableRow): LoadedRows {
    const data = response.data ?? [];

    return {
      status: response.status,
      rows: data.map(mapper),
      total: response.totalLogs,
    };
  }

  private toLoadedRowsImagenes(response: ApiResponse<{ total: number; imagenes: ImagenRostroItem[] }>): LoadedRows {
    const imagenes = response.data?.imagenes ?? [];

    return {
      status: response.status,
      rows: imagenes.map(this.imageToRow),
      total: response.data?.total,
    };
  }

  private toLoadedRowsResumen(response: ApiResponse<ResumenEmocionesData>): LoadedRows {
    const data = response.data;
    if (!data) {
      return { status: response.status, rows: [], total: 0 };
    }

    this.resumenExtra.set({
      totalImagenes: data.totalImagenes,
      emocionMasFrecuente: data.emocionMasFrecuente,
      ultimasImagenes: data.ultimasImagenes,
    });

    const rows: TableRow[] = Object.entries(data.porEmocion).map(([emocion, cantidad]) => ({
      emocion,
      cantidad,
      porcentaje: data.totalImagenes > 0 ? ((cantidad / data.totalImagenes) * 100).toFixed(1) + '%' : '0%',
    }));

    return {
      status: response.status,
      rows,
      total: rows.length,
    };
  }

  private userToRow(user: UsuarioConectado): TableRow {
    return {
      usuario: user.usuario,
      ip: user.ip,
      protocolo: user.protocolo,
      horaInicio: user.horaInicio,
      servidor: user.servidor,
    };
  }

  private documentToRow(documento: DocumentoItem): TableRow {
    return {
      id: documento.id,
      nombreVisible: documento.nombreVisible,
      tipo: documento.tipo,
      tipoPropietario: documento.tipoPropietario,
      destinatario: documento.destinatario,
      servidor: documento.servidor,
    };
  }

  private messageToRow(mensaje: DocumentoItem): TableRow {
    return {
      id: mensaje.id,
      nombreVisible: mensaje.nombreVisible,
      tipo: mensaje.tipo,
      asunto: mensaje.asunto,
      destinatario: mensaje.destinatario,
      tipoPropietario: mensaje.tipoPropietario,
    };
  }

  private logToRow(log: LogItem): TableRow {
    return {
      id: log.id,
      tipo: log.tipo,
      mensaje: log.mensaje,
      fechaHora: log.fechaHora,
      usuario: log.usuario,
    };
  }

  private imageToRow = (image: ImagenRostroItem): TableRow => {
    const mimeType = this.mimeTypeFor(image.extension);
    const imageSrc = image.datosBase64 ? `data:${mimeType};base64,${image.datosBase64}` : null;

    return {
      id: image.id,
      nombre: image.nombre,
      emocion: image.emocion ?? 'Sin identificar',
      fechaHora: image.fechaHora,
      imagen: imageSrc,
    };
  };

  private isOkStatus(status: string): boolean {
    return status.toUpperCase() === 'OK';
  }

  private mimeTypeFor(extension: string): string {
    const normalizedExtension = extension.toLowerCase();

    if (normalizedExtension === 'jpg' || normalizedExtension === 'jpeg') {
      return 'image/jpeg';
    }

    if (normalizedExtension === 'png') {
      return 'image/png';
    }

    if (normalizedExtension === 'webp') {
      return 'image/webp';
    }

    return 'application/octet-stream';
  }

  private formatHttpError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const serverMessage = this.extractServerMessage(error.error);

      if (serverMessage) {
        return serverMessage;
      }

      return `No se pudo conectar con el Gateway HTTP (${error.status || 'sin respuesta'}).`;
    }

    return 'No se pudo completar la peticion al Gateway HTTP.';
  }

  private extractServerMessage(payload: unknown): string | null {
    if (typeof payload !== 'object' || payload === null || !('message' in payload)) {
      return null;
    }

    const message = (payload as { message?: unknown }).message;
    return typeof message === 'string' ? message : null;
  }
}

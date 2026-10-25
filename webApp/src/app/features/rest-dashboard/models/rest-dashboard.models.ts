export type DataViewKey = 'usuarios' | 'documentos' | 'mensajes' | 'logs' | 'imagenes' | 'resumenEmociones';
export type CellValue = string | number | null | undefined;
export type TableRow = Record<string, CellValue>;

export interface TableColumn {
  key: string;
  label: string;
}

export interface DashboardView {
  key: DataViewKey;
  label: string;
  columns: readonly TableColumn[];
}

export interface LoadedRows {
  status: string;
  rows: TableRow[];
  total?: number;
}

export const DASHBOARD_VIEWS: readonly DashboardView[] = [
  {
    key: 'usuarios',
    label: 'Usuarios conectados',
    columns: [
      { key: 'usuario', label: 'Usuario' },
      { key: 'ip', label: 'IP' },
      { key: 'protocolo', label: 'Protocolo' },
      { key: 'horaInicio', label: 'Hora inicio' },
      { key: 'servidor', label: 'Servidor' },
    ],
  },
  {
    key: 'documentos',
    label: 'Documentos',
    columns: [
      { key: 'id', label: 'ID' },
      { key: 'nombreVisible', label: 'Nombre' },
      { key: 'tipo', label: 'Tipo' },
      { key: 'tipoPropietario', label: 'Propietario' },
      { key: 'destinatario', label: 'Destinatario' },
      { key: 'servidor', label: 'Servidor' },
    ],
  },
  {
    key: 'mensajes',
    label: 'Mensajes',
    columns: [
      { key: 'id', label: 'ID' },
      { key: 'nombreVisible', label: 'Nombre' },
      { key: 'tipo', label: 'Tipo' },
      { key: 'asunto', label: 'Asunto' },
      { key: 'destinatario', label: 'Destinatario' },
      { key: 'tipoPropietario', label: 'Propietario' },
    ],
  },
  {
    key: 'logs',
    label: 'Logs del sistema',
    columns: [
      { key: 'id', label: 'ID' },
      { key: 'tipo', label: 'Tipo' },
      { key: 'mensaje', label: 'Mensaje' },
      { key: 'fechaHora', label: 'Fecha hora' },
      { key: 'usuario', label: 'Usuario' },
    ],
  },
  {
    key: 'imagenes',
    label: 'Imagenes de rostro',
    columns: [
      { key: 'imagen', label: 'Imagen' },
      { key: 'id', label: 'ID' },
      { key: 'nombre', label: 'Nombre' },
      { key: 'emocion', label: 'Emocion' },
      { key: 'fechaHora', label: 'Fecha hora' },
    ],
  },
  {
    key: 'resumenEmociones',
    label: 'Resumen de emociones',
    columns: [
      { key: 'emocion', label: 'Emocion' },
      { key: 'cantidad', label: 'Cantidad' },
      { key: 'porcentaje', label: 'Porcentaje' },
    ],
  },
];

export function isDataViewKey(value: string): value is DataViewKey {
  return DASHBOARD_VIEWS.some((view) => view.key === value);
}

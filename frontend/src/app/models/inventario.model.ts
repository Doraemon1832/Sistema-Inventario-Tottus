export interface User {
  idUsuario: number;
  usuario: string;
  rol: string;
  idSede?: number;
  nombreSede?: string;
  idSedesAutorizadas?: number[];
  accesoGlobal?: boolean;
  cambioPasswordRequerido?: boolean;
  token?: string;
}

export interface UsuarioAdmin {
  idUsuario: number;
  usuario: string;
  rol: string;
  idSedePrincipal: number | null;
  sedePrincipal: string | null;
  idSedesAutorizadas: number[];
  accesoGlobal: boolean;
  estado: boolean;
  cambioPasswordRequerido: boolean;
}

export interface Sede {
  idSede: number;
  nombre: string;
  direccion: string;
  estado: boolean;
}

export interface Categoria {
  idCategoria: number;
  nombre: string;
  estado: boolean;
}

export interface Producto {
  idProducto: number;
  codigo: string;
  nombre: string;
  unidadMedida?: string | null;
  categoria?: Categoria;
  precio: number;
  stockMinimo: number;
  estado?: boolean;
}

export interface InventarioItem {
  idInventario: number;
  idSede: number;
  nombreSede: string;
  idProducto: number;
  codigoProducto: string;
  nombreProducto: string;
  unidadMedida?: string | null;
  categoria: string;
  idCategoria: number;
  precio: number;
  stockActual: number;
  stockMinimo: number;
  ubicacion: string;
  estadoStock: 'OPTIMO' | 'BAJO_STOCK' | 'AGOTADO';
  estado: boolean;
}

export interface ResumenInventario {
  totalProductos: number;
  stockBajo: number;
  sinStock: number;
  totalUnidades: number;
  movimientosHoy: number;
}

export interface MovimientoRequest {
  idSede: number;
  idProducto: number;
  tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE';
  cantidad: number;
  referencia: string;
}

export interface MovimientoResponse {
  idMovimiento: number;
  idSede: number;
  nombreSede: string;
  idProducto: number;
  codigoProducto: string;
  nombreProducto: string;
  tipo: string;
  subtipo?: string | null;
  cantidad: number;
  fecha: string;
  referencia: string;
  usuario: string;
  stockAnterior?: number | null;
  stockPosterior?: number | null;
  idTraslado?: number | null;
  codigoTraslado?: string | null;
  unidadMedida?: string | null;
}

export interface TrasladoDetalle {
  idProducto: number;
  codigoProducto: string;
  nombreProducto: string;
  unidadMedida?: string | null;
  cantidadSolicitada: number;
  cantidadDespachada: number;
  cantidadRecibida: number;
  cantidadEnTransito: number;
}

export interface Traslado {
  idTraslado: number;
  codigo: string;
  idSedeOrigen: number;
  sedeOrigen: string;
  idSedeDestino: number;
  sedeDestino: string;
  estado: 'PENDIENTE' | 'EN_TRANSITO' | 'RECIBIDO_PARCIAL' | 'RECIBIDO' | 'CANCELADO';
  referencia: string;
  observaciones?: string | null;
  responsable: string;
  responsableDespacho?: string | null;
  responsableRecepcion?: string | null;
  fechaSolicitud: string;
  fechaDespacho?: string | null;
  fechaFinalizacion?: string | null;
  detalles: TrasladoDetalle[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProductoRequest {
  codigo: string;
  nombre: string;
  unidadMedida?: string;
  idCategoria: number;
  precio: number;
  stockMinimo: number;
  idSede?: number;
  stockInicial?: number;
  ubicacion?: string;
}

export interface OperacionDetalle {
  idProducto: number;
  codigoProducto?: string;
  nombreProducto?: string;
  unidadMedida?: string | null;
  cantidad: number;
}

export interface OperacionInventario {
  idOperacion: number;
  tipo: 'INGRESO' | 'EGRESO';
  subtipo?: string | null;
  estado?: string;
  aprobadoPor?: string | null;
  fechaRevision?: string | null;
  motivoRevision?: string | null;
  idSede: number;
  nombreSede: string;
  usuario: string;
  fecha: string;
  referencia: string;
  idProveedor?: number | null;
  nombreProveedor?: string | null;
  beneficiario?: string | null;
  motivo?: string | null;
  observaciones?: string | null;
  detalles: OperacionDetalle[];
}

export interface ProveedorOption {
  idProveedor: number;
  nombre: string;
  documento: string;
  estado: boolean;
}

export interface OperacionRequest {
  idSede: number;
  subtipo?: string;
  idProveedor?: number;
  beneficiario?: string;
  motivo?: string;
  observaciones?: string;
  referencia: string;
  detalles: OperacionDetalle[];
}

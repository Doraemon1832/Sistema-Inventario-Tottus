export interface User {
  idUsuario: number;
  usuario: string;
  rol: string;
  idSede?: number;
  nombreSede?: string;
  token?: string;
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
  cantidad: number;
  fecha: string;
  referencia: string;
  usuario: string;
}

export interface ProductoRequest {
  codigo: string;
  nombre: string;
  idCategoria: number;
  precio: number;
  stockMinimo: number;
  idSede?: number;
  stockInicial?: number;
  ubicacion?: string;
}

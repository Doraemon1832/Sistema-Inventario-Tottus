import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  InventarioItem,
  ResumenInventario,
  MovimientoRequest,
  MovimientoResponse,
  Sede,
  Categoria,
  Producto,
  ProductoRequest,
  OperacionInventario,
  OperacionRequest,
  PageResponse,
  ProveedorOption,
  Traslado
} from '../models/inventario.model';

@Injectable({
  providedIn: 'root'
})
export class InventarioService {
  private baseUrl = '/api';

  constructor(private http: HttpClient) {}

  getInventario(
    sedeId?: number,
    categoriaId?: number,
    query?: string,
    stockStatus?: string
  ): Observable<InventarioItem[]> {
    let params = new HttpParams();
    if (sedeId) params = params.set('sedeId', sedeId.toString());
    if (categoriaId) params = params.set('categoriaId', categoriaId.toString());
    if (query && query.trim()) params = params.set('query', query.trim());
    if (stockStatus && stockStatus !== 'TODOS') params = params.set('stockStatus', stockStatus);

    return this.http.get<InventarioItem[]>(`${this.baseUrl}/inventario`, { params });
  }

  getResumen(sedeId?: number): Observable<ResumenInventario> {
    let params = new HttpParams();
    if (sedeId) params = params.set('sedeId', sedeId.toString());
    return this.http.get<ResumenInventario>(`${this.baseUrl}/inventario/resumen`, { params });
  }

  registrarMovimiento(req: MovimientoRequest): Observable<MovimientoResponse> {
    return this.http.post<MovimientoResponse>(`${this.baseUrl}/inventario/movimientos`, req);
  }

  getMovimientos(sedeId?: number, tipo?: string, productoId?: number, desde?: string, hasta?: string,
    page = 0, size = 20, subtipo?: string): Observable<PageResponse<MovimientoResponse>> {
    let params = new HttpParams();
    if (sedeId) params = params.set('sedeId', sedeId.toString());
    if (tipo && tipo !== 'TODOS') params = params.set('tipo', tipo);
    if (productoId) params = params.set('productoId', productoId.toString());
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    if (subtipo && subtipo !== 'TODOS') params = params.set('subtipo', subtipo);
    params = params.set('page', page.toString()).set('size', size.toString());

    return this.http.get<PageResponse<MovimientoResponse>>(`${this.baseUrl}/inventario/movimientos`, { params });
  }

  getSedes(): Observable<Sede[]> {
    return this.http.get<Sede[]>(`${this.baseUrl}/sedes`);
  }

  getCategorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(`${this.baseUrl}/productos/categorias`);
  }

  getProductos(): Observable<Producto[]> {
    return this.http.get<Producto[]>(`${this.baseUrl}/productos`);
  }

  getProveedores(activos = true): Observable<ProveedorOption[]> {
    return this.http.get<ProveedorOption[]>(`${this.baseUrl}/proveedores`, { params: { activos: String(activos) } });
  }

  getSedesTraslado(): Observable<Sede[]> {
    return this.http.get<Sede[]>(`${this.baseUrl}/traslados/sedes-destino`);
  }

  getTraslados(estado = 'TODOS'): Observable<Traslado[]> {
    return this.http.get<Traslado[]>(`${this.baseUrl}/traslados`, { params: { estado } });
  }

  crearTraslado(request: { idSedeOrigen: number; idSedeDestino: number; referencia: string; observaciones?: string; detalles: { idProducto: number; cantidad: number }[] }): Observable<Traslado> {
    return this.http.post<Traslado>(`${this.baseUrl}/traslados`, request);
  }

  despacharTraslado(id: number, idempotencyKey: string): Observable<Traslado> {
    return this.http.post<Traslado>(`${this.baseUrl}/traslados/${id}/despacho`, { idempotencyKey });
  }

  recibirTraslado(id: number, idempotencyKey: string, detalles: { idProducto: number; cantidad: number }[]): Observable<Traslado> {
    return this.http.post<Traslado>(`${this.baseUrl}/traslados/${id}/recepcion`, { idempotencyKey, detalles });
  }

  cancelarTraslado(id: number, idempotencyKey: string, referenciaDevolucion?: string): Observable<Traslado> {
    return this.http.post<Traslado>(`${this.baseUrl}/traslados/${id}/cancelacion`, { idempotencyKey, referenciaDevolucion });
  }

  crearProducto(req: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>(`${this.baseUrl}/productos`, req);
  }

  actualizarProducto(id: number, req: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(`${this.baseUrl}/productos/${id}`, req);
  }

  cambiarEstadoProducto(id: number, activo: boolean): Observable<Producto> {
    return this.http.patch<Producto>(`${this.baseUrl}/productos/${id}/estado`, null, {
      params: { activo: activo.toString() }
    });
  }

  getOperaciones(tipo: 'ingresos' | 'egresos', sedeId?: number, query?: string, desde?: string, hasta?: string): Observable<OperacionInventario[]> {
    let params = new HttpParams();
    if (sedeId) params = params.set('sedeId', sedeId.toString());
    if (query?.trim()) params = params.set('query', query.trim());
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    return this.http.get<OperacionInventario[]>(`${this.baseUrl}/operaciones/${tipo}`, { params });
  }

  crearOperacion(tipo: 'ingresos' | 'egresos', request: OperacionRequest): Observable<OperacionInventario> {
    return this.http.post<OperacionInventario>(`${this.baseUrl}/operaciones/${tipo}`, request);
  }

  editarIngreso(id: number, request: OperacionRequest): Observable<OperacionInventario> {
    return this.http.put<OperacionInventario>(`${this.baseUrl}/operaciones/ingresos/${id}`, request);
  }

  aprobarEgreso(id: number, sedeId: number): Observable<OperacionInventario> {
    return this.http.post<OperacionInventario>(`${this.baseUrl}/operaciones/egresos/${id}/aprobar`, null, { params: { sedeId } });
  }

  rechazarEgreso(id: number, sedeId: number, motivo: string): Observable<OperacionInventario> {
    return this.http.post<OperacionInventario>(`${this.baseUrl}/operaciones/egresos/${id}/rechazar`, { motivo }, { params: { sedeId } });
  }
}

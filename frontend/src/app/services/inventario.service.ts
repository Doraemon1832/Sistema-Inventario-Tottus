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
  ProductoRequest
} from '../models/inventario.model';

@Injectable({
  providedIn: 'root'
})
export class InventarioService {
  private baseUrl = 'http://localhost:8080/api';

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

  getMovimientos(sedeId?: number, tipo?: string, productoId?: number): Observable<MovimientoResponse[]> {
    let params = new HttpParams();
    if (sedeId) params = params.set('sedeId', sedeId.toString());
    if (tipo && tipo !== 'TODOS') params = params.set('tipo', tipo);
    if (productoId) params = params.set('productoId', productoId.toString());

    return this.http.get<MovimientoResponse[]>(`${this.baseUrl}/inventario/movimientos`, { params });
  }

  getSedes(): Observable<Sede[]> {
    return this.http.get<Sede[]>(`${this.baseUrl}/sedes`);
  }

  getCategorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(`${this.baseUrl}/productos/categorias`);
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
}

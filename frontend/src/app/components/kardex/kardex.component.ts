import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import { InventarioItem, MovimientoResponse, PageResponse } from '../../models/inventario.model';
import { DataSyncService } from '../../services/data-sync.service';
import { formatDateTimeLima } from '../../utils/date-time-lima';

@Component({
  selector: 'app-kardex',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './kardex.component.html',
  styleUrl: './kardex.component.scss'
})
export class KardexComponent implements OnInit {
  formatDate(value: string | null | undefined): string { return formatDateTimeLima(value); }
  labelSubtype(value: string): string {
    const labels: Record<string, string> = {
      INGRESO_GENERAL: 'Ingreso general',
      COMPRA: 'Compra',
      VENTA: 'Venta',
      MERMA: 'Merma',
      DEVOLUCION_CLIENTE: 'Devolución de cliente', PRODUCCION: 'Producción', BONIFICACION_PROVEEDOR: 'Bonificación de proveedor',
      DEVOLUCION_PROVEEDOR: 'Devolución a proveedor', DESMEDRO: 'Desmedro', DONACION: 'Donación', CONSUMO_INTERNO: 'Consumo interno',
      TRANSFERENCIA_ENVIADA: 'Traslado enviado', TRANSFERENCIA_RECIBIDA: 'Traslado recibido', DEVOLUCION_TRASLADO_ORIGEN: 'Retorno de traslado',
      AJUSTE: 'Ajuste'
    };
    return labels[value] ?? value;
  }
  readonly pageSize = 20;
  readonly siteId: number;
  siteName = signal('Sede asignada');
  products = signal<InventarioItem[]>([]);
  movements = signal<MovimientoResponse[]>([]);
  page = signal(0);
  totalElements = signal(0);
  totalPages = signal(0);
  loading = signal(false);
  error = signal('');
  typeFilter = 'TODOS';
  reasonFilter = 'TODOS';
  productFilter: number | null = null;
  fromDate = '';
  toDate = '';
  private stopSync?: () => void;

  constructor(private api: InventarioService, private auth: AuthService, private sync: DataSyncService) {
    this.siteId = auth.activeSedeId() || auth.currentUser()?.idSede || 0;
  }

  ngOnInit(): void {
    this.stopSync = this.sync.listen(['all', 'inventory', 'operations'], () => this.load());
    this.api.getSedes().subscribe({
      next: sites => this.siteName.set(sites.find(site => site.idSede === this.siteId)?.nombre ?? 'Sede asignada')
    });
    this.api.getInventario(this.siteId).subscribe({
      next: products => this.products.set(products)
    });
    this.load();
  }

  ngOnDestroy(): void { this.stopSync?.(); }

  load(): void {
    if (!this.siteId) {
      this.error.set('No hay una sede asignada para consultar el Kardex.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.api.getMovimientos(
      this.siteId,
      this.typeFilter,
      this.productFilter || undefined,
      this.fromDate,
      this.toDate,
      this.page(),
      this.pageSize,
      this.reasonFilter
    ).subscribe({
      next: (result: PageResponse<MovimientoResponse>) => {
        this.movements.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: err => {
        this.movements.set([]);
        this.error.set(err?.error?.error || 'No se pudo cargar el Kardex. Intenta nuevamente.');
        this.loading.set(false);
      }
    });
  }

  filtersChanged(): void {
    this.page.set(0);
    this.load();
  }

  changePage(delta: number): void {
    const next = this.page() + delta;
    if (next < 0 || next >= this.totalPages()) return;
    this.page.set(next);
    this.load();
  }
}

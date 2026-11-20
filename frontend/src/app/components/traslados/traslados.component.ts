import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import { InventarioItem, Sede, Traslado } from '../../models/inventario.model';
import { formatDateTimeLima } from '../../utils/date-time-lima';

interface LineDraft { idProducto: number | null; cantidad: number; }

@Component({
  selector: 'app-traslados', standalone: true, imports: [CommonModule, FormsModule],
  templateUrl: './traslados.component.html', styleUrl: './traslados.component.scss'
})
export class TrasladosComponent implements OnInit {
  siteId = 0;
  sites = signal<Sede[]>([]);
  products = signal<InventarioItem[]>([]);
  transfers = signal<Traslado[]>([]);
  busy = signal(false);
  loading = signal(false);
  error = signal('');
  success = signal('');
  estado = 'TODOS';
  referencia = '';
  observaciones = '';
  destinoId: number | null = null;
  details: LineDraft[] = [{ idProducto: null, cantidad: 1 }];
  receiving: Record<number, number> = {};
  returnReference: Record<number, string> = {};

  constructor(private api: InventarioService, private auth: AuthService) {
    this.siteId = auth.activeSedeId() || auth.currentUser()?.idSede || 0;
  }

  get authSiteName(): string { return this.auth.currentUser()?.nombreSede || `Sede ${this.siteId}`; }

  ngOnInit(): void {
    this.api.getSedesTraslado().subscribe({ next: sites => this.sites.set(sites.filter(s => s.estado)) });
    this.api.getInventario(this.siteId).subscribe({ next: items => this.products.set(items.filter(p => p.estado)) });
    this.load();
  }

  canCreate(): boolean { return this.auth.hasAnyRole('ADMINISTRADOR', 'JEFE_INVENTARIO', 'OPERADOR_ALMACEN'); }
  canDispatch(t: Traslado): boolean { return t.idSedeOrigen === this.siteId && t.estado === 'PENDIENTE' && this.auth.hasAnyRole('ADMINISTRADOR', 'OPERADOR_ALMACEN'); }
  canReceive(t: Traslado): boolean { return t.idSedeDestino === this.siteId && ['EN_TRANSITO', 'RECIBIDO_PARCIAL'].includes(t.estado); }
  canCancel(t: Traslado): boolean { return t.idSedeOrigen === this.siteId && ['PENDIENTE', 'EN_TRANSITO'].includes(t.estado); }
  date(value: string): string { return formatDateTimeLima(value); }
  stock(id: number | null): number { return this.products().find(p => p.idProducto === Number(id))?.stockActual ?? 0; }
  addLine(): void { this.details = [...this.details, { idProducto: null, cantidad: 1 }]; }
  removeLine(index: number): void { if (this.details.length > 1) this.details = this.details.filter((_, i) => i !== index); }

  load(): void {
    this.loading.set(true); this.error.set('');
    this.api.getTraslados(this.estado).subscribe({
      next: items => { this.transfers.set(items); this.loading.set(false); },
      error: e => { this.error.set(e?.error?.error || 'No se pudieron cargar los traslados.'); this.loading.set(false); }
    });
  }

  create(): void {
    this.error.set(''); this.success.set('');
    if (!this.destinoId || this.destinoId === this.siteId || !this.referencia.trim() || this.details.some(d => !d.idProducto || d.cantidad < 1)) {
      this.error.set('Completa sede destino, referencia y productos con cantidades positivas.'); return;
    }
    if (this.details.some(d => this.details.filter(x => x.idProducto === d.idProducto).length > 1)) {
      this.error.set('No repitas productos en el mismo traslado.'); return;
    }
    this.busy.set(true);
    this.api.crearTraslado({ idSedeOrigen: this.siteId, idSedeDestino: this.destinoId, referencia: this.referencia.trim(), observaciones: this.observaciones.trim(), detalles: this.details.map(d => ({ idProducto: Number(d.idProducto), cantidad: Number(d.cantidad) })) }).subscribe({
      next: () => { this.success.set('Traslado creado. El stock se descontará al confirmar el despacho.'); this.referencia = ''; this.observaciones = ''; this.details = [{ idProducto: null, cantidad: 1 }]; this.busy.set(false); this.load(); },
      error: e => { this.error.set(e?.error?.error || 'No se pudo crear el traslado.'); this.busy.set(false); }
    });
  }

  dispatch(t: Traslado): void {
    this.act(() => this.api.despacharTraslado(t.idTraslado, this.key()));
  }

  receive(t: Traslado): void {
    const lines = t.detalles.map(d => ({ idProducto: d.idProducto, cantidad: Number(this.receiving[d.idProducto] || 0) })).filter(d => d.cantidad > 0);
    if (!lines.length || lines.some(line => line.cantidad > (t.detalles.find(d => d.idProducto === line.idProducto)?.cantidadEnTransito ?? 0))) {
      this.error.set('Indica cantidades recibidas mayores que cero y no superiores a lo que está en tránsito.'); return;
    }
    this.act(() => this.api.recibirTraslado(t.idTraslado, this.key(), lines), 'Recepción registrada.');
  }

  cancel(t: Traslado): void {
    const ref = this.returnReference[t.idTraslado]?.trim();
    if (t.estado === 'EN_TRANSITO' && !ref) { this.error.set('Para cancelar un traslado despachado, registra primero la referencia de devolución física.'); return; }
    this.act(() => this.api.cancelarTraslado(t.idTraslado, this.key(), ref), 'Traslado cancelado.');
  }

  private act(request: () => import('rxjs').Observable<Traslado>, message = 'Acción registrada.'): void {
    this.error.set(''); this.success.set(''); this.busy.set(true);
    request().subscribe({ next: () => { this.success.set(message); this.busy.set(false); this.load(); }, error: e => { this.error.set(e?.error?.error || 'No se pudo completar la acción.'); this.busy.set(false); } });
  }
  private key(): string { return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`; }
}

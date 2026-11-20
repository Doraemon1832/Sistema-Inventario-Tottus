import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import { InventarioItem, OperacionInventario, OperacionRequest, Producto, ProveedorOption } from '../../models/inventario.model';
import { DataSyncService } from '../../services/data-sync.service';
import { ConfirmationDialogComponent } from '../shared/confirmation-dialog.component';
import { formatDateTimeLima } from '../../utils/date-time-lima';

interface ProductOption { idProducto: number; codigo: string; nombre: string; categoria?: string; unidadMedida?: string | null; stockActual?: number; }
interface OperationLine { idProducto: number | null; cantidad: number; busqueda: string; }

@Component({
  selector: 'app-operaciones', standalone: true, imports: [CommonModule, FormsModule, ConfirmationDialogComponent],
  templateUrl: './operaciones.component.html', styleUrl: './operaciones.component.scss'
})
export class OperacionesComponent implements OnInit {
  tipo = signal<'ingresos' | 'egresos'>('ingresos');
  titulo = computed(() => this.tipo() === 'ingresos' ? 'Ingresos de inventario' : 'Egresos de inventario');
  canEdit = computed(() => this.tipo() === 'ingresos' && this.auth.hasAnyRole('ADMINISTRADOR', 'OPERADOR_ALMACEN'));
  canRegister = computed(() => this.tipo() === 'ingresos'
    ? this.auth.hasAnyRole('ADMINISTRADOR', 'JEFE_INVENTARIO', 'OPERADOR_ALMACEN')
    : this.auth.hasAnyRole('ADMINISTRADOR', 'OPERADOR_ALMACEN', 'VENDEDOR'));
  canReviewSensitiveEgress = computed(() => this.tipo() === 'egresos' && this.auth.hasAnyRole('ADMINISTRADOR', 'JEFE_INVENTARIO'));
  rejectingId = signal<number | null>(null);
  rejectionReason = '';
  operaciones = signal<OperacionInventario[]>([]);
  productos = signal<ProductOption[]>([]);
  proveedores = signal<ProveedorOption[]>([]);
  activeTab = signal<'registro' | 'historial'>('registro');
  activeAutocomplete = signal<number | null>(null);
  activeProductId = signal<number | null>(null);
  busy = signal(false);
  error = signal('');
  success = signal('');
  confirmationOpen = signal(false);
  confirmationError = signal('');
  subtipo = signal<string | null>(null);
  canSeeStock = computed(() => this.auth.hasAnyRole('ADMINISTRADOR', 'JEFE_INVENTARIO', 'OPERADOR_ALMACEN', 'VENDEDOR'));
  query = '';
  desde = '';
  hasta = '';
  referencia = '';
  idProveedor: number | null = null;
  beneficiario = '';
  motivo = '';
  observaciones = '';
  editingId: number | null = null;
  detalles: OperationLine[] = [{ idProducto: null, cantidad: 1, busqueda: '' }];
  private pendingPayload: OperacionRequest | null = null;
  readonly sedeId: number;
  private stopSync?: () => void;

  get sedeName(): string { return this.auth.currentUser()?.nombreSede || `Sede ${this.sedeId}`; }
  get responsibleName(): string { return this.auth.currentUser()?.usuario || 'Usuario autenticado'; }
  get supplierName(): string { return this.proveedores().find(p => p.idProveedor === this.idProveedor)?.nombre || 'Sin seleccionar'; }
  formatDate(value: string | null | undefined): string { return formatDateTimeLima(value); }
  get subtypeLabel(): string {
    const labels: Record<string, string> = {
      INGRESO_GENERAL: 'Ingreso general', COMPRA: 'Compra a proveedor', DEVOLUCION_CLIENTE: 'Devolución de cliente',
      PRODUCCION: 'Entrada por producción', BONIFICACION_PROVEEDOR: 'Bonificación de proveedor', VENTA: 'Venta de mercadería',
      DEVOLUCION_PROVEEDOR: 'Devolución a proveedor', MERMA: 'Merma', DESMEDRO: 'Desmedro', DONACION: 'Donación entregada', CONSUMO_INTERNO: 'Consumo interno'
    };
    return labels[this.subtipo() ?? ''] ?? 'Sin clasificar (histórico)';
  }
  get allowedSubtypes(): string[] {
    if (this.tipo() === 'ingresos') return ['DEVOLUCION_CLIENTE', 'PRODUCCION', 'BONIFICACION_PROVEEDOR'];
    if (this.auth.hasAnyRole('VENDEDOR') && !this.auth.hasAnyRole('ADMINISTRADOR')) return ['VENTA'];
    if (this.auth.hasAnyRole('OPERADOR_ALMACEN') && !this.auth.hasAnyRole('ADMINISTRADOR')) return ['DEVOLUCION_PROVEEDOR', 'MERMA', 'DESMEDRO', 'DONACION', 'CONSUMO_INTERNO'];
    return ['VENTA', 'DEVOLUCION_PROVEEDOR', 'MERMA', 'DESMEDRO', 'DONACION', 'CONSUMO_INTERNO'];
  }
  subtypeOptionLabel(kind: string): string {
    const labels: Record<string, string> = {
      VENTA: 'Venta de mercadería', DEVOLUCION_PROVEEDOR: 'Devolución a proveedor', MERMA: 'Merma',
      DESMEDRO: 'Desmedro', DONACION: 'Donación entregada', CONSUMO_INTERNO: 'Consumo interno'
    };
    return labels[kind] ?? kind;
  }
  needsSupplier(): boolean { return ['BONIFICACION_PROVEEDOR', 'DEVOLUCION_PROVEEDOR'].includes(this.subtipo() ?? ''); }
  needsBeneficiary(): boolean { return this.subtipo() === 'DONACION'; }
  needsReason(): boolean { return ['DEVOLUCION_CLIENTE', 'PRODUCCION', 'BONIFICACION_PROVEEDOR', 'DEVOLUCION_PROVEEDOR', 'MERMA', 'DESMEDRO', 'DONACION', 'CONSUMO_INTERNO'].includes(this.subtipo() ?? ''); }

  cambiarSubtipo(value: string): void {
    this.subtipo.set(value);
    if (!this.needsSupplier()) this.idProveedor = null;
    if (!this.needsBeneficiary()) this.beneficiario = '';
    if (!this.needsReason()) this.motivo = '';
  }

  constructor(private route: ActivatedRoute, private api: InventarioService, private auth: AuthService, private sync: DataSyncService) {
    this.sedeId = this.auth.activeSedeId() || this.auth.currentUser()?.idSede || 0;
  }

  ngOnInit(): void {
    this.api.getProveedores(true).subscribe({ next: list => this.proveedores.set(list) });
    this.stopSync = this.sync.listen(['all', 'inventory', 'operations'], () => this.cargar());
    this.route.data.subscribe(data => {
      this.tipo.set(data['tipo'] === 'egresos' ? 'egresos' : 'ingresos');
      if (!this.editingId) this.subtipo.set(null);
      this.activeTab.set(this.canRegister() ? 'registro' : 'historial');
      this.cargar();
    });
    if (this.auth.hasAnyRole('ENCARGADO_COMPRAS') && !this.auth.hasAnyRole('ADMINISTRADOR', 'OPERADOR_ALMACEN')) {
      this.api.getProductos().subscribe({ next: items => this.productos.set(items.filter(p => p.estado).map(p => ({ idProducto: p.idProducto, codigo: p.codigo, nombre: p.nombre, categoria: p.categoria?.nombre, unidadMedida: p.unidadMedida }))) });
    } else {
      this.api.getInventario(this.sedeId).subscribe({ next: items => this.productos.set(items.filter(p => p.estado).map(p => ({ idProducto: p.idProducto, codigo: p.codigoProducto, nombre: p.nombreProducto, categoria: p.categoria, unidadMedida: p.unidadMedida, stockActual: p.stockActual }))) });
    }
  }

  ngOnDestroy(): void { this.stopSync?.(); }

  cargar(): void {
    this.busy.set(true); this.error.set('');
    this.api.getOperaciones(this.tipo(), this.sedeId, this.query, this.desde, this.hasta).subscribe({
      next: data => { this.operaciones.set(data); this.busy.set(false); },
      error: e => { this.error.set(e?.error?.error || 'No se pudieron cargar las operaciones.'); this.busy.set(false); }
    });
  }

  seleccionarTab(tab: 'registro' | 'historial'): void { this.activeTab.set(tab); }
  navegarPestanas(event: KeyboardEvent): void {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    const tabs: Array<'registro' | 'historial'> = this.canRegister() ? ['registro', 'historial'] : ['historial'];
    const current = tabs.indexOf(this.activeTab());
    const direction = event.key === 'ArrowRight' ? 1 : -1;
    const next = tabs[(current + direction + tabs.length) % tabs.length];
    event.preventDefault();
    this.selectTabAndFocus(next);
  }

  private selectTabAndFocus(tab: 'registro' | 'historial'): void {
    this.activeTab.set(tab);
    window.setTimeout(() => document.getElementById(tab === 'registro' ? 'register-tab' : 'history-tab')?.focus());
  }

  agregarDetalle(): void { this.detalles = [...this.detalles, { idProducto: null, cantidad: 1, busqueda: '' }]; }
  quitarDetalle(index: number): void { if (this.detalles.length > 1) this.detalles = this.detalles.filter((_, i) => i !== index); }

  actualizarBusqueda(index: number, value: string): void {
    const line = this.detalles[index];
    if (!line) return;
    line.busqueda = value;
    this.activeAutocomplete.set(index);
    this.activeProductId.set(null);
    const normalized = value.trim().toLocaleLowerCase();
    const selected = this.productos().find(product =>
      product.codigo.toLocaleLowerCase() === normalized
      || product.nombre.toLocaleLowerCase() === normalized
      || this.productLabel(product).toLocaleLowerCase() === normalized
    );
    line.idProducto = selected?.idProducto ?? null;
    if (selected && this.detalles.some((other, otherIndex) => otherIndex !== index && other.idProducto === selected.idProducto)) {
      line.idProducto = null;
      this.error.set('Cada producto debe aparecer una sola vez en el detalle.');
    } else if (selected) {
      this.error.set('');
    }
    this.activeProductId.set(null);
    this.detalles = [...this.detalles];
  }

  seleccionarProducto(index: number, idProducto: number): void {
    const product = this.productos().find(item => item.idProducto === idProducto);
    if (!product) return;
    this.actualizarBusqueda(index, this.productLabel(product));
    this.activeAutocomplete.set(null);
    this.activeProductId.set(null);
  }

  abrirAutocompletado(index: number): void {
    this.activeAutocomplete.set(index);
    this.activeProductId.set(null);
  }
  cerrarAutocompletado(index: number): void {
    if (this.activeAutocomplete() === index) {
      this.activeAutocomplete.set(null);
      this.activeProductId.set(null);
    }
  }

  filteredProducts(index: number): ProductOption[] {
    const line = this.detalles[index];
    const query = line?.busqueda.trim().toLocaleLowerCase() ?? '';
    const selectedElsewhere = new Set(this.detalles.flatMap((item, itemIndex) => itemIndex !== index && item.idProducto ? [item.idProducto] : []));
    return this.productos().filter(product => !selectedElsewhere.has(product.idProducto)
      && (!query || `${product.codigo} ${product.nombre} ${product.categoria ?? ''}`.toLocaleLowerCase().includes(query)));
  }

  optionId(lineIndex: number, productId: number): string { return `product-option-${lineIndex}-${productId}`; }
  activeOptionId(lineIndex: number): string | null {
    const activeId = this.activeProductId();
    return this.activeAutocomplete() === lineIndex && activeId !== null
      && this.filteredProducts(lineIndex).some(product => product.idProducto === activeId)
      ? this.optionId(lineIndex, activeId)
      : null;
  }

  navegarAutocompletado(event: KeyboardEvent, lineIndex: number): void {
    const options = this.filteredProducts(lineIndex);
    const currentIndex = options.findIndex(product => product.idProducto === this.activeProductId());
    if (event.key === 'ArrowDown' && options.length) {
      event.preventDefault();
      this.activeAutocomplete.set(lineIndex);
      this.activeProductId.set(options[currentIndex < 0 ? 0 : Math.min(currentIndex + 1, options.length - 1)].idProducto);
    } else if (event.key === 'ArrowUp' && options.length) {
      event.preventDefault();
      this.activeAutocomplete.set(lineIndex);
      this.activeProductId.set(options[currentIndex < 0 ? options.length - 1 : Math.max(currentIndex - 1, 0)].idProducto);
    } else if (event.key === 'Enter' && this.activeAutocomplete() === lineIndex && options.length) {
      const highlighted = options.find(product => product.idProducto === this.activeProductId());
      const query = this.detalles[lineIndex]?.busqueda.trim().toLocaleLowerCase() ?? '';
      const exact = options.find(product => product.codigo.toLocaleLowerCase() === query
        || product.nombre.toLocaleLowerCase() === query
        || this.productLabel(product).toLocaleLowerCase() === query);
      event.preventDefault();
      if (highlighted || exact) {
        this.seleccionarProducto(lineIndex, (highlighted ?? exact)!.idProducto);
      }
    } else if (event.key === 'Escape') {
      this.cerrarAutocompletado(lineIndex);
    }
  }

  productLabel(product: ProductOption): string { return `${product.codigo} · ${product.nombre}`; }
  productFor(idProducto: number | null): ProductOption | undefined { return this.productos().find(product => product.idProducto === idProducto); }
  currentStock(line: OperationLine): number | null {
    const stock = this.productFor(line.idProducto)?.stockActual;
    return this.canSeeStock() && Number.isFinite(stock) ? Number(stock) : null;
  }
  projectedStock(line: OperationLine): number | null {
    const current = this.currentStock(line);
    if (current === null) return null;
    const quantity = Number(line.cantidad);
    if (this.tipo() === 'egresos') return current - quantity;
    const previous = this.editingId
      ? this.operaciones().find(op => op.idOperacion === this.editingId)?.detalles.find(detail => detail.idProducto === line.idProducto)?.cantidad ?? 0
      : 0;
    return current + quantity - previous;
  }
  get confirmationTitle(): string {
    if (this.editingId) return 'Confirmar corrección del ingreso';
    return this.tipo() === 'ingresos' ? 'Confirmar ingreso de inventario' : 'Confirmar egreso de inventario';
  }
  get confirmationLabel(): string { return this.editingId ? 'Confirmar corrección' : `Confirmar ${this.tipo() === 'ingresos' ? 'ingreso' : 'egreso'}`; }

  editar(op: OperacionInventario): void {
    if (!this.canEdit()) return;
    this.editingId = op.idOperacion; this.referencia = op.referencia; this.subtipo.set(op.subtipo ?? null);
    this.idProveedor = op.idProveedor ?? null; this.beneficiario = op.beneficiario ?? '';
    this.motivo = op.motivo ?? ''; this.observaciones = op.observaciones ?? '';
    this.detalles = op.detalles.map(d => {
      const product = this.productos().find(item => item.idProducto === d.idProducto);
      return { idProducto: d.idProducto, cantidad: d.cantidad, busqueda: product ? this.productLabel(product) : `${d.codigoProducto ?? ''} ${d.nombreProducto ?? ''}`.trim() };
    });
    this.activeTab.set('registro');
    this.error.set(''); this.success.set('');
    document.querySelector('.operation-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  cancelarEdicion(): void {
    this.editingId = null; this.referencia = ''; this.detalles = [{ idProducto: null, cantidad: 1, busqueda: '' }];
    this.subtipo.set(null);
    this.idProveedor = null; this.beneficiario = ''; this.motivo = ''; this.observaciones = '';
  }

  guardar(): void {
    if (!this.canRegister() && !this.editingId) return;
    if (this.editingId && !this.canEdit()) return;
    this.error.set('');
    this.success.set('');
    if (!this.editingId && !this.subtipo()) {
      this.error.set('Selecciona un tipo de operación.');
      return;
    }
    if (!this.editingId && this.tipo() === 'ingresos' && this.subtipo() === 'COMPRA') {
      this.error.set('Las compras se reciben desde Compras para evitar contabilizar el stock dos veces.');
      return;
    }
    if (this.editingId && this.tipo() === 'ingresos' && this.subtipo() !== this.operaciones().find(op => op.idOperacion === this.editingId)?.subtipo) {
      this.error.set('No se puede cambiar la clasificación del ingreso existente.');
      return;
    }
    if (this.needsSupplier() && !this.idProveedor) { this.error.set('Selecciona el proveedor relacionado.'); return; }
    if (this.needsBeneficiary() && !this.beneficiario.trim()) { this.error.set('Indica el beneficiario de la donación.'); return; }
    if (this.needsReason() && !this.motivo.trim()) { this.error.set('Indica el motivo de la operación.'); return; }
    const clean = this.detalles.filter(d => d.idProducto && Number.isInteger(Number(d.cantidad)) && d.cantidad > 0);
    if (!this.sedeId || !this.referencia.trim() || !clean.length || clean.length !== this.detalles.length) {
      this.error.set('Completa la referencia documental o el motivo y selecciona productos con cantidades mayores que cero.');
      return;
    }
    if (new Set(clean.map(d => d.idProducto)).size !== clean.length) {
      this.error.set('Cada producto debe aparecer una sola vez.');
      return;
    }
    if (this.tipo() === 'egresos' && this.canSeeStock()) {
      const insufficient = clean.find(line => {
        const current = this.currentStock(line);
        return current !== null && line.cantidad > current;
      });
      if (insufficient) {
        const product = this.productFor(insufficient.idProducto);
        this.error.set('Stock insuficiente para ' + (product?.nombre ?? 'el producto') + '. Disponible: '
          + this.currentStock(insufficient) + ' unidades.');
        return;
      }
    }
    this.pendingPayload = {
      idSede: this.sedeId,
      ...(this.subtipo() ? { subtipo: this.subtipo()! } : {}),
      ...(this.needsSupplier() && this.idProveedor ? { idProveedor: this.idProveedor } : {}),
      ...(this.needsBeneficiary() ? { beneficiario: this.beneficiario.trim() } : {}),
      ...(this.motivo.trim() ? { motivo: this.motivo.trim() } : {}),
      ...(this.observaciones.trim() ? { observaciones: this.observaciones.trim() } : {}),
      referencia: this.referencia.trim(),
      detalles: clean.map(d => ({ idProducto: Number(d.idProducto), cantidad: Number(d.cantidad) }))
    };
    this.confirmationError.set('');
    document.querySelector<HTMLButtonElement>('.operation-form .form-actions button[type="submit"]')?.focus();
    this.confirmationOpen.set(true);
  }

  cancelarConfirmacion(): void {
    if (this.busy()) return;
    this.confirmationOpen.set(false);
    this.confirmationError.set('');
    this.pendingPayload = null;
  }

  confirmarOperacion(): void {
    if (this.busy() || !this.pendingPayload) return;
    const wasEditing = this.editingId !== null;
    this.busy.set(true);
    this.confirmationError.set('');
    const request = this.editingId
      ? this.api.editarIngreso(this.editingId, this.pendingPayload)
      : this.api.crearOperacion(this.tipo(), this.pendingPayload);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.confirmationOpen.set(false);
        this.pendingPayload = null;
        this.success.set(wasEditing
          ? 'Ingreso corregido; el Kardex conserva los movimientos compensatorios.'
          : 'Operacion registrada correctamente.');
        this.sync.announce('operations');
        this.cancelarEdicion();
        this.cargar();
      },
      error: e => {
        this.busy.set(false);
        this.confirmationError.set(e?.error?.error
          || 'No se pudo guardar la operacion. Revisa los datos e intentalo nuevamente.');
      }
    });
  }

  approveEgress(op: OperacionInventario): void {
    if (!this.canReviewSensitiveEgress() || op.estado !== 'PENDIENTE' || this.busy()) return;
    this.busy.set(true);
    this.api.aprobarEgreso(op.idOperacion, this.sedeId).subscribe({
      next: () => { this.busy.set(false); this.success.set('Solicitud aprobada y movimiento registrado en Kardex.'); this.sync.announce('operations'); this.cargar(); },
      error: e => { this.busy.set(false); this.error.set(e?.error?.error || 'No se pudo aprobar la solicitud.'); }
    });
  }

  beginReject(op: OperacionInventario): void { if (op.estado === 'PENDIENTE' && this.canReviewSensitiveEgress()) { this.rejectingId.set(op.idOperacion); this.rejectionReason = ''; } }
  cancelReject(): void { this.rejectingId.set(null); this.rejectionReason = ''; }
  rejectEgress(op: OperacionInventario): void {
    const reason = this.rejectionReason.trim();
    if (!reason || reason.length > 255 || this.busy()) return;
    this.busy.set(true);
    this.api.rechazarEgreso(op.idOperacion, this.sedeId, reason).subscribe({
      next: () => { this.busy.set(false); this.rejectingId.set(null); this.success.set('Solicitud rechazada con justificación.'); this.sync.announce('operations'); this.cargar(); },
      error: e => { this.busy.set(false); this.error.set(e?.error?.error || 'No se pudo rechazar la solicitud.'); }
    });
  }
}

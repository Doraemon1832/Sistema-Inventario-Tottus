import { Component, OnInit, signal, computed, effect, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import { DataSyncService } from '../../services/data-sync.service';
import { ConfirmationDialogComponent } from '../shared/confirmation-dialog.component';
import {
  InventarioItem,
  ResumenInventario,
  Categoria,
  MovimientoRequest,

  ProductoRequest
} from '../../models/inventario.model';

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmationDialogComponent],
  templateUrl: './inventario.component.html',
  styleUrl: './inventario.component.scss'
})
export class InventarioComponent implements OnInit {
  private modalTrigger: HTMLElement | null = null;
  private movementReviewTrigger: HTMLElement | null = null;
  private stopSync?: () => void;
  protected readonly Number = Number;
  canSeeSummary = computed(() => this.authService.hasAnyRole('ADMINISTRADOR', 'JEFE_INVENTARIO'));
  canManageProducts = computed(() => this.authService.hasAnyRole('ADMINISTRADOR', 'OPERADOR_ALMACEN'));
  canRegisterEntry = computed(() => this.authService.hasAnyRole('ADMINISTRADOR', 'OPERADOR_ALMACEN'));
  canRegisterExit = computed(() => this.authService.hasAnyRole('ADMINISTRADOR', 'OPERADOR_ALMACEN', 'VENDEDOR'));
  canAdjustStock = computed(() => this.authService.hasAnyRole('ADMINISTRADOR'));


  inventario = signal<InventarioItem[]>([]);
  resumen = signal<ResumenInventario>({
    totalProductos: 0,
    stockBajo: 0,
    sinStock: 0,
    totalUnidades: 0,
    movimientosHoy: 0
  });
  categorias = signal<Categoria[]>([]);

  searchTerm = signal('');
  selectedCategoriaId = signal<number | null>(null);
  selectedStockStatus = signal<string>('TODOS');
  selectedEstadoFilter = signal<'TODOS' | 'ACTIVOS' | 'INACTIVOS'>('TODOS');
  isLoading = signal(false);


  inventarioFiltrado = computed(() => {
    const list = this.inventario();
    const filtro = this.selectedEstadoFilter();
    if (filtro === 'ACTIVOS') {
      return list.filter((item) => item.estado !== false);
    }
    if (filtro === 'INACTIVOS') {
      return list.filter((item) => item.estado === false);
    }
    return list;
  });


  productosParaMovimiento = computed(() => {
    return this.inventario().filter((item) => item.estado !== false);
  });


  showMovimientoModal = signal(false);
  showProductoModal = signal(false);
  showEditModal = signal(false);
  showInactivarModal = signal(false);
  movementConfirmOpen = signal(false);
  itemParaReactivar = signal<InventarioItem | null>(null);
  reactivarLoading = signal(false);
  reactivarError = signal('');


  selectedItemForMov = signal<InventarioItem | null>(null);
  movTipo = signal<'ENTRADA' | 'SALIDA' | 'AJUSTE'>('ENTRADA');
  movCantidad = signal<number>(10);
  movReferencia = signal<string>('');
  movError = signal<string | null>(null);
  movLoading = signal(false);


  nuevoCodigo = signal('');
  nuevoNombre = signal('');
  nuevoUnidadMedida = signal('');
  nuevoCategoriaId = signal<number | null>(null);
  nuevoPrecio = signal<number>(10.0);
  nuevoStockMinimo = signal<number>(10);
  nuevoStockInicial = signal<number>(20);
  nuevoUbicacion = signal('Pasillo 01 - Góndola A');
  prodError = signal<string | null>(null);
  prodLoading = signal(false);


  editIdProducto = signal<number | null>(null);
  editCodigo = signal('');
  editNombre = signal('');
  editUnidadMedida = signal('');
  editCategoriaId = signal<number | null>(null);
  editPrecio = signal<number>(0);
  editStockMinimo = signal<number>(0);
  editUbicacion = signal('');
  editError = signal<string | null>(null);
  editLoading = signal(false);


  itemParaInactivar = signal<InventarioItem | null>(null);
  inactivarLoading = signal(false);
  inactivarError = signal<string | null>(null);

  constructor(
    public authService: AuthService,
    private inventarioService: InventarioService,
    private sync: DataSyncService
  ) {
    // Reaccionar cuando cambie la sede activa en el AuthService
    effect(() => {
      const sedeId = this.authService.activeSedeId();
      if (sedeId) {
        this.cargarDatos();
      }
    });
  }

  ngOnInit(): void {
    this.stopSync = this.sync.listen(['all', 'inventory', 'operations'], () => this.cargarDatos());
    this.cargarCategorias();
    this.cargarDatos();
  }

  ngOnDestroy(): void { this.stopSync?.(); }

  cargarDatos(): void {
    const sedeId = this.authService.activeSedeId();
    if (!sedeId) {
      this.inventario.set([]);
      this.resumen.set({ totalProductos: 0, stockBajo: 0, sinStock: 0, totalUnidades: 0, movimientosHoy: 0 });
      this.isLoading.set(false);
      return;
    }
    this.isLoading.set(true);

    if (this.canSeeSummary()) {
      this.inventarioService.getResumen(sedeId).subscribe({
        next: (res) => this.resumen.set(res),
        error: (err) => console.error('Error cargando resumen', err)
      });
    }

    this.inventarioService
      .getInventario(
        sedeId,
        this.selectedCategoriaId() || undefined,
        this.searchTerm(),
        this.selectedStockStatus()
      )
      .subscribe({
        next: (items) => {
          this.inventario.set(items);
          this.isLoading.set(false);
        },
        error: (err) => {
          console.error('Error cargando inventario', err);
          this.isLoading.set(false);
        }
      });
  }

  cargarCategorias(): void {
    if (!this.canManageProducts()) return;
    this.inventarioService.getCategorias().subscribe({
      next: (cats) => {
        this.categorias.set(cats);
        if (cats.length > 0 && !this.nuevoCategoriaId()) {
          this.nuevoCategoriaId.set(cats[0].idCategoria);
        }
      }
    });
  }

  onFilterChange(): void {
    this.cargarDatos();
  }

  setStockFilter(status: string): void {
    this.selectedStockStatus.set(status);
    this.cargarDatos();
  }

  setEstadoFilter(filtro: 'TODOS' | 'ACTIVOS' | 'INACTIVOS'): void {
    this.selectedEstadoFilter.set(filtro);
  }


  abrirModalMovimiento(item?: InventarioItem, tipo?: 'ENTRADA' | 'SALIDA' | 'AJUSTE'): void {
    if (tipo === 'ENTRADA' && !this.canRegisterEntry()
        || tipo === 'SALIDA' && !this.canRegisterExit()
        || tipo === 'AJUSTE' && !this.canAdjustStock()) return;
    if (item && item.estado === false) {
      return; // No permitir registrar movimientos en productos inactivos
    }
    const activos = this.productosParaMovimiento();
    this.selectedItemForMov.set(item || (activos[0] || null));
    if (tipo) this.movTipo.set(tipo);
    this.movCantidad.set(tipo === 'AJUSTE' ? Number(item?.stockActual ?? 0) : 1);
    this.movReferencia.set('');
    this.movError.set(null);
    this.captureModalTrigger();
    this.showMovimientoModal.set(true);
    this.focusDialogOnOpen();
  }

  cerrarModalMovimiento(): void {
    this.showMovimientoModal.set(false);
    this.restoreModalTrigger();
  }

  actualizarCantidadMovimiento(value: unknown): void {
    const normalized = String(value ?? '').trim();
    this.movCantidad.set(normalized ? Number(normalized) : Number.NaN);
  }

  guardarMovimiento(event?: Event): void {
    const item = this.selectedItemForMov();
    if (!item) return;

    if (item.estado === false) {
      this.movError.set('El producto se encuentra inactivo. Reactívelo para operar con él.');
      return;
    }

    if (!Number.isInteger(this.movCantidad())
        || (this.movTipo() === 'AJUSTE' ? this.movCantidad() < 0 : this.movCantidad() <= 0)) {
      this.movError.set(this.movTipo() === 'AJUSTE'
        ? 'El stock final debe ser un numero entero igual o mayor a cero.'
        : 'La cantidad debe ser un numero entero mayor a cero.');
      return;
    }

    if (!this.movReferencia().trim()) {
      this.movError.set('Ingresa una referencia documental o el motivo del movimiento.');
      return;
    }

    if (this.movTipo() === 'SALIDA' && this.movCantidad() > item.stockActual) {
      this.movError.set(`Stock insuficiente. Disponible: ${item.stockActual}`);
      return;
    }

    this.movError.set(null);
    this.movementReviewTrigger = event?.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    this.movementConfirmOpen.set(true);
  }

  cancelarRevisionMovimiento(): void {
    this.movementConfirmOpen.set(false);
    const trigger = this.movementReviewTrigger;
    this.movementReviewTrigger = null;
    window.setTimeout(() => trigger?.focus());
  }

  confirmarMovimiento(): void {
    const item = this.selectedItemForMov();
    if (!item || this.movLoading()) return;
    this.movLoading.set(true);
    this.movError.set(null);

    const req: MovimientoRequest = {
      idSede: item.idSede || this.authService.currentUser()?.idSede || 0,
      idProducto: item.idProducto,
      tipo: this.movTipo(),
      cantidad: this.movCantidad(),
      referencia: this.movReferencia().trim()
    };

    this.inventarioService.registrarMovimiento(req).subscribe({
      next: () => {
        this.movLoading.set(false);
        this.movementConfirmOpen.set(false);
        this.sync.announce('operations');
        this.cerrarModalMovimiento();
        this.cargarDatos();
      },
      error: (err) => {
        this.movLoading.set(false);
        this.movError.set(err.error?.error || 'Error al registrar el movimiento');
        this.movementConfirmOpen.set(false);
      }
    });
  }


  abrirModalEdicion(item: InventarioItem): void {
    if (!this.canManageProducts()) return;
    this.editIdProducto.set(item.idProducto);
    this.editCodigo.set(item.codigoProducto);
    this.editNombre.set(item.nombreProducto);
    this.editUnidadMedida.set(item.unidadMedida || '');
    this.editCategoriaId.set(item.idCategoria);
    this.editPrecio.set(item.precio);
    this.editStockMinimo.set(item.stockMinimo);
    this.editUbicacion.set(item.ubicacion);
    this.editError.set(null);
    this.captureModalTrigger();
    this.showEditModal.set(true);
    this.focusDialogOnOpen();
  }

  cerrarModalEdicion(): void {
    this.showEditModal.set(false);
    this.restoreModalTrigger();
  }

  guardarEdicion(): void {
    const id = this.editIdProducto();
    if (!id || !this.editCodigo() || !this.editNombre() || !this.editCategoriaId()) {
      this.editError.set('Complete todos los campos obligatorios');
      return;
    }

    this.editLoading.set(true);
    this.editError.set(null);

    const req: ProductoRequest = {
      codigo: this.editCodigo().trim(),
      nombre: this.editNombre().trim(),
      unidadMedida: this.editUnidadMedida().trim(),
      idCategoria: this.editCategoriaId()!,
      precio: this.editPrecio(),
      stockMinimo: this.editStockMinimo(),
      idSede: this.authService.activeSedeId() ?? this.authService.currentUser()?.idSede,
      ubicacion: this.editUbicacion().trim()
    };

    this.inventarioService.actualizarProducto(id, req).subscribe({
      next: () => {
        this.editLoading.set(false);
        this.sync.announce('inventory');
        this.cerrarModalEdicion();
        this.cargarDatos();
      },
      error: (err) => {
        this.editLoading.set(false);
        this.editError.set(err.error?.error || 'Error al actualizar el producto');
      }
    });
  }


  abrirModalInactivar(item: InventarioItem): void {
    if (!this.canManageProducts()) return;
    this.itemParaInactivar.set(item);
    this.inactivarError.set(null);
    this.captureModalTrigger();
    this.showInactivarModal.set(true);
    this.focusDialogOnOpen();
  }

  cerrarModalInactivar(): void {
    this.showInactivarModal.set(false);
    this.itemParaInactivar.set(null);
    this.restoreModalTrigger();
  }

  confirmarInactivacion(): void {
    const item = this.itemParaInactivar();
    if (!item) return;

    this.inactivarLoading.set(true);
    this.inactivarError.set(null);

    this.inventarioService.cambiarEstadoProducto(item.idProducto, false).subscribe({
      next: () => {
        this.inactivarLoading.set(false);
        this.sync.announce('inventory');
        this.cerrarModalInactivar();
        this.cargarDatos();
      },
      error: (err) => {
        this.inactivarLoading.set(false);
        this.inactivarError.set(err.error?.error || 'Error al inactivar el producto');
      }
    });
  }

  reactivarProducto(item: InventarioItem): void {
    if (!this.canManageProducts()) return;
    this.itemParaReactivar.set(item);
    this.reactivarError.set('');
  }

  cancelarReactivacion(): void {
    if (this.reactivarLoading()) return;
    this.itemParaReactivar.set(null);
    this.reactivarError.set('');
  }

  confirmarReactivacion(): void {
    const item = this.itemParaReactivar();
    if (!item || this.reactivarLoading()) return;
    this.reactivarLoading.set(true);
    this.inventarioService.cambiarEstadoProducto(item.idProducto, true).subscribe({
      next: () => {
        this.reactivarLoading.set(false);
        this.itemParaReactivar.set(null);
        this.sync.announce('inventory');
        this.cargarDatos();
      },
      error: (err) => {
        this.reactivarLoading.set(false);
        this.reactivarError.set(err.error?.error || 'Error al reactivar el producto');
      }
    });
  }


  abrirModalProducto(): void {
    if (!this.canManageProducts()) return;
    this.nuevoCodigo.set('TOT-NVO-' + Math.floor(100 + Math.random() * 900));
    this.nuevoNombre.set('');
    this.nuevoUnidadMedida.set('');
    this.nuevoPrecio.set(9.90);
    this.nuevoStockMinimo.set(10);
    this.nuevoStockInicial.set(25);
    this.nuevoUbicacion.set('Pasillo 02 - Góndola Central');
    this.prodError.set(null);
    this.captureModalTrigger();
    this.showProductoModal.set(true);
    this.focusDialogOnOpen();
  }

  cerrarModalProducto(): void {
    this.showProductoModal.set(false);
    this.restoreModalTrigger();
  }

  guardarProducto(): void {
    if (!this.nuevoCodigo() || !this.nuevoNombre() || !this.nuevoCategoriaId()) {
      this.prodError.set('Complete todos los campos requeridos');
      return;
    }

    this.prodLoading.set(true);
    this.prodError.set(null);

    const req: ProductoRequest = {
      codigo: this.nuevoCodigo(),
      nombre: this.nuevoNombre(),
      unidadMedida: this.nuevoUnidadMedida().trim(),
      idCategoria: this.nuevoCategoriaId()!,
      precio: this.nuevoPrecio(),
      stockMinimo: this.nuevoStockMinimo(),
      idSede: this.authService.activeSedeId() ?? this.authService.currentUser()?.idSede,
      stockInicial: this.nuevoStockInicial(),
      ubicacion: this.nuevoUbicacion()
    };

    this.inventarioService.crearProducto(req).subscribe({
      next: () => {
        this.prodLoading.set(false);
        this.sync.announce('inventory');
        this.cerrarModalProducto();
        this.cargarDatos();
      },
      error: (err) => {
        this.prodLoading.set(false);
        this.prodError.set(err.error?.error || 'Error al guardar el producto');
      }
    });
  }

  @HostListener('document:keydown', ['$event'])
  onDialogKeydown(event: KeyboardEvent): void {
    const dialog = document.querySelector<HTMLElement>('[role="dialog"][aria-modal="true"]');
    if (!dialog) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeActiveDialog();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )).filter(element => element.getAttribute('aria-hidden') !== 'true');
    if (!focusable.length) {
      event.preventDefault();
      dialog.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && (document.activeElement === first || !dialog.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (document.activeElement === last || !dialog.contains(document.activeElement))) {
      event.preventDefault();
      first.focus();
    }
  }

  private captureModalTrigger(): void {
    this.modalTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  }

  private focusDialogOnOpen(): void {
    window.setTimeout(() => {
      const dialog = document.querySelector<HTMLElement>('[role="dialog"][aria-modal="true"]');
      const first = dialog?.querySelector<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
      (first ?? dialog)?.focus();
    });
  }

  private restoreModalTrigger(): void {
    const trigger = this.modalTrigger;
    this.modalTrigger = null;
    window.setTimeout(() => trigger?.focus());
  }

  private closeActiveDialog(): void {
    if (this.showMovimientoModal()) this.cerrarModalMovimiento();
    else if (this.showProductoModal()) this.cerrarModalProducto();
    else if (this.showEditModal()) this.cerrarModalEdicion();
    else if (this.showInactivarModal()) this.cerrarModalInactivar();
  }
}

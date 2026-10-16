import { Component, OnInit, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import {
  InventarioItem,
  ResumenInventario,
  Categoria,
  MovimientoRequest,
  MovimientoResponse,
  ProductoRequest
} from '../../models/inventario.model';

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './inventario.component.html',
  styleUrl: './inventario.component.scss'
})
export class InventarioComponent implements OnInit {
  protected readonly Number = Number;

  // Datos principales
  inventario = signal<InventarioItem[]>([]);
  resumen = signal<ResumenInventario>({
    totalProductos: 0,
    stockBajo: 0,
    sinStock: 0,
    totalUnidades: 0,
    movimientosHoy: 0
  });
  categorias = signal<Categoria[]>([]);
  movimientosHistorial = signal<MovimientoResponse[]>([]);

  // Filtros
  searchTerm = signal('');
  selectedCategoriaId = signal<number | null>(null);
  selectedStockStatus = signal<string>('TODOS');
  selectedEstadoFilter = signal<'TODOS' | 'ACTIVOS' | 'INACTIVOS'>('TODOS');
  isLoading = signal(false);

  // Lista filtrada reactiva en cliente según estado activo/inactivo
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

  // Lista de productos activos para selección en movimientos
  productosParaMovimiento = computed(() => {
    return this.inventario().filter((item) => item.estado !== false);
  });

  // Modales
  showMovimientoModal = signal(false);
  showProductoModal = signal(false);
  showKardexModal = signal(false);
  showEditModal = signal(false);
  showInactivarModal = signal(false);

  // Formulario de Movimiento
  selectedItemForMov = signal<InventarioItem | null>(null);
  movTipo = signal<'ENTRADA' | 'SALIDA' | 'AJUSTE'>('ENTRADA');
  movCantidad = signal<number>(10);
  movReferencia = signal<string>('');
  movError = signal<string | null>(null);
  movLoading = signal(false);

  // Formulario de Nuevo Producto
  nuevoCodigo = signal('');
  nuevoNombre = signal('');
  nuevoCategoriaId = signal<number | null>(null);
  nuevoPrecio = signal<number>(10.0);
  nuevoStockMinimo = signal<number>(10);
  nuevoStockInicial = signal<number>(20);
  nuevoUbicacion = signal('Pasillo 01 - Góndola A');
  prodError = signal<string | null>(null);
  prodLoading = signal(false);

  // Formulario de Edición de Producto
  editIdProducto = signal<number | null>(null);
  editCodigo = signal('');
  editNombre = signal('');
  editCategoriaId = signal<number | null>(null);
  editPrecio = signal<number>(0);
  editStockMinimo = signal<number>(0);
  editUbicacion = signal('');
  editError = signal<string | null>(null);
  editLoading = signal(false);

  // Inactivación de Producto
  itemParaInactivar = signal<InventarioItem | null>(null);
  inactivarLoading = signal(false);
  inactivarError = signal<string | null>(null);

  constructor(
    public authService: AuthService,
    private inventarioService: InventarioService
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
    this.cargarCategorias();
    this.cargarDatos();
  }

  cargarDatos(): void {
    const sedeId = this.authService.activeSedeId() || 1;
    this.isLoading.set(true);

    this.inventarioService.getResumen(sedeId).subscribe({
      next: (res) => this.resumen.set(res),
      error: (err) => console.error('Error cargando resumen', err)
    });

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

  // --- Manejo de Movimientos ---
  abrirModalMovimiento(item?: InventarioItem, tipo?: 'ENTRADA' | 'SALIDA' | 'AJUSTE'): void {
    if (item && item.estado === false) {
      return; // No permitir registrar movimientos en productos inactivos
    }
    const activos = this.productosParaMovimiento();
    this.selectedItemForMov.set(item || (activos[0] || null));
    if (tipo) this.movTipo.set(tipo);
    this.movCantidad.set(10);
    this.movReferencia.set('');
    this.movError.set(null);
    this.showMovimientoModal.set(true);
  }

  cerrarModalMovimiento(): void {
    this.showMovimientoModal.set(false);
  }

  guardarMovimiento(): void {
    const item = this.selectedItemForMov();
    if (!item) return;

    if (item.estado === false) {
      this.movError.set('El producto se encuentra inactivo. Reactívelo para operar con él.');
      return;
    }

    if (this.movCantidad() <= 0) {
      this.movError.set('La cantidad debe ser mayor a 0');
      return;
    }

    if (this.movTipo() === 'SALIDA' && this.movCantidad() > item.stockActual) {
      this.movError.set(`Stock insuficiente. Disponible: ${item.stockActual}`);
      return;
    }

    this.movLoading.set(true);
    this.movError.set(null);

    const req: MovimientoRequest = {
      idSede: this.authService.activeSedeId() || 1,
      idProducto: item.idProducto,
      tipo: this.movTipo(),
      cantidad: this.movCantidad(),
      referencia: this.movReferencia() || `Movimiento de ${this.movTipo()}`
    };

    this.inventarioService.registrarMovimiento(req).subscribe({
      next: () => {
        this.movLoading.set(false);
        this.cerrarModalMovimiento();
        this.cargarDatos();
      },
      error: (err) => {
        this.movLoading.set(false);
        this.movError.set(err.error?.error || 'Error al registrar el movimiento');
      }
    });
  }

  // --- Manejo de Edición de Producto ---
  abrirModalEdicion(item: InventarioItem): void {
    this.editIdProducto.set(item.idProducto);
    this.editCodigo.set(item.codigoProducto);
    this.editNombre.set(item.nombreProducto);
    this.editCategoriaId.set(item.idCategoria);
    this.editPrecio.set(item.precio);
    this.editStockMinimo.set(item.stockMinimo);
    this.editUbicacion.set(item.ubicacion);
    this.editError.set(null);
    this.showEditModal.set(true);
  }

  cerrarModalEdicion(): void {
    this.showEditModal.set(false);
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
      idCategoria: this.editCategoriaId()!,
      precio: this.editPrecio(),
      stockMinimo: this.editStockMinimo(),
      idSede: this.authService.activeSedeId() || 1,
      ubicacion: this.editUbicacion().trim()
    };

    this.inventarioService.actualizarProducto(id, req).subscribe({
      next: () => {
        this.editLoading.set(false);
        this.cerrarModalEdicion();
        this.cargarDatos();
      },
      error: (err) => {
        this.editLoading.set(false);
        this.editError.set(err.error?.error || 'Error al actualizar el producto');
      }
    });
  }

  // --- Manejo de Inactivación / Reactivación ---
  abrirModalInactivar(item: InventarioItem): void {
    this.itemParaInactivar.set(item);
    this.inactivarError.set(null);
    this.showInactivarModal.set(true);
  }

  cerrarModalInactivar(): void {
    this.showInactivarModal.set(false);
    this.itemParaInactivar.set(null);
  }

  confirmarInactivacion(): void {
    const item = this.itemParaInactivar();
    if (!item) return;

    this.inactivarLoading.set(true);
    this.inactivarError.set(null);

    this.inventarioService.cambiarEstadoProducto(item.idProducto, false).subscribe({
      next: () => {
        this.inactivarLoading.set(false);
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
    this.inventarioService.cambiarEstadoProducto(item.idProducto, true).subscribe({
      next: () => {
        this.cargarDatos();
      },
      error: (err) => {
        console.error('Error al reactivar el producto', err);
      }
    });
  }

  // --- Manejo de Nuevo Producto ---
  abrirModalProducto(): void {
    this.nuevoCodigo.set('TOT-NVO-' + Math.floor(100 + Math.random() * 900));
    this.nuevoNombre.set('');
    this.nuevoPrecio.set(9.90);
    this.nuevoStockMinimo.set(10);
    this.nuevoStockInicial.set(25);
    this.nuevoUbicacion.set('Pasillo 02 - Góndola Central');
    this.prodError.set(null);
    this.showProductoModal.set(true);
  }

  cerrarModalProducto(): void {
    this.showProductoModal.set(false);
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
      idCategoria: this.nuevoCategoriaId()!,
      precio: this.nuevoPrecio(),
      stockMinimo: this.nuevoStockMinimo(),
      idSede: this.authService.activeSedeId() || 1,
      stockInicial: this.nuevoStockInicial(),
      ubicacion: this.nuevoUbicacion()
    };

    this.inventarioService.crearProducto(req).subscribe({
      next: () => {
        this.prodLoading.set(false);
        this.cerrarModalProducto();
        this.cargarDatos();
      },
      error: (err) => {
        this.prodLoading.set(false);
        this.prodError.set(err.error?.error || 'Error al guardar el producto');
      }
    });
  }

  // --- Kardex ---
  abrirKardex(): void {
    const sedeId = this.authService.activeSedeId() || 1;
    this.inventarioService.getMovimientos(sedeId).subscribe({
      next: (list) => {
        this.movimientosHistorial.set(list);
        this.showKardexModal.set(true);
      }
    });
  }

  cerrarKardex(): void {
    this.showKardexModal.set(false);
  }
}

import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, effect, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import { InventarioItem, OperacionInventario, ResumenInventario } from '../../models/inventario.model';
import { DataSyncService } from '../../services/data-sync.service';
import { dateKeyForLimaDateTime, dateKeyInLima, formatDateTimeLima, shiftDateKeyInLima } from '../../utils/date-time-lima';

interface DailyOperations {
  date: string;
  key: string;
  label: string;
  income: number;
  expense: number;
}
interface PurchaseSummary { idCompra: number; referencia: string; proveedor: string; estado: string; fecha: string; detalles: unknown[]; }
interface SupplierSummary { estado: boolean; }

@Component({
  selector: 'app-dashboard', standalone: true, imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html', styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  loading = signal(true);
  error = signal('');
  movementError = signal('');
  trendLoading = signal(false);
  items = signal<InventarioItem[]>([]);
  incomes = signal<OperacionInventario[]>([]);
  expenses = signal<OperacionInventario[]>([]);
  purchases = signal<PurchaseSummary[]>([]);
  activeSuppliers = signal(0);
  pendingPurchases = computed(() => this.purchases().filter(purchase => purchase.estado === 'PENDIENTE').length);
  receivedPurchases = computed(() => this.purchases().filter(purchase => purchase.estado === 'RECIBIDA').length);
  summary = signal<ResumenInventario>({ totalProductos: 0, stockBajo: 0, sinStock: 0, totalUnidades: 0, movimientosHoy: 0 });
  activeProducts = computed(() => this.items().filter(item => item.estado).length);
  rangeDays = signal<7 | 30>(7);
  siteName = signal('Sede asignada');
  todayLabel = new Intl.DateTimeFormat('es-PE', { dateStyle: 'long', timeZone: 'America/Lima' }).format(new Date());
  private initialized = false;
  private lastLoadedSiteId = 0;
  private stopSync?: () => void;

  stockDistribution = computed(() => {
    const active = this.items().filter(item => item.estado);
    const counts = [
      { key: 'OPTIMO', label: 'Stock óptimo', color: '#16824c', count: active.filter(item => item.estadoStock === 'OPTIMO').length },
      { key: 'BAJO_STOCK', label: 'Stock bajo', color: '#ad6f10', count: active.filter(item => item.estadoStock === 'BAJO_STOCK').length },
      { key: 'AGOTADO', label: 'Agotados', color: '#b23d34', count: active.filter(item => item.estadoStock === 'AGOTADO').length }
    ];
    const total = counts.reduce((sum, item) => sum + item.count, 0);
    let passed = 0;
    return counts.map(item => {
      const percent = total ? item.count / total * 100 : 0;
      const segment = { ...item, percent, offset: -passed };
      passed += percent;
      return segment;
    });
  });
  totalClassified = computed(() => this.stockDistribution().reduce((sum, part) => sum + part.count, 0));
  dailyOperations = computed(() => this.buildDailyOperations());
  maxDailyOperations = computed(() => Math.max(1, ...this.dailyOperations().flatMap(day => [day.income, day.expense])));
  totalPeriodOperations = computed(() => this.incomes().length + this.expenses().length);
  recentOperations = computed(() => [...this.incomes(), ...this.expenses()]
    .sort((left, right) => new Date(right.fecha).getTime() - new Date(left.fecha).getTime())
    .slice(0, 8));
  alerts = computed(() => this.items()
    .filter(item => item.estado && item.estadoStock !== 'OPTIMO')
    .sort((left, right) => left.stockActual - right.stockActual));
  isPurchasingRole = computed(() => this.auth.hasAnyRole('ENCARGADO_COMPRAS'));
  isWarehouseRole = computed(() => this.auth.hasAnyRole('OPERADOR_ALMACEN'));
  isSalesRole = computed(() => this.auth.hasAnyRole('VENDEDOR'));
  roleTitle = computed(() => ({
    ADMINISTRADOR: 'Resumen general autorizado', JEFE_INVENTARIO: 'Supervisión de inventario',
    OPERADOR_ALMACEN: 'Actividad del almacén', VENDEDOR: 'Disponibilidad y salidas',
    ENCARGADO_COMPRAS: 'Abastecimiento y compras'
  }[this.auth.currentUser()?.rol?.toUpperCase() ?? ''] ?? 'Panel de control'));

  constructor(private api: InventarioService, private http: HttpClient, public auth: AuthService, private sync: DataSyncService) {
    effect(() => {
      const siteId = this.currentSiteId();
      if (this.initialized && siteId > 0 && siteId !== this.lastLoadedSiteId) {
        this.lastLoadedSiteId = siteId;
        this.refresh();
      }
    });
  }

  ngOnInit(): void {
    this.stopSync = this.sync.listen(['all', 'inventory', 'operations', 'supply'], () => this.refresh());
    this.initialized = true;
    this.lastLoadedSiteId = this.currentSiteId();
    this.refresh();
  }

  ngOnDestroy(): void { this.stopSync?.(); }

  refresh(): void {
    const siteId = this.currentSiteId();
    if (!siteId) {
      this.error.set('No hay una sede asignada para consultar el inventario.');
      this.loading.set(false);
      this.trendLoading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set('');
    if (this.isPurchasingRole()) {
      const params = new HttpParams().set('sedeId', String(siteId));
      forkJoin({
        sites: this.api.getSedes(),
        purchases: this.http.get<PurchaseSummary[]>('/api/compras', { params }),
        suppliers: this.http.get<SupplierSummary[]>('/api/proveedores', { params: new HttpParams().set('activos', 'true') })
      }).subscribe({
        next: result => {
          this.siteName.set(result.sites.find(site => site.idSede === siteId)?.nombre ?? 'Sede asignada');
          this.purchases.set(result.purchases);
          this.activeSuppliers.set(result.suppliers.filter(supplier => supplier.estado).length);
          this.loading.set(false);
        },
        error: err => { this.error.set(err?.error?.error || 'No se pudieron cargar los datos de abastecimiento.'); this.loading.set(false); }
      });
      this.trendLoading.set(false);
      return;
    }
    forkJoin({
      summary: this.auth.hasAnyRole('ADMINISTRADOR', 'JEFE_INVENTARIO') ? this.api.getResumen(siteId) : of(null),
      items: this.api.getInventario(siteId),
      sites: this.api.getSedes()
    }).subscribe({
      next: result => {
        this.summary.set(result.summary ?? this.summaryFromItems(result.items));
        this.items.set(result.items);
        this.siteName.set(result.sites.find(site => site.idSede === siteId)?.nombre ?? 'Sede asignada');
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.error || 'No se pudieron cargar los indicadores de inventario.');
        this.loading.set(false);
      }
    });
    this.loadMovementTrend();
  }

  private currentSiteId(): number {
    return this.auth.activeSedeId() || this.auth.currentUser()?.idSede || 0;
  }

  setRange(days: 7 | 30): void {
    if (this.rangeDays() === days) return;
    this.rangeDays.set(days);
    this.loadMovementTrend();
  }

  percentLabel(value: number): string {
    return `${Math.round(value)}%`;
  }

  formatDate(value: string | null | undefined): string { return formatDateTimeLima(value); }

  barHeight(value: number): string {
    return `${value === 0 ? 0 : Math.max(4, value / this.maxDailyOperations() * 100)}%`;
  }

  private loadMovementTrend(): void {
    if (this.isPurchasingRole()) { this.trendLoading.set(false); return; }
    const toText = dateKeyInLima();
    const fromText = shiftDateKeyInLima(toText, 1 - this.rangeDays());
    this.trendLoading.set(true);
    this.movementError.set('');
    forkJoin({
      incomes: this.auth.hasAnyRole('VENDEDOR') ? of([]) : this.api.getOperaciones('ingresos', this.currentSiteId(), undefined, fromText, toText),
      expenses: this.api.getOperaciones('egresos', this.currentSiteId(), undefined, fromText, toText)
    }).subscribe({
      next: result => {
        this.incomes.set(result.incomes);
        this.expenses.set(result.expenses);
        this.trendLoading.set(false);
      },
      error: err => {
        this.movementError.set(err?.error?.error || 'No se pudieron cargar los movimientos del periodo.');
        this.trendLoading.set(false);
      }
    });
  }

  private summaryFromItems(items: InventarioItem[]): ResumenInventario {
    const active = items.filter(item => item.estado);
    return {
      totalProductos: active.length,
      stockBajo: active.filter(item => item.estadoStock === 'BAJO_STOCK').length,
      sinStock: active.filter(item => item.estadoStock === 'AGOTADO').length,
      totalUnidades: active.reduce((total, item) => total + item.stockActual, 0),
      movimientosHoy: 0
    };
  }

  private buildDailyOperations(): DailyOperations[] {
    const countByDate = (operations: OperacionInventario[]) => {
      const counts = new Map<string, number>();
      for (const operation of operations) {
        const key = operation.fecha ? dateKeyForLimaDateTime(operation.fecha) : '';
        if (key) counts.set(key, (counts.get(key) ?? 0) + 1);
      }
      return counts;
    };
    const incomeByDate = countByDate(this.incomes());
    const expenseByDate = countByDate(this.expenses());
    const todayKey = dateKeyInLima();
    const [year, month, day] = todayKey.split('-').map(Number);
    return Array.from({ length: this.rangeDays() }, (_, index) => {
      const date = new Date(Date.UTC(year, month - 1, day - (this.rangeDays() - index - 1), 12));
      const key = dateKeyInLima(date);
      return {
        date: new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', timeZone: 'America/Lima' }).format(date),
        key,
        label: new Intl.DateTimeFormat('es-PE', { dateStyle: 'full', timeZone: 'America/Lima' }).format(date),
        income: incomeByDate.get(key) ?? 0,
        expense: expenseByDate.get(key) ?? 0
      };
    });
  }

}

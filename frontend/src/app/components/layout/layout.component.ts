import { Component, HostListener, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import { Sede, User } from '../../models/inventario.model';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss'
})
export class LayoutComponent implements OnInit {
  sedes = signal<Sede[]>([]);
  activeSedeId = signal<number>(0);
  currentUser = signal<User | null>(null);
  navCollapsed = signal(false);
  mobileMenuOpen = signal(false);

  constructor(
    public authService: AuthService,
    private inventarioService: InventarioService,
    public themeService: ThemeService
  ) {}

  ngOnInit(): void {
    this.currentUser.set(this.authService.currentUser());
    this.activeSedeId.set(this.authService.activeSedeId() || 0);

    if (!this.authService.isGlobalAdmin()) {
      const user = this.authService.currentUser();
      this.sedes.set(user?.idSede ? [{
        idSede: user.idSede,
        nombre: user.nombreSede || 'Sede asignada',
        direccion: '',
        estado: true
      }] : []);
      this.activeSedeId.set(user?.idSede ?? 0);
      if (user?.idSede) this.authService.setActiveSede(user.idSede);
      return;
    }

    this.inventarioService.getSedes().subscribe({
      next: (list) => {
        const user = this.authService.currentUser();
        const allowedIds = user?.idSedesAutorizadas ?? (user?.idSede ? [user.idSede] : []);
        const allowed = list.filter(site => allowedIds.includes(site.idSede));
        this.sedes.set(allowed);
        const active = this.authService.activeSedeId();
        const fallback = user?.idSede && allowedIds.includes(user.idSede) ? user.idSede : allowed[0]?.idSede;
        this.activeSedeId.set(active && allowedIds.includes(active) ? active : fallback ?? 0);
        if (this.activeSedeId()) this.authService.setActiveSede(this.activeSedeId());
      }
    });
  }

  onSedeChange(sedeId: number): void {
    if (!this.authService.isGlobalAdmin()) return;
    const next = Number(sedeId);
    if (!this.sedes().some(site => site.idSede === next) || next === this.activeSedeId()) return;
    this.authService.setActiveSede(next);
    if (this.authService.activeSedeId() !== next) return;
    this.activeSedeId.set(next);
    window.location.reload();
  }

  get isGlobalAdmin(): boolean {
    return this.authService.isGlobalAdmin();
  }

  get activeSedeName(): string {
    const user = this.currentUser();
    return this.sedes().find(site => site.idSede === this.activeSedeId())?.nombre
      || user?.nombreSede
      || 'Sede asignada';
  }

  logout(): void {
    this.authService.logout();
  }

  toggleNav(): void {
    this.navCollapsed.update(collapsed => !collapsed);
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update(open => !open);
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  brandRoute(): string {
    if (this.authService.hasAnyRole('ADMINISTRADOR', 'JEFE_INVENTARIO', 'OPERADOR_ALMACEN', 'VENDEDOR', 'ENCARGADO_COMPRAS')) return '/dashboard';
    if (this.authService.hasAnyRole('OPERADOR_ALMACEN', 'VENDEDOR')) return '/inventario';
    if (this.authService.hasAnyRole('ENCARGADO_COMPRAS')) return '/ingresos';
    return '/login';
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMobileMenu();
  }
}

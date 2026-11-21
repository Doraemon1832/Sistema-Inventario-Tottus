import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import { Sede, UsuarioAdmin } from '../../models/inventario.model';

@Component({ selector: 'app-usuarios', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './usuarios.component.html', styleUrl: './usuarios.component.scss' })
export class UsuariosComponent implements OnInit {
  users = signal<UsuarioAdmin[]>([]); sites = signal<Sede[]>([]); busy = signal(false); error = signal(''); message = signal('');
  username = ''; role = 'OPERADOR_ALMACEN'; password = ''; selectedSites: number[] = []; accessGlobal = false;
  resetTarget: number | null = null; resetPassword = '';
  filterSiteId: number | null = null; filterRole = '';
  editingUser: number | null = null; editRole = ''; editSites: number[] = []; editGlobal = false;
  readonly roleOptions = ['JEFE_INVENTARIO', 'OPERADOR_ALMACEN', 'VENDEDOR', 'ENCARGADO_COMPRAS'];
  constructor(private http: HttpClient, private inventory: InventarioService, public auth: AuthService) {}
  get canGrantGlobal(): boolean { return this.auth.currentUser()?.accesoGlobal === true; }
  ngOnInit(): void { this.inventory.getSedes().subscribe({ next: sites => this.sites.set(sites) }); this.load(); }
  load(): void {
    let params = new HttpParams();
    if (this.filterSiteId) params = params.set('sedeId', this.filterSiteId);
    if (this.filterRole) params = params.set('rol', this.filterRole);
    this.http.get<UsuarioAdmin[]>('/api/admin/usuarios', { params }).subscribe({ next: users => this.users.set(users), error: err => this.error.set(err?.error?.error || 'No se pudieron cargar las cuentas autorizadas.') });
  }
  siteName(id: number): string { return this.sites().find(site => site.idSede === id)?.nombre || `Sede ${id}`; }
  siteNames(ids: number[]): string { return ids.map(id => this.siteName(id)).join(', '); }
  beginEdit(user: UsuarioAdmin): void {
    this.editingUser = user.idUsuario; this.editRole = user.rol;
    this.editSites = [...user.idSedesAutorizadas]; this.editGlobal = user.accesoGlobal;
  }
  toggleEditSite(id: number, checked: boolean): void {
    this.editSites = checked ? [...new Set([...this.editSites, id])] : this.editSites.filter(siteId => siteId !== id);
  }
  saveAccess(user: UsuarioAdmin): void {
    if (!this.editSites.length) { this.error.set('Asigna al menos una sede.'); return; }
    this.http.put<UsuarioAdmin>(`/api/admin/usuarios/${user.idUsuario}/acceso`, {
      rol: this.editRole, idSedes: this.editSites, accesoGlobal: this.editGlobal
    }).subscribe({
      next: () => { this.editingUser = null; this.message.set(`Permisos actualizados para ${user.usuario}.`); this.load(); },
      error: err => this.error.set(err?.error?.error || 'No se pudieron actualizar los permisos.')
    });
  }
  create(): void {
    const idSedes = [...new Set(this.selectedSites)];
    if (!/^[a-z0-9]+(?:[._-][a-z0-9]+)*$/.test(this.username.trim().toLowerCase()) || this.password.length < 12 || !idSedes.length) {
      this.error.set('Indica un usuario válido, al menos una sede y una contraseña inicial de 12 caracteres o más.'); return;
    }
    this.busy.set(true); this.error.set(''); this.message.set('');
    this.http.post<UsuarioAdmin>('/api/admin/usuarios', { usuario: this.username.trim().toLowerCase(), rol: this.role,
      idSedes, passwordInicial: this.password, accesoGlobal: this.accessGlobal }).subscribe({
      next: created => { this.busy.set(false); this.username = ''; this.password = ''; this.selectedSites = []; this.accessGlobal = false;
        this.message.set(`Cuenta ${created.usuario} creada. La contraseña no se vuelve a mostrar; comunícala por un canal seguro.`); this.load(); },
      error: err => { this.busy.set(false); this.error.set(err?.error?.error || 'No se pudo crear la cuenta.'); }
    });
  }
  toggle(user: UsuarioAdmin): void {
    this.http.patch<UsuarioAdmin>(`/api/admin/usuarios/${user.idUsuario}/estado?activo=${!user.estado}`, {}).subscribe({ next: () => this.load(), error: err => this.error.set(err?.error?.error || 'No se pudo cambiar el estado de la cuenta.') });
  }
  reset(user: UsuarioAdmin): void {
    if (this.resetPassword.length < 12) { this.error.set('La nueva contraseña debe tener al menos 12 caracteres.'); return; }
    this.http.put(`/api/admin/usuarios/${user.idUsuario}/password`, { password: this.resetPassword }).subscribe({
      next: () => { this.resetTarget = null; this.resetPassword = ''; this.message.set(`Contraseña restablecida para ${user.usuario}; deberá cambiarla al iniciar sesión.`); this.load(); },
      error: err => this.error.set(err?.error?.error || 'No se pudo restablecer la contraseña.')
    });
  }
}

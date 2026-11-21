import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { User, Sede } from '../models/inventario.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api/auth';

  currentUser = signal<User | null>(this.getStoredUser());
  activeSedeId = signal<number | null>(this.getStoredSedeId());

  constructor(private http: HttpClient, private router: Router) {}

  login(credentials: { usuario: string; password: string }): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/login`, credentials).pipe(
      tap((res) => {
        if (res && res.token) {
          localStorage.setItem('tottus_token', res.token);
          localStorage.setItem('tottus_user', JSON.stringify(res));
          this.currentUser.set(res);
          if (res.idSede) {
            this.setActiveSede(res.idSede);
          }
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem('tottus_token');
    localStorage.removeItem('tottus_user');
    localStorage.removeItem('tottus_active_sede');
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem('tottus_token');
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  hasAnyRole(...roles: string[]): boolean {
    const currentRole = this.currentUser()?.rol?.toUpperCase();
    return !!currentRole && roles.some(role => role.toUpperCase() === currentRole);
  }

  isGlobalAdmin(): boolean {
    const user = this.currentUser();
    return user?.rol?.toUpperCase() === 'ADMINISTRADOR' && user.accesoGlobal === true;
  }

  setActiveSede(sedeId: number): void {
    const user = this.currentUser();
    const authorized = this.isGlobalAdmin()
      ? (user?.idSedesAutorizadas ?? (user?.idSede ? [user.idSede] : []))
      : (user?.idSede ? [user.idSede] : []);
    if (!authorized.includes(sedeId)) return;
    localStorage.setItem('tottus_active_sede', sedeId.toString());
    this.activeSedeId.set(sedeId);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/change-password`, { currentPassword, newPassword });
  }

  private getStoredUser(): User | null {
    const raw = localStorage.getItem('tottus_user');
    if (!raw) return null;
    try { return JSON.parse(raw) as User; }
    catch { localStorage.removeItem('tottus_user'); localStorage.removeItem('tottus_token'); return null; }
  }

  private getStoredSedeId(): number | null {
    const raw = localStorage.getItem('tottus_active_sede');
    if (raw) {
      const selected = Number.parseInt(raw, 10);
      const user = this.getStoredUser();
      const assigned = user?.idSede;
      const allowed = user?.rol?.toUpperCase() === 'ADMINISTRADOR' && user.accesoGlobal === true
        ? (user.idSedesAutorizadas ?? (assigned ? [assigned] : []))
        : (assigned ? [assigned] : []);
      return allowed.includes(selected) ? selected : assigned ?? null;
    }
    const user = this.getStoredUser();
    return user?.idSede ?? null;
  }
}

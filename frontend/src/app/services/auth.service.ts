import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { User, Sede } from '../models/inventario.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';

  currentUser = signal<User | null>(this.getStoredUser());
  activeSedeId = signal<number | null>(this.getStoredSedeId());

  constructor(private http: HttpClient, private router: Router) {}

  login(credentials: { usuario: string; password: string }): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/login`, credentials).pipe(
      tap((res) => {
        if (res && res.token) {
          localStorage.setItem('tottus_token', res.token);
          localStorage.setItem('tottus_user', JSON.stringify(res));
          if (res.idSede) {
            this.setActiveSede(res.idSede);
          }
          this.currentUser.set(res);
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

  setActiveSede(sedeId: number): void {
    localStorage.setItem('tottus_active_sede', sedeId.toString());
    this.activeSedeId.set(sedeId);
  }

  private getStoredUser(): User | null {
    const raw = localStorage.getItem('tottus_user');
    return raw ? JSON.parse(raw) : null;
  }

  private getStoredSedeId(): number | null {
    const raw = localStorage.getItem('tottus_active_sede');
    if (raw) return parseInt(raw, 10);
    const user = this.getStoredUser();
    return user?.idSede || 1;
  }
}

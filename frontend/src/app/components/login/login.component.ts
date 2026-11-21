import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  usuario = signal('');
  password = signal('');
  showPassword = signal(false);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(private authService: AuthService, private router: Router) {
    if (this.authService.isAuthenticated()) {
      this.router.navigate([this.authService.currentUser()?.cambioPasswordRequerido ? '/cambiar-password' : '/dashboard']);
    }
  }

  togglePassword(): void {
    this.showPassword.update((val) => !val);
  }

  onSubmit(): void {
    if (!this.usuario() || !this.password()) {
      this.errorMessage.set('Por favor ingrese su usuario y contraseña');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.login({ usuario: this.usuario(), password: this.password() }).subscribe({
      next: (user) => {
        this.isLoading.set(false);
        this.router.navigate([user.cambioPasswordRequerido ? '/cambiar-password' : this.landingRoute(user.rol)]);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(
          err.error?.error || 'Usuario o contraseña incorrectos. Verifique sus credenciales.'
        );
      }
    });
  }

  private landingRoute(role: string): string {
    switch (role.toUpperCase()) {
      case 'ENCARGADO_COMPRAS': return '/dashboard';
      case 'ADMINISTRADOR':
      case 'JEFE_INVENTARIO':
      case 'OPERADOR_ALMACEN':
      case 'VENDEDOR': return '/dashboard';
      default: return '/inventario';
    }
  }
}

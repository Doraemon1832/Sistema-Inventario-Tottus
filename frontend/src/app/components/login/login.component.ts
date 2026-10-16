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
  usuario = signal('gerencia_admin');
  password = signal('admin123');
  showPassword = signal(false);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(private authService: AuthService, private router: Router) {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/inventario']);
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
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/inventario']);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(
          err.error?.error || 'Usuario o contraseña incorrectos. Verifique sus credenciales.'
        );
      }
    });
  }
}

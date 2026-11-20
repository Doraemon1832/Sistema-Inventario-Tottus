import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-cambiar-password', standalone: true, imports: [CommonModule, FormsModule],
  templateUrl: './cambiar-password.component.html', styleUrl: './cambiar-password.component.scss'
})
export class CambiarPasswordComponent {
  currentPassword = ''; newPassword = ''; confirmPassword = '';
  busy = signal(false); error = signal(''); message = signal('');
  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error.set(''); this.message.set('');
    if (this.newPassword.length < 12 || this.newPassword.length > 72) {
      this.error.set('La nueva contraseña debe tener entre 12 y 72 caracteres.'); return;
    }
    if (this.newPassword !== this.confirmPassword) { this.error.set('Las contraseñas nuevas no coinciden.'); return; }
    this.busy.set(true);
    this.auth.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        const user = this.auth.currentUser();
        if (user) {
          const refreshed = { ...user, cambioPasswordRequerido: false };
          localStorage.setItem('tottus_user', JSON.stringify(refreshed)); this.auth.currentUser.set(refreshed);
        }
        this.busy.set(false); this.message.set('Contraseña actualizada.');
        setTimeout(() => this.router.navigate(['/dashboard']), 500);
      },
      error: err => { this.busy.set(false); this.error.set(err?.error?.error || 'No se pudo actualizar la contraseña.'); }
    });
  }
}

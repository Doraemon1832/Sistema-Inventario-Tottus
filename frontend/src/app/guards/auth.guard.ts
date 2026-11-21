import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    if (authService.currentUser()?.cambioPasswordRequerido && !state.url.startsWith('/cambiar-password'))
      return router.parseUrl('/cambiar-password');
    return true;
  }

  router.navigate(['/login']);
  return false;
};

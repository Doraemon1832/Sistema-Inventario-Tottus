import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const token = localStorage.getItem('tottus_token');

  if (token) {
    const authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(authReq).pipe(catchError((error) => {
      if (error.status === 401 && !req.url.includes('/api/auth/login')) {
        localStorage.removeItem('tottus_token');
        localStorage.removeItem('tottus_user');
        localStorage.removeItem('tottus_active_sede');
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }));
  }

  return next(req);
};

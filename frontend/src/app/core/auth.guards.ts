import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from './auth.service';
import { Role } from './models';

export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  return inject(AuthService).authenticated()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Solo UX: la autorización real es del backend. */
export const roleGuard = (...roles: Role[]): CanActivateFn => () => {
  if (inject(AuthService).hasRole(...roles)) return true;
  inject(MatSnackBar).open('No tiene permiso para acceder a esa sección', 'Cerrar', { duration: 4000 });
  return inject(Router).createUrlTree(['/dashboard']);
};

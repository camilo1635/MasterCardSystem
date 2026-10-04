import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

/** Muestra el mensaje de error del backend en un snackbar. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snack = inject(MatSnackBar);
  return next(req).pipe(
    catchError((e: HttpErrorResponse) => {
      const msg = e.status === 0 ? 'No hay conexión con el servidor' : e.error?.message ?? `Error ${e.status}`;
      snack.open(msg, 'Cerrar', { duration: 6000 });
      return throwError(() => e);
    }),
  );
};

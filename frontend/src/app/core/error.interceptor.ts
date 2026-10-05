import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, catchError, from, map, of, switchMap, tap, throwError } from 'rxjs';
import { API_URL } from './api.service';

/** El cuerpo de errores en respuestas blob (p. ej. PDF) llega como Blob: se lee su JSON. */
function backendMessage(e: HttpErrorResponse): Observable<string | undefined> {
  if (e.error instanceof Blob) {
    return from(e.error.text()).pipe(
      map(t => (JSON.parse(t) as { message?: string }).message),
      catchError(() => of(undefined)),
    );
  }
  return of(e.error?.message);
}

function fallback(status: number): string {
  if (status === 0) return 'No hay conexión con el servidor';
  if (status === 401) return 'Su sesión expiró. Inicie sesión de nuevo';
  if (status === 403) return 'No tiene permiso para realizar esta acción';
  return `Error ${status}`;
}

/**
 * Muestra el mensaje de error del backend en un snackbar. Va por fuera de authInterceptor, así que
 * solo ve el error final (tras el refresh/reintento). Las llamadas a /auth/* las muestra su propia pantalla.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snack = inject(MatSnackBar);
  const silent = req.url.startsWith(`${API_URL}/auth/`);
  return next(req).pipe(
    catchError((e: HttpErrorResponse) => {
      if (silent) return throwError(() => e);
      return backendMessage(e).pipe(
        tap(m => snack.open(m ?? fallback(e.status), 'Cerrar', { duration: 6000 })),
        switchMap(() => throwError(() => e)),
      );
    }),
  );
};

import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { API_URL } from './api.service';
import { AuthService } from './auth.service';

/** Añade el Bearer y, ante un 401, refresca la sesión y reintenta una sola vez. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isApi = req.url.startsWith(`${API_URL}/`);
  const isAuth = req.url.startsWith(`${API_URL}/auth/`);

  const withToken = (r: HttpRequest<unknown>) => {
    const token = auth.token();
    return isApi && !isAuth && token ? r.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : r;
  };

  return next(withToken(req)).pipe(
    catchError(e => {
      if (!(e instanceof HttpErrorResponse) || e.status !== 401 || !isApi || isAuth) return throwError(() => e);
      return auth.refresh().pipe(
        catchError(() => { auth.expire(); return throwError(() => e); }),
        switchMap(() => next(withToken(req))),
      );
    }),
  );
};

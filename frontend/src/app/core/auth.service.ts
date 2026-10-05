import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, of, shareReplay, tap } from 'rxjs';
import { ApiService } from './api.service';
import { AuthResponse, Role } from './models';

/** Sesión en memoria: el access token nunca se guarda en localStorage; la cookie de refresh restaura la sesión al recargar. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);
  private router = inject(Router);

  private session = signal<AuthResponse | null>(null);
  user = computed(() => this.session()?.user ?? null);
  role = computed(() => this.user()?.role ?? null);
  authenticated = computed(() => this.user() !== null);
  token = computed(() => this.session()?.accessToken ?? null);

  private inflight: Observable<AuthResponse> | null = null;

  login(username: string, password: string) {
    return this.api.login(username, password).pipe(tap(r => this.session.set(r)));
  }

  /** Una sola petición de refresh a la vez: las demás se suscriben a la misma. */
  refresh(): Observable<AuthResponse> {
    if (!this.inflight) {
      this.inflight = this.api.refresh().pipe(
        tap(r => this.session.set(r)),
        finalize(() => (this.inflight = null)),
        shareReplay(1),
      );
    }
    return this.inflight;
  }

  /** Intenta restaurar la sesión al arrancar; nunca falla. */
  restore() { return this.refresh().pipe(catchError(() => of(null))); }

  logout() {
    return this.api.logout().pipe(
      catchError(() => of(null)),
      finalize(() => { this.session.set(null); this.router.navigate(['/login']); }),
    );
  }

  /** Sesión inválida o vencida: limpia y manda a login conservando la ruta. */
  expire() {
    this.session.set(null);
    if (!this.router.url.startsWith('/login')) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
    }
  }

  hasRole(...roles: Role[]): boolean {
    const r = this.role();
    return r !== null && roles.includes(r);
  }
}

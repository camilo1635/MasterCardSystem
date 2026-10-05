import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { SHARED_IMPORTS } from '../core/shared';

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <form class="card login" (ngSubmit)="submit()">
      <h1>Iniciar sesión</h1>
      <mat-form-field><mat-label>Usuario</mat-label>
        <input matInput name="username" autocomplete="username" required [(ngModel)]="username"></mat-form-field>
      <mat-form-field><mat-label>Contraseña</mat-label>
        <input matInput name="password" type="password" autocomplete="current-password" required [(ngModel)]="password"></mat-form-field>
      @if (error()) { <p class="danger" role="alert">{{ error() }}</p> }
      <button mat-flat-button color="primary" type="submit" [disabled]="loading() || !username || !password">Entrar</button>
    </form>
  `,
  styles: [`
    .login { max-width: 380px; margin: 48px auto; display: flex; flex-direction: column; }
  `],
})
export class LoginComponent implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  username = '';
  password = '';
  loading = signal(false);
  error = signal('');

  ngOnInit() { if (this.auth.authenticated()) this.goNext(); }

  submit() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.auth.login(this.username.trim(), this.password).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: () => this.goNext(),
      error: (e: HttpErrorResponse) => {
        this.password = '';
        // 400 validación, 401 credenciales, 423 cuenta bloqueada: el backend trae el mensaje en español.
        this.error.set(e.status === 0 ? 'No hay conexión con el servidor' : e.error?.message ?? 'No se pudo iniciar sesión');
      },
    });
  }

  private goNext() {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    const safe = url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login');
    this.router.navigateByUrl(safe ? url : '/dashboard');
  }
}

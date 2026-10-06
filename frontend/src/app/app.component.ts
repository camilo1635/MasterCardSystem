import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ThemeService } from './core/theme.service';
import { AuthService } from './core/auth.service';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

interface NavLink { path: string; label: string; icon: string; }

const LINKS: NavLink[] = [
  { path: '/dashboard', label: 'Resumen', icon: 'dashboard' },
  { path: '/facturacion', label: 'Facturación', icon: 'receipt_long' },
  { path: '/clientes', label: 'Clientes y crédito', icon: 'groups' },
  { path: '/productos', label: 'Productos', icon: 'inventory_2' },
  { path: '/inventario', label: 'Inventario', icon: 'warehouse' },
  { path: '/compras', label: 'Compras', icon: 'local_shipping' },
  { path: '/contabilidad', label: 'Contabilidad', icon: 'account_balance' },
];

@Component({
  selector: 'app-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule, MatButtonModule],
  template: `
    <mat-toolbar class="top">
      <mat-icon>speaker</mat-icon>
      <span class="brand">MasterCard Sound</span>
      <span class="spacer"></span>
      @if (auth.user(); as u) {
        <span class="who">{{ u.username }}</span>
      }
      <button mat-icon-button (click)="theme.toggle()" [attr.aria-label]="theme.dark() ? 'Modo claro' : 'Modo oscuro'">
        <mat-icon>{{ theme.dark() ? 'light_mode' : 'dark_mode' }}</mat-icon>
      </button>
      @if (auth.authenticated()) {
        <button mat-icon-button (click)="logout()" aria-label="Cerrar sesión"><mat-icon>logout</mat-icon></button>
      }
    </mat-toolbar>
    <mat-sidenav-container>
      <mat-sidenav mode="side" [opened]="auth.authenticated()">
        <mat-nav-list>
          @for (l of links; track l.path) {
            <a mat-list-item [routerLink]="l.path" routerLinkActive="active">
              <mat-icon matListItemIcon>{{ l.icon }}</mat-icon>
              <span matListItemTitle>{{ l.label }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content><main><router-outlet /></main></mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100vh; }
    .brand { margin-left: 12px; }
    .spacer { flex: 1; }
    .who { margin-right: 8px; font-size: 14px; }
    .top { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    mat-sidenav-container { flex: 1; }
    mat-sidenav { width: 230px; }
    main { padding: 24px; max-width: 1280px; margin: 0 auto; }
    a.active { background: var(--app-active); }
  `],
})
export class AppComponent {
  theme = inject(ThemeService);
  auth = inject(AuthService);
  links = LINKS;

  logout() { this.auth.logout().subscribe(); }
}

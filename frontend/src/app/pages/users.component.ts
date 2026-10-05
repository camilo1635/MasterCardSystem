import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AppUser, ROLE_LABELS, Role } from '../core/models';
import { MatSnackBar, SHARED_IMPORTS } from '../core/shared';

@Component({
  selector: 'app-users',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <h1>Usuarios</h1>
    <div class="card">
      <h3>{{ editing() ? 'Editar usuario ' + editing()!.username : 'Nuevo usuario' }}</h3>
      <div class="row">
        @if (!editing()) {
          <mat-form-field><mat-label>Usuario (3-50)</mat-label><input matInput autocomplete="off" [(ngModel)]="username"></mat-form-field>
        }
        <mat-form-field><mat-label>{{ editing() ? 'Nueva contraseña (opcional)' : 'Contraseña (10-72)' }}</mat-label>
          <input matInput type="password" autocomplete="new-password" [(ngModel)]="password"></mat-form-field>
        <mat-form-field><mat-label>Rol</mat-label>
          <mat-select [(ngModel)]="role" [disabled]="isSelf()">
            @for (r of roles; track r) { <mat-option [value]="r">{{ labels[r] }}</mat-option> }
          </mat-select></mat-form-field>
        @if (editing()) { <mat-checkbox [(ngModel)]="active" [disabled]="isSelf()">Activo</mat-checkbox> }
        <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">Guardar</button>
        @if (editing()) { <button mat-button (click)="reset()">Cancelar</button> }
      </div>
      @if (isSelf()) { <p class="muted">No puede cambiar su propio rol ni desactivarse.</p> }
    </div>

    <div class="card">
      <table mat-table [dataSource]="items()">
        <ng-container matColumnDef="username"><th mat-header-cell *matHeaderCellDef>Usuario</th><td mat-cell *matCellDef="let u">{{ u.username }}</td></ng-container>
        <ng-container matColumnDef="role"><th mat-header-cell *matHeaderCellDef>Rol</th><td mat-cell *matCellDef="let u">{{ roleLabel(u.role) }}</td></ng-container>
        <ng-container matColumnDef="state"><th mat-header-cell *matHeaderCellDef>Estado</th>
          <td mat-cell *matCellDef="let u"><span [class.danger]="!u.active || u.locked">{{ u.active ? (u.locked ? 'Bloqueado' : 'Activo') : 'Inactivo' }}</span></td></ng-container>
        <ng-container matColumnDef="created"><th mat-header-cell *matHeaderCellDef>Creado</th><td mat-cell *matCellDef="let u">{{ u.createdAt | date:'dd/MM/yy HH:mm' }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let u">
            <button mat-icon-button aria-label="Editar usuario" (click)="edit(u)"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button aria-label="Eliminar usuario" [disabled]="u.id === auth.user()?.id" (click)="remove(u)"><mat-icon>delete</mat-icon></button>
          </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let r; columns: cols"></tr>
      </table>
    </div>
  `,
})
export class UsersComponent implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);
  auth = inject(AuthService);

  items = signal<AppUser[]>([]);
  editing = signal<AppUser | null>(null);
  saving = signal(false);
  username = '';
  password = '';
  role: Role = 'VENDEDOR';
  active = true;
  roles: Role[] = ['ADMIN', 'VENDEDOR', 'CONTADOR'];
  labels = ROLE_LABELS;
  cols = ['username', 'role', 'state', 'created', 'actions'];

  ngOnInit() { this.load(); }

  load() { this.api.users().subscribe(u => this.items.set(u)); }
  roleLabel(r: Role) { return this.labels[r]; }
  isSelf() { return this.editing()?.id === this.auth.user()?.id; }

  edit(u: AppUser) {
    this.editing.set(u);
    this.role = u.role;
    this.active = u.active;
    this.password = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  reset() { this.editing.set(null); this.username = ''; this.password = ''; this.role = 'VENDEDOR'; this.active = true; }

  save() {
    const e = this.editing();
    const done = (msg: string) => { this.saving.set(false); this.snack.open(msg, undefined, { duration: 2500 }); this.reset(); this.load(); };
    const fail = () => this.saving.set(false);
    this.saving.set(true);
    if (e) {
      const body = { role: this.role, active: this.active, ...(this.password ? { password: this.password } : {}) };
      this.api.updateUser(e.id, body).subscribe({ next: () => done('Usuario actualizado'), error: fail });
    } else {
      this.api.createUser({ username: this.username.trim(), password: this.password, role: this.role })
        .subscribe({ next: () => done('Usuario creado'), error: fail });
    }
  }

  remove(u: AppUser) {
    if (!confirm(`¿Eliminar al usuario ${u.username}?`)) return;
    this.api.deleteUser(u.id).subscribe(() => { this.snack.open('Usuario eliminado', undefined, { duration: 2500 }); this.load(); });
  }
}

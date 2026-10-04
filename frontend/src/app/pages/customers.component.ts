import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { CreditTransaction, Customer, CustomerCredit, Receivable } from '../core/models';
import { MatSnackBar, SHARED_IMPORTS } from '../core/shared';

const blank = (): Customer => ({ document: '', name: '', phone: '', email: '', address: '', creditLimit: 0, active: true });

@Component({
  selector: 'app-customers',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <h1>Clientes y crédito</h1>
    <mat-tab-group (selectedTabChange)="tab = $event.index; tab === 1 && loadReceivables()">
      <mat-tab label="Clientes">
        <div class="card" style="margin-top:16px">
          <h3>{{ form.id ? 'Editar cliente' : 'Nuevo cliente' }}</h3>
          <div class="row">
            <mat-form-field><mat-label>Documento / NIT</mat-label><input matInput [(ngModel)]="form.document"></mat-form-field>
            <mat-form-field style="flex: 2 1 260px"><mat-label>Nombre</mat-label><input matInput [(ngModel)]="form.name"></mat-form-field>
            <mat-form-field><mat-label>Teléfono</mat-label><input matInput [(ngModel)]="form.phone"></mat-form-field>
            <mat-form-field><mat-label>Email</mat-label><input matInput [(ngModel)]="form.email"></mat-form-field>
            <mat-form-field><mat-label>Cupo de crédito</mat-label><input matInput type="number" min="0" [(ngModel)]="form.creditLimit"></mat-form-field>
            <button mat-flat-button color="primary" (click)="save()">Guardar</button>
            @if (form.id) { <button mat-button (click)="form = blank()">Cancelar</button> }
          </div>
        </div>

        <div class="card">
          <mat-form-field style="width:100%"><mat-label>Buscar por nombre o documento</mat-label>
            <input matInput [ngModel]="q" (ngModelChange)="q = $event; load()"></mat-form-field>
          <table mat-table [dataSource]="customers()">
            <ng-container matColumnDef="document"><th mat-header-cell *matHeaderCellDef>Documento</th><td mat-cell *matCellDef="let c">{{ c.document }}</td></ng-container>
            <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Nombre</th><td mat-cell *matCellDef="let c">{{ c.name }}</td></ng-container>
            <ng-container matColumnDef="phone"><th mat-header-cell *matHeaderCellDef>Teléfono</th><td mat-cell *matCellDef="let c">{{ c.phone }}</td></ng-container>
            <ng-container matColumnDef="limit"><th mat-header-cell *matHeaderCellDef class="num">Cupo</th><td mat-cell *matCellDef="let c" class="num">{{ c.creditLimit | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
            <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let c">
                <button mat-button (click)="openCredit(c)">Historial de crédito</button>
                <button mat-icon-button (click)="edit(c)"><mat-icon>edit</mat-icon></button>
              </td></ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let r; columns: cols"></tr>
          </table>
        </div>

        @if (selected(); as s) {
          <div class="card">
            <h3>Crédito de {{ s.customer.name }}</h3>
            <div class="kpis">
              <div class="kpi"><div class="label">Cupo</div><div class="value">{{ s.customer.creditLimit | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
              <div class="kpi"><div class="label">Saldo adeudado</div><div class="value" [class.danger]="s.balance > 0">{{ s.balance | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
              <div class="kpi"><div class="label">Cupo disponible</div><div class="value">{{ s.available | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
            </div>
            <div class="row">
              <mat-form-field><mat-label>Valor del abono</mat-label><input matInput type="number" min="1" [(ngModel)]="payAmount"></mat-form-field>
              <mat-form-field><mat-label>Método</mat-label>
                <mat-select [(ngModel)]="payMethod"><mat-option value="EFECTIVO">Efectivo</mat-option><mat-option value="TRANSFERENCIA">Transferencia</mat-option><mat-option value="TARJETA">Tarjeta</mat-option></mat-select></mat-form-field>
              <mat-form-field style="flex: 2 1 220px"><mat-label>Nota</mat-label><input matInput [(ngModel)]="payNote"></mat-form-field>
              <button mat-flat-button color="primary" [disabled]="!payAmount || s.balance <= 0" (click)="pay(s)">Registrar abono</button>
            </div>
            <table mat-table [dataSource]="history()">
              <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Fecha</th><td mat-cell *matCellDef="let t">{{ t.createdAt | date:'dd/MM/yy HH:mm' }}</td></ng-container>
              <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Tipo</th><td mat-cell *matCellDef="let t">{{ t.type }}</td></ng-container>
              <ng-container matColumnDef="note"><th mat-header-cell *matHeaderCellDef>Detalle</th><td mat-cell *matCellDef="let t">{{ t.note }} {{ t.method }}</td></ng-container>
              <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef class="num">Valor</th>
                <td mat-cell *matCellDef="let t" class="num" [class.ok]="t.amount < 0" [class.danger]="t.amount > 0">{{ t.amount | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
              <ng-container matColumnDef="balance"><th mat-header-cell *matHeaderCellDef class="num">Saldo</th><td mat-cell *matCellDef="let t" class="num">{{ t.balance | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="histCols"></tr>
              <tr mat-row *matRowDef="let r; columns: histCols"></tr>
            </table>
          </div>
        }
      </mat-tab>

      <mat-tab label="Cartera por cobrar">
        <div class="card" style="margin-top:16px">
          @if (!receivables().length) { <p class="muted">No hay clientes con saldo pendiente.</p> }
          <table mat-table [dataSource]="receivables()">
            <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Cliente</th><td mat-cell *matCellDef="let r">{{ r.name }}</td></ng-container>
            <ng-container matColumnDef="document"><th mat-header-cell *matHeaderCellDef>Documento</th><td mat-cell *matCellDef="let r">{{ r.document }}</td></ng-container>
            <ng-container matColumnDef="phone"><th mat-header-cell *matHeaderCellDef>Teléfono</th><td mat-cell *matCellDef="let r">{{ r.phone }}</td></ng-container>
            <ng-container matColumnDef="balance"><th mat-header-cell *matHeaderCellDef class="num">Saldo</th><td mat-cell *matCellDef="let r" class="num danger">{{ r.balance | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="recCols"></tr>
            <tr mat-row *matRowDef="let r; columns: recCols"></tr>
          </table>
        </div>
      </mat-tab>
    </mat-tab-group>
  `,
})
export class CustomersComponent implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  customers = signal<Customer[]>([]);
  receivables = signal<Receivable[]>([]);
  selected = signal<CustomerCredit | null>(null);
  history = signal<CreditTransaction[]>([]);
  form: Customer = blank();
  blank = blank;
  q = '';
  tab = 0;
  payAmount = 0;
  payMethod = 'EFECTIVO';
  payNote = '';
  cols = ['document', 'name', 'phone', 'limit', 'actions'];
  histCols = ['date', 'type', 'note', 'amount', 'balance'];
  recCols = ['name', 'document', 'phone', 'balance'];

  ngOnInit() { this.load(); }

  edit(c: Customer) { this.form = { ...c }; }

  load() { this.api.customers(this.q).subscribe(c => this.customers.set(c)); }
  loadReceivables() { this.api.receivables().subscribe(r => this.receivables.set(r)); }

  save() {
    this.api.saveCustomer(this.form).subscribe(() => { this.snack.open('Cliente guardado', undefined, { duration: 2500 }); this.form = blank(); this.load(); });
  }

  openCredit(c: Customer) {
    this.api.customerCredit(c.id!).subscribe(s => this.selected.set(s));
    this.api.creditHistory(c.id!).subscribe(h => this.history.set(h));
  }

  pay(s: CustomerCredit) {
    this.api.pay(s.customer.id!, this.payAmount, this.payMethod, this.payNote).subscribe(() => {
      this.snack.open('Abono registrado', undefined, { duration: 2500 });
      this.payAmount = 0; this.payNote = '';
      this.openCredit(s.customer);
    });
  }
}

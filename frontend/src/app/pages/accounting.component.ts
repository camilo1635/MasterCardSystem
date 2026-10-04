import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { Account, Expense, IncomeStatement, JournalEntry, TrialBalanceRow } from '../core/models';
import { MatSnackBar, SHARED_IMPORTS, monthStart, today } from '../core/shared';

@Component({
  selector: 'app-accounting',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <h1>Contabilidad</h1>
    <div class="card row">
      <mat-form-field><mat-label>Desde</mat-label><input matInput type="date" [(ngModel)]="from" (ngModelChange)="reload()"></mat-form-field>
      <mat-form-field><mat-label>Hasta</mat-label><input matInput type="date" [(ngModel)]="to" (ngModelChange)="reload()"></mat-form-field>
    </div>

    <mat-tab-group>
      <mat-tab label="Estado de resultados">
        @if (income(); as i) {
          <div class="kpis" style="margin-top:16px">
            <div class="card kpi"><div class="label">Ingresos</div><div class="value">{{ i.income | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
            <div class="card kpi"><div class="label">Costo de ventas</div><div class="value">{{ i.costOfSales | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
            <div class="card kpi"><div class="label">Utilidad bruta</div><div class="value">{{ i.grossProfit | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
            <div class="card kpi"><div class="label">Gastos</div><div class="value">{{ i.expenses | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
            <div class="card kpi"><div class="label">Utilidad neta</div>
              <div class="value" [class.ok]="i.netProfit >= 0" [class.danger]="i.netProfit < 0">{{ i.netProfit | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
          </div>
        }
      </mat-tab>

      <mat-tab label="Balance de comprobación">
        <div class="card" style="margin-top:16px">
          <table mat-table [dataSource]="trial()">
            <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Código</th><td mat-cell *matCellDef="let r">{{ r.code }}</td></ng-container>
            <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Cuenta</th><td mat-cell *matCellDef="let r">{{ r.name }}</td></ng-container>
            <ng-container matColumnDef="debit"><th mat-header-cell *matHeaderCellDef class="num">Débito</th><td mat-cell *matCellDef="let r" class="num">{{ r.debit | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
            <ng-container matColumnDef="credit"><th mat-header-cell *matHeaderCellDef class="num">Crédito</th><td mat-cell *matCellDef="let r" class="num">{{ r.credit | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
            <ng-container matColumnDef="balance"><th mat-header-cell *matHeaderCellDef class="num">Saldo</th><td mat-cell *matCellDef="let r" class="num">{{ r.balance | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="trialCols"></tr>
            <tr mat-row *matRowDef="let r; columns: trialCols"></tr>
          </table>
        </div>
      </mat-tab>

      <mat-tab label="Libro diario">
        <div style="margin-top:16px">
          @for (e of journal(); track e.id) {
            <div class="card">
              <strong>{{ e.date | date:'dd/MM/yyyy' }}</strong> · {{ e.description }} <span class="muted">({{ e.source }})</span>
              <table mat-table [dataSource]="e.lines">
                <ng-container matColumnDef="account"><th mat-header-cell *matHeaderCellDef>Cuenta</th><td mat-cell *matCellDef="let l">{{ l.accountCode }} {{ l.accountName }}</td></ng-container>
                <ng-container matColumnDef="debit"><th mat-header-cell *matHeaderCellDef class="num">Débito</th><td mat-cell *matCellDef="let l" class="num">{{ l.debit ? (l.debit | currency:'COP':'symbol-narrow':'1.0-0') : '' }}</td></ng-container>
                <ng-container matColumnDef="credit"><th mat-header-cell *matHeaderCellDef class="num">Crédito</th><td mat-cell *matCellDef="let l" class="num">{{ l.credit ? (l.credit | currency:'COP':'symbol-narrow':'1.0-0') : '' }}</td></ng-container>
                <tr mat-header-row *matHeaderRowDef="lineCols"></tr>
                <tr mat-row *matRowDef="let r; columns: lineCols"></tr>
              </table>
            </div>
          }
          @if (!journal().length) { <p class="muted">Sin asientos en el período.</p> }
        </div>
      </mat-tab>

      <mat-tab label="Gastos">
        <div class="card" style="margin-top:16px">
          <div class="row">
            <mat-form-field><mat-label>Fecha</mat-label><input matInput type="date" [(ngModel)]="exp.date"></mat-form-field>
            <mat-form-field><mat-label>Cuenta de gasto</mat-label>
              <mat-select [(ngModel)]="exp.accountId">
                @for (a of expenseAccounts(); track a.id) { <mat-option [value]="a.id">{{ a.code }} {{ a.name }}</mat-option> }
              </mat-select></mat-form-field>
            <mat-form-field style="flex: 2 1 240px"><mat-label>Descripción</mat-label><input matInput [(ngModel)]="exp.description"></mat-form-field>
            <mat-form-field><mat-label>Valor</mat-label><input matInput type="number" min="1" [(ngModel)]="exp.amount"></mat-form-field>
            <button mat-flat-button color="primary" [disabled]="!exp.accountId || !exp.description || !exp.amount" (click)="saveExpense()">Registrar gasto</button>
          </div>
          <table mat-table [dataSource]="expenses()">
            <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Fecha</th><td mat-cell *matCellDef="let e">{{ e.date | date:'dd/MM/yyyy' }}</td></ng-container>
            <ng-container matColumnDef="account"><th mat-header-cell *matHeaderCellDef>Cuenta</th><td mat-cell *matCellDef="let e">{{ e.account.name }}</td></ng-container>
            <ng-container matColumnDef="description"><th mat-header-cell *matHeaderCellDef>Descripción</th><td mat-cell *matCellDef="let e">{{ e.description }}</td></ng-container>
            <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef class="num">Valor</th><td mat-cell *matCellDef="let e" class="num">{{ e.amount | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="expCols"></tr>
            <tr mat-row *matRowDef="let r; columns: expCols"></tr>
          </table>
        </div>
      </mat-tab>
    </mat-tab-group>
  `,
})
export class AccountingComponent implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  from = monthStart();
  to = today();
  income = signal<IncomeStatement | null>(null);
  trial = signal<TrialBalanceRow[]>([]);
  journal = signal<JournalEntry[]>([]);
  expenses = signal<Expense[]>([]);
  expenseAccounts = signal<Account[]>([]);
  exp = { date: today(), accountId: null as number | null, description: '', amount: 0 };
  trialCols = ['code', 'name', 'debit', 'credit', 'balance'];
  lineCols = ['account', 'debit', 'credit'];
  expCols = ['date', 'account', 'description', 'amount'];

  ngOnInit() {
    this.api.accounts().subscribe(a => this.expenseAccounts.set(a.filter(x => x.type === 'GASTO')));
    this.reload();
  }

  reload() {
    this.api.incomeStatement(this.from, this.to).subscribe(i => this.income.set(i));
    this.api.trialBalance(this.from, this.to).subscribe(t => this.trial.set(t));
    this.api.journal(this.from, this.to).subscribe(j => this.journal.set(j));
    this.api.expenses(this.from, this.to).subscribe(e => this.expenses.set(e));
  }

  saveExpense() {
    this.api.createExpense({ ...this.exp, accountId: this.exp.accountId! }).subscribe(() => {
      this.snack.open('Gasto registrado', undefined, { duration: 2500 });
      this.exp = { date: today(), accountId: null, description: '', amount: 0 };
      this.reload();
    });
  }
}

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DashboardSummary } from '../core/models';
import { SHARED_IMPORTS } from '../core/shared';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [...SHARED_IMPORTS, RouterLink],
  template: `
    <h1>Resumen</h1>
    @if (s(); as d) {
      <div class="kpis">
        <div class="card kpi"><div class="label">Ventas de hoy ({{ d.invoicesToday }} facturas)</div>
          <div class="value">{{ d.salesToday | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
        <div class="card kpi"><div class="label">Ventas del mes</div>
          <div class="value">{{ d.salesMonth | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
        <div class="card kpi"><div class="label">Cartera por cobrar</div>
          <div class="value">{{ d.receivable | currency:'COP':'symbol-narrow':'1.0-0' }}</div></div>
        <div class="card kpi"><div class="label">Productos con stock bajo</div>
          <div class="value" [class.danger]="d.lowStock.length">{{ d.lowStock.length }}</div></div>
      </div>
      <div class="card">
        <h3>Stock bajo o agotado</h3>
        @if (!d.lowStock.length) { <p class="muted">Todo el inventario está por encima del mínimo.</p> }
        @else {
          <table mat-table [dataSource]="d.lowStock">
            <ng-container matColumnDef="sku"><th mat-header-cell *matHeaderCellDef>SKU</th><td mat-cell *matCellDef="let p">{{ p.sku }}</td></ng-container>
            <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Producto</th><td mat-cell *matCellDef="let p">{{ p.name }}</td></ng-container>
            <ng-container matColumnDef="stock"><th mat-header-cell *matHeaderCellDef class="num">Stock</th><td mat-cell *matCellDef="let p" class="num danger">{{ p.stock }}</td></ng-container>
            <ng-container matColumnDef="min"><th mat-header-cell *matHeaderCellDef class="num">Mínimo</th><td mat-cell *matCellDef="let p" class="num">{{ p.minStock }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let r; columns: cols"></tr>
          </table>
        }
      </div>
      <a mat-flat-button color="primary" routerLink="/facturacion">Nueva factura</a>
    }
  `,
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  s = signal<DashboardSummary | null>(null);
  cols = ['sku', 'name', 'stock', 'min'];

  ngOnInit() { this.api.dashboard().subscribe(d => this.s.set(d)); }
}

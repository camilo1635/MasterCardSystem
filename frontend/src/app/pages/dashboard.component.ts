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
      </div>
      <a mat-flat-button color="primary" routerLink="/facturacion">Nueva factura</a>
    }
  `,
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  s = signal<DashboardSummary | null>(null);

  ngOnInit() { this.api.dashboard().subscribe(d => this.s.set(d)); }
}

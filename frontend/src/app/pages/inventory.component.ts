import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { InventoryMovement, Product } from '../core/models';
import { MatSnackBar, Pager, SHARED_IMPORTS } from '../core/shared';

@Component({
  selector: 'app-inventory',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <h1>Inventario</h1>
        <div class="card">
      <h3>Ajuste manual de cantidad</h3>
      <p class="muted">Use cantidad positiva para sumar (conteo, hallazgo) y negativa para restar (merma, daño). Las entradas por compra se hacen en Compras.</p>
      <div class="row">
        <mat-form-field style="flex: 2 1 300px"><mat-label>Producto</mat-label>
          <mat-select [(ngModel)]="productId">
            @for (p of products(); track p.id) { <mat-option [value]="p.id">{{ p.sku }} - {{ p.name }} (cantidad {{ p.stock }})</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Cantidad (+/-)</mat-label><input matInput type="number" [(ngModel)]="qty"></mat-form-field>
        <button mat-flat-button color="primary" [disabled]="!productId || !qty" (click)="adjust()">Aplicar ajuste</button>
      </div>
    </div>
    

    <div class="card">
      <h3>Movimientos recientes</h3>
      <mat-form-field style="min-width: 320px"><mat-label>Buscar producto (nombre o SKU)</mat-label>
        <input matInput [ngModel]="q()" (ngModelChange)="q.set($event); pager.reset()"></mat-form-field>
      <table mat-table [dataSource]="pager.slice(filtered())">
        <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Fecha</th><td mat-cell *matCellDef="let m">{{ m.createdAt | date:'dd/MM/yy HH:mm' }}</td></ng-container>
        <ng-container matColumnDef="product"><th mat-header-cell *matHeaderCellDef>Producto</th><td mat-cell *matCellDef="let m">{{ productNames().get(m.productId) ?? m.productId }}</td></ng-container>
        <ng-container matColumnDef="qty"><th mat-header-cell *matHeaderCellDef class="num">Cantidad</th>
          <td mat-cell *matCellDef="let m" class="num">{{ m.quantity > 0 ? '+' : '' }}{{ m.quantity }}</td></ng-container>
        <ng-container matColumnDef="cost"><th mat-header-cell *matHeaderCellDef class="num">Costo unit.</th><td mat-cell *matCellDef="let m" class="num">{{ m.unitCost | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let r; columns: cols"></tr>
      </table>
      <app-pager [pager]="pager" [length]="filtered().length" />
    </div>
  `,
})
export class InventoryComponent implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  products = signal<Product[]>([]);
  movements = signal<InventoryMovement[]>([]);
  productNames = computed(() => new Map(this.products().map(p => [p.id!, p.name])));
  productId: number | null = null;
  q = signal('');
  filtered = computed(() => {
    const q = this.q().trim().toLowerCase();
    if (!q) return this.movements();
    const ids = new Set(this.products().filter(p => p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q)).map(p => p.id));
    return this.movements().filter(m => ids.has(m.productId));
  });
  qty = 0;
  pager = new Pager();
  cols =['date', 'product', 'qty', 'cost'];

  ngOnInit() { this.loadProducts(); this.loadMovements(); }

  loadProducts() { this.api.products().subscribe(p => this.products.set(p)); }
  loadMovements() { this.api.movements().subscribe(m => this.movements.set(m)); }


  adjust() {
    this.api.adjust(this.productId!, this.qty).subscribe(() => {
      this.snack.open('Ajuste aplicado', undefined, { duration: 2500 });
      this.qty = 0;
      this.loadProducts(); this.loadMovements();
    });
  }
}

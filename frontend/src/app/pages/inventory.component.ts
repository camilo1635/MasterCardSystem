import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { InventoryMovement, Product } from '../core/models';
import { MatSnackBar, SHARED_IMPORTS } from '../core/shared';

@Component({
  selector: 'app-inventory',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <h1>Inventario</h1>
    @if (canAdjust) {
    <div class="card">
      <h3>Ajuste manual de stock</h3>
      <p class="muted">Use cantidad positiva para sumar (conteo, hallazgo) y negativa para restar (merma, daño). Las entradas por compra se hacen en Compras.</p>
      <div class="row">
        <mat-form-field style="flex: 2 1 300px"><mat-label>Producto</mat-label>
          <mat-select [(ngModel)]="productId">
            @for (p of products(); track p.id) { <mat-option [value]="p.id">{{ p.sku }} - {{ p.name }} (stock {{ p.stock }})</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Cantidad (+/-)</mat-label><input matInput type="number" [(ngModel)]="qty"></mat-form-field>
        <mat-form-field style="flex: 2 1 240px"><mat-label>Motivo</mat-label><input matInput [(ngModel)]="note"></mat-form-field>
        <button mat-flat-button color="primary" [disabled]="!productId || !qty" (click)="adjust()">Aplicar ajuste</button>
      </div>
    </div>
    }

    <div class="card">
      <h3>Movimientos recientes</h3>
      <mat-form-field style="min-width: 320px"><mat-label>Filtrar por producto</mat-label>
        <mat-select [ngModel]="filterId" (ngModelChange)="filterId = $event; loadMovements()">
          <mat-option [value]="null">Todos</mat-option>
          @for (p of products(); track p.id) { <mat-option [value]="p.id">{{ p.name }}</mat-option> }
        </mat-select></mat-form-field>
      <table mat-table [dataSource]="movements()">
        <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Fecha</th><td mat-cell *matCellDef="let m">{{ m.createdAt | date:'dd/MM/yy HH:mm' }}</td></ng-container>
        <ng-container matColumnDef="product"><th mat-header-cell *matHeaderCellDef>Producto</th><td mat-cell *matCellDef="let m">{{ productNames().get(m.productId) ?? m.productId }}</td></ng-container>
        <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Tipo</th><td mat-cell *matCellDef="let m">{{ m.type }}</td></ng-container>
        <ng-container matColumnDef="qty"><th mat-header-cell *matHeaderCellDef class="num">Cantidad</th>
          <td mat-cell *matCellDef="let m" class="num" [class.danger]="m.quantity < 0" [class.ok]="m.quantity > 0">{{ m.quantity > 0 ? '+' : '' }}{{ m.quantity }}</td></ng-container>
        <ng-container matColumnDef="cost"><th mat-header-cell *matHeaderCellDef class="num">Costo unit.</th><td mat-cell *matCellDef="let m" class="num">{{ m.unitCost | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
        <ng-container matColumnDef="ref"><th mat-header-cell *matHeaderCellDef>Referencia</th><td mat-cell *matCellDef="let m">{{ m.reference }} <span class="muted">{{ m.note }}</span></td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let r; columns: cols"></tr>
      </table>
    </div>
  `,
})
export class InventoryComponent implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  /** Solo UX: los ajustes de stock son de ADMIN. */
  canAdjust = inject(AuthService).hasRole('ADMIN');
  products = signal<Product[]>([]);
  movements = signal<InventoryMovement[]>([]);
  productNames = computed(() => new Map(this.products().map(p => [p.id!, p.name])));
  productId: number | null = null;
  filterId: number | null = null;
  qty = 0;
  note = '';
  cols = ['date', 'product', 'type', 'qty', 'cost', 'ref'];

  ngOnInit() { this.loadProducts(); this.loadMovements(); }

  loadProducts() { this.api.products().subscribe(p => this.products.set(p)); }
  loadMovements() { this.api.movements(this.filterId ?? undefined).subscribe(m => this.movements.set(m)); }


  adjust() {
    this.api.adjust(this.productId!, this.qty, this.note).subscribe(() => {
      this.snack.open('Ajuste aplicado', undefined, { duration: 2500 });
      this.qty = 0; this.note = '';
      this.loadProducts(); this.loadMovements();
    });
  }
}

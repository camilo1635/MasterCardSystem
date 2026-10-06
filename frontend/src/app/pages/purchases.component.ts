import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { Product, Purchase, Supplier } from '../core/models';
import { MatSnackBar, SHARED_IMPORTS, today } from '../core/shared';

interface Line { productId: number | null; quantity: number; unitCost: number; }

@Component({
  selector: 'app-purchases',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <h1>Compras y proveedores</h1>
    <mat-tab-group>
            <mat-tab label="Nueva compra">
        <div class="card" style="margin-top:16px">
          <div class="row">
            <mat-form-field style="flex: 2 1 260px"><mat-label>Proveedor</mat-label>
              <mat-select [(ngModel)]="supplierId">
                @for (s of suppliers(); track s.id) { <mat-option [value]="s.id">{{ s.name }}</mat-option> }
              </mat-select></mat-form-field>
            <mat-form-field><mat-label>No. documento</mat-label><input matInput [(ngModel)]="docNumber"></mat-form-field>
            <mat-form-field><mat-label>Fecha</mat-label><input matInput type="date" [(ngModel)]="date"></mat-form-field>
            <mat-form-field><mat-label>Pago</mat-label>
              <mat-select [(ngModel)]="paymentType"><mat-option value="CONTADO">Contado</mat-option><mat-option value="CREDITO">Crédito (proveedor)</mat-option></mat-select></mat-form-field>
          </div>
          @for (l of lines; track $index) {
            <div class="row">
              <mat-form-field style="flex: 3 1 300px"><mat-label>Producto</mat-label>
                <mat-select [(ngModel)]="l.productId" (ngModelChange)="onProduct(l)">
                  @for (p of products(); track p.id) { <mat-option [value]="p.id">{{ p.sku }} - {{ p.name }}</mat-option> }
                </mat-select></mat-form-field>
              <mat-form-field><mat-label>Cantidad</mat-label><input matInput type="number" min="1" [(ngModel)]="l.quantity"></mat-form-field>
              <mat-form-field><mat-label>Costo unitario</mat-label><input matInput type="number" min="0" [(ngModel)]="l.unitCost"></mat-form-field>
              <button mat-icon-button (click)="lines.splice($index, 1)" [disabled]="lines.length === 1"><mat-icon>delete</mat-icon></button>
            </div>
          }
          <div class="row">
            <button mat-button (click)="addLine()"><mat-icon>add</mat-icon> Agregar línea</button>
            <span class="muted">Subtotal estimado: {{ subtotal() | currency:'COP':'symbol-narrow':'1.0-0' }} + IVA</span>
            <button mat-flat-button color="primary" (click)="save()">Registrar compra</button>
          </div>
        </div>
      </mat-tab>
      

      <mat-tab label="Historial">
        <div class="card" style="margin-top:16px">
          <table mat-table [dataSource]="purchases()">
            <ng-container matColumnDef="id"><th mat-header-cell *matHeaderCellDef>#</th><td mat-cell *matCellDef="let p">{{ p.id }}</td></ng-container>
            <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Fecha</th><td mat-cell *matCellDef="let p">{{ p.date | date:'dd/MM/yyyy' }}</td></ng-container>
            <ng-container matColumnDef="supplier"><th mat-header-cell *matHeaderCellDef>Proveedor</th><td mat-cell *matCellDef="let p">{{ p.supplier.name }}</td></ng-container>
            <ng-container matColumnDef="doc"><th mat-header-cell *matHeaderCellDef>Documento</th><td mat-cell *matCellDef="let p">{{ p.docNumber }}</td></ng-container>
            <ng-container matColumnDef="pay"><th mat-header-cell *matHeaderCellDef>Pago</th><td mat-cell *matCellDef="let p">{{ p.paymentType }}</td></ng-container>
            <ng-container matColumnDef="total"><th mat-header-cell *matHeaderCellDef class="num">Total</th><td mat-cell *matCellDef="let p" class="num">{{ p.total | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let r; columns: cols"></tr>
          </table>
        </div>
      </mat-tab>

      <mat-tab label="Proveedores">
        <div class="card" style="margin-top:16px">
                    <div class="row">
            <mat-form-field><mat-label>NIT</mat-label><input matInput [(ngModel)]="sup.nit"></mat-form-field>
            <mat-form-field style="flex: 2 1 240px"><mat-label>Nombre</mat-label><input matInput [(ngModel)]="sup.name"></mat-form-field>
            <mat-form-field><mat-label>Teléfono</mat-label><input matInput [(ngModel)]="sup.phone"></mat-form-field>
            <mat-form-field><mat-label>Email</mat-label><input matInput [(ngModel)]="sup.email"></mat-form-field>
            <button mat-flat-button color="primary" [disabled]="!sup.name" (click)="saveSupplier()">Guardar proveedor</button>
          </div>
          
          <table mat-table [dataSource]="suppliers()">
            <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Nombre</th><td mat-cell *matCellDef="let s">{{ s.name }}</td></ng-container>
            <ng-container matColumnDef="nit"><th mat-header-cell *matHeaderCellDef>NIT</th><td mat-cell *matCellDef="let s">{{ s.nit }}</td></ng-container>
            <ng-container matColumnDef="phone"><th mat-header-cell *matHeaderCellDef>Teléfono</th><td mat-cell *matCellDef="let s">{{ s.phone }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="supCols"></tr>
            <tr mat-row *matRowDef="let r; columns: supCols"></tr>
          </table>
        </div>
      </mat-tab>
    </mat-tab-group>
  `,
})
export class PurchasesComponent implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  suppliers = signal<Supplier[]>([]);
  products = signal<Product[]>([]);
  purchases = signal<Purchase[]>([]);
  supplierId: number | null = null;
  docNumber = '';
  date = today();
  paymentType = 'CONTADO';
  lines: Line[] = [{ productId: null, quantity: 1, unitCost: 0 }];
  sup: Supplier = { name: '' };
  cols = ['id', 'date', 'supplier', 'doc', 'pay', 'total'];
  supCols = ['name', 'nit', 'phone'];

  ngOnInit() {
    this.api.products().subscribe(p => this.products.set(p));
    this.loadSuppliers();
    this.loadPurchases();
  }

  loadSuppliers() { this.api.suppliers().subscribe(s => this.suppliers.set(s)); }
  loadPurchases() { this.api.purchases().subscribe(p => this.purchases.set(p)); }
  addLine() { this.lines.push({ productId: null, quantity: 1, unitCost: 0 }); }
  onProduct(l: Line) { l.unitCost = this.products().find(p => p.id === l.productId)?.cost ?? 0; }
  subtotal() { return this.lines.reduce((s, l) => s + l.quantity * l.unitCost, 0); }

  save() {
    const items = this.lines.filter(l => l.productId).map(l => ({ productId: l.productId!, quantity: l.quantity, unitCost: l.unitCost }));
    if (!this.supplierId || !items.length) { this.snack.open('Seleccione proveedor y al menos un producto', 'Cerrar', { duration: 4000 }); return; }
    this.api.createPurchase({ supplierId: this.supplierId, docNumber: this.docNumber, date: this.date, paymentType: this.paymentType, items })
      .subscribe(() => {
        this.snack.open('Compra registrada, inventario actualizado', undefined, { duration: 3000 });
        this.lines = [{ productId: null, quantity: 1, unitCost: 0 }];
        this.docNumber = '';
        this.api.products().subscribe(p => this.products.set(p));
        this.loadPurchases();
      });
  }

  saveSupplier() {
    this.api.saveSupplier(this.sup).subscribe(() => { this.sup = { name: '' }; this.loadSuppliers(); });
  }
}

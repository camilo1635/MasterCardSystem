import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Category, Product } from '../core/models';
import { MatSnackBar, SHARED_IMPORTS } from '../core/shared';

const blank = (): Product => ({ sku: '', name: '', brand: '', category: null, cost: 0, price: 0, ivaRate: 19, stock: 0, minStock: 0, active: true });

@Component({
  selector: 'app-products',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <h1>Productos</h1>
    <div class="card">
      <h3>{{ form.id ? 'Editar producto' : 'Nuevo producto' }}</h3>
      <div class="row">
        <mat-form-field><mat-label>SKU</mat-label><input matInput [(ngModel)]="form.sku"></mat-form-field>
        <mat-form-field style="flex: 2 1 300px"><mat-label>Nombre</mat-label><input matInput [(ngModel)]="form.name"></mat-form-field>
        <mat-form-field><mat-label>Marca</mat-label><input matInput [(ngModel)]="form.brand"></mat-form-field>
        <mat-form-field><mat-label>Categoría</mat-label>
          <mat-select [(ngModel)]="categoryId">
            <mat-option [value]="null">Sin categoría</mat-option>
            @for (c of categories(); track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select></mat-form-field>
      </div>
      <div class="row">
        <mat-form-field><mat-label>Costo</mat-label><input matInput type="number" [(ngModel)]="form.cost"></mat-form-field>
        <mat-form-field><mat-label>Precio (sin IVA)</mat-label><input matInput type="number" [(ngModel)]="form.price"></mat-form-field>
        <mat-form-field><mat-label>IVA %</mat-label><input matInput type="number" [(ngModel)]="form.ivaRate"></mat-form-field>
        <mat-form-field><mat-label>Stock mínimo</mat-label><input matInput type="number" [(ngModel)]="form.minStock"></mat-form-field>
        <mat-checkbox [(ngModel)]="form.active">Activo</mat-checkbox>
      </div>
      <div class="row">
        <button mat-flat-button color="primary" (click)="save()">Guardar</button>
        @if (form.id) { <button mat-button (click)="reset()">Cancelar</button> }
        <span class="muted">El stock inicial se carga desde Compras o con un ajuste en Inventario.</span>
      </div>
      <div class="row">
        <mat-form-field style="max-width:260px"><mat-label>Nueva categoría</mat-label><input matInput [(ngModel)]="newCategory"></mat-form-field>
        <button mat-stroked-button (click)="addCategory()">Agregar categoría</button>
      </div>
    </div>

    <div class="card">
      <mat-form-field style="width: 100%"><mat-label>Buscar por nombre, SKU o marca</mat-label>
        <input matInput [ngModel]="q" (ngModelChange)="onSearch($event)"></mat-form-field>
      <table mat-table [dataSource]="items()">
        <ng-container matColumnDef="sku"><th mat-header-cell *matHeaderCellDef>SKU</th><td mat-cell *matCellDef="let p">{{ p.sku }}</td></ng-container>
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Producto</th>
          <td mat-cell *matCellDef="let p">{{ p.name }} <span class="muted">{{ p.brand }}</span>@if (!p.active) { <em class="muted"> (inactivo)</em> }</td></ng-container>
        <ng-container matColumnDef="cost"><th mat-header-cell *matHeaderCellDef class="num">Costo</th><td mat-cell *matCellDef="let p" class="num">{{ p.cost | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
        <ng-container matColumnDef="price"><th mat-header-cell *matHeaderCellDef class="num">Precio</th><td mat-cell *matCellDef="let p" class="num">{{ p.price | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
        <ng-container matColumnDef="stock"><th mat-header-cell *matHeaderCellDef class="num">Stock</th>
          <td mat-cell *matCellDef="let p" class="num" [class.danger]="p.stock <= p.minStock">{{ p.stock }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let p"><button mat-icon-button (click)="edit(p)"><mat-icon>edit</mat-icon></button></td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let r; columns: cols"></tr>
      </table>
    </div>
  `,
})
export class ProductsComponent implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  items = signal<Product[]>([]);
  categories = signal<Category[]>([]);
  form: Product = blank();
  categoryId: number | null = null;
  newCategory = '';
  q = '';
  cols = ['sku', 'name', 'cost', 'price', 'stock', 'actions'];

  private search$ = new Subject<string>();

  constructor() {
    // Espera a que el usuario deje de teclear y descarta respuestas de búsquedas anteriores.
    this.search$.pipe(debounceTime(250), distinctUntilChanged(), switchMap(q => this.api.products(q)), takeUntilDestroyed())
      .subscribe(p => this.items.set(p));
  }

  ngOnInit() { this.load(); this.loadCategories(); }

  onSearch(q: string) { this.q = q; this.search$.next(q); }
  load() { this.api.products(this.q).subscribe(p => this.items.set(p)); }
  loadCategories() { this.api.categories().subscribe(c => this.categories.set(c)); }

  edit(p: Product) { this.form = { ...p }; this.categoryId = p.category?.id ?? null; window.scrollTo({ top: 0, behavior: 'smooth' }); }
  reset() { this.form = blank(); this.categoryId = null; }

  save() {
    const body: Product = { ...this.form, category: this.categoryId ? { id: this.categoryId, name: '' } : null };
    this.api.saveProduct(body).subscribe(() => { this.snack.open('Producto guardado', undefined, { duration: 2500 }); this.reset(); this.load(); });
  }

  addCategory() {
    if (!this.newCategory.trim()) return;
    this.api.createCategory({ name: this.newCategory.trim() }).subscribe(() => { this.newCategory = ''; this.loadCategories(); });
  }
}

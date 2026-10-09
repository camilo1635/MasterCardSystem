import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, finalize, switchMap } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Customer, Invoice, Product, Returnable, SalesReturn } from '../core/models';
import { MatSnackBar, SHARED_IMPORTS } from '../core/shared';

interface CartLine { product: Product; quantity: number; unitPrice: number; }

@Component({
  selector: 'app-invoicing',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: SHARED_IMPORTS,
  template: `
    <h1>Facturación</h1>
    <mat-tab-group [(selectedIndex)]="tab">
      <mat-tab label="Nueva factura">
        <div class="row" style="margin-top:16px; align-items: flex-start">
          <div class="card" style="flex: 3 1 420px">
            <mat-form-field style="width:100%"><mat-label>Buscar producto (nombre, SKU, marca)</mat-label>
              <input matInput [ngModel]="q" (ngModelChange)="onSearch($event)"></mat-form-field>
            <table mat-table [dataSource]="results()">
              <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Producto</th><td mat-cell *matCellDef="let p">{{ p.name }} <span class="muted">{{ p.sku }}</span></td></ng-container>
              <ng-container matColumnDef="price"><th mat-header-cell *matHeaderCellDef class="num">Precio</th><td mat-cell *matCellDef="let p" class="num">{{ p.price | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
              <ng-container matColumnDef="stock"><th mat-header-cell *matHeaderCellDef class="num">Cantidad</th><td mat-cell *matCellDef="let p" class="num" [class.danger]="p.stock === 0">{{ p.stock }}</td></ng-container>
              <ng-container matColumnDef="add"><th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let p"><button mat-icon-button [disabled]="p.stock <= 0" [title]="p.stock <= 0 ? 'Sin cantidad disponible' : 'Agregar'" (click)="add(p)"><mat-icon>add_shopping_cart</mat-icon></button></td></ng-container>
              <tr mat-header-row *matHeaderRowDef="searchCols"></tr>
              <tr mat-row *matRowDef="let r; columns: searchCols"></tr>
            </table>
          </div>

          <div class="card" style="flex: 2 1 380px">
            <h3>Factura</h3>
            <mat-form-field style="width:100%"><mat-label>Cliente</mat-label>
              <mat-select [(ngModel)]="customerId">
                <mat-option [value]="null">Consumidor final</mat-option>
                @for (c of customers(); track c.id) { <mat-option [value]="c.id">{{ c.name }} ({{ c.document }})</mat-option> }
              </mat-select></mat-form-field>
            <mat-form-field style="width:100%"><mat-label>Forma de pago</mat-label>
              <mat-select [(ngModel)]="paymentType">
                <mat-option value="CONTADO">Contado</mat-option>
                <mat-option value="CREDITO" [disabled]="!customerId">Crédito (fiado)</mat-option>
              </mat-select></mat-form-field>
            @for (l of cart(); track l.product.id) {
              <div class="row" style="margin-bottom:4px">
                <span style="flex: 1 1 140px">{{ l.product.name }}</span>
                <input type="number" min="1" [max]="l.product.stock" style="width:80px; height:36px; font-size:16px; text-align:center" [(ngModel)]="l.quantity" (ngModelChange)="touch()">
                <span class="num" style="width:90px">{{ l.quantity * l.unitPrice | currency:'COP':'symbol-narrow':'1.0-0' }}</span>
                <button mat-icon-button (click)="remove(l)"><mat-icon>close</mat-icon></button>
              </div>
            }
            @if (!cart().length) { <p class="muted">Agregue productos desde la lista.</p> }
            <hr>
            <div class="row"><span style="flex:1">Subtotal</span><span>{{ totals().subtotal | currency:'COP':'symbol-narrow':'1.0-0' }}</span></div>
            <div class="row"><span style="flex:1">IVA</span><span>{{ totals().iva | currency:'COP':'symbol-narrow':'1.0-0' }}</span></div>
            <div class="row"><strong style="flex:1">Total</strong><strong>{{ totals().total | currency:'COP':'symbol-narrow':'1.0-0' }}</strong></div>
            <br>
            <button mat-flat-button color="primary" style="width:100%" [disabled]="!cart().length || saving()" (click)="submit()">Emitir factura</button>
          </div>
        </div>
      </mat-tab>

      <mat-tab label="Historial">
        <div class="card" style="margin-top:16px">
          <div class="row">
            <mat-form-field><mat-label>Desde</mat-label><input matInput type="date" [(ngModel)]="from" (ngModelChange)="loadInvoices(); loadReturns()"></mat-form-field>
            <mat-form-field><mat-label>Hasta</mat-label><input matInput type="date" [(ngModel)]="to" (ngModelChange)="loadInvoices(); loadReturns()"></mat-form-field>
          </div>
          <table mat-table [dataSource]="invoices()">
            <ng-container matColumnDef="number"><th mat-header-cell *matHeaderCellDef>No.</th><td mat-cell *matCellDef="let i">{{ i.number }}</td></ng-container>
            <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Fecha</th><td mat-cell *matCellDef="let i">{{ i.date | date:'dd/MM/yy HH:mm' }}</td></ng-container>
            <ng-container matColumnDef="customer"><th mat-header-cell *matHeaderCellDef>Cliente</th><td mat-cell *matCellDef="let i">{{ customerName(i.customerId) }}</td></ng-container>
            <ng-container matColumnDef="pay"><th mat-header-cell *matHeaderCellDef>Pago</th><td mat-cell *matCellDef="let i">{{ i.paymentType }}</td></ng-container>
            <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Estado</th><td mat-cell *matCellDef="let i" [class.danger]="i.status === 'ANULADA'">{{ statusLabel(i) }}</td></ng-container>
            <ng-container matColumnDef="total"><th mat-header-cell *matHeaderCellDef class="num">Total</th><td mat-cell *matCellDef="let i" class="num">{{ i.total | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
            <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let i">
                <button mat-icon-button title="PDF" aria-label="Ver PDF de la factura" (click)="openPdf(i.id)"><mat-icon>picture_as_pdf</mat-icon></button>
                @if (i.status === 'EMITIDA') {
                  @if (i.returnStatus !== 'TOTAL') {
                    <button mat-icon-button title="Devolución" aria-label="Registrar devolución" (click)="startReturn(i)"><mat-icon>assignment_return</mat-icon></button>
                  }
                  @if (!i.returnStatus || i.returnStatus === 'NINGUNA') {
                    <button mat-icon-button title="Anular" aria-label="Anular factura" (click)="cancel(i)"><mat-icon>block</mat-icon></button>
                  }
                }
              </td></ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let r; columns: cols"></tr>
          </table>
        </div>
      </mat-tab>

      <mat-tab label="Devoluciones">
        <div class="row" style="margin-top:16px; align-items: flex-start">
          <div class="card" style="flex: 2 1 420px">
            <h3>Nueva devolución</h3>
            @if (!returnInvoice()) {
              <p class="muted">Elija una factura en la pestaña Historial con el botón de devolución.</p>
            } @else {
              <p>Factura <strong>{{ returnInvoice()!.number }}</strong> · {{ returnInvoice()!.paymentType }}
                @if (returnInvoice()!.paymentType === 'CREDITO') { <span class="muted">(se descuenta del saldo del cliente; lo que exceda se reembolsa en efectivo)</span> }
              </p>
              @for (l of returnLines(); track l.invoiceItemId) {
                <div class="row" style="margin-bottom:4px">
                  <span style="flex: 1 1 160px">{{ l.description }} <span class="muted">vendidas {{ l.sold }} · devueltas {{ l.returned }}</span></span>
                  <input type="number" min="0" [max]="l.available" [disabled]="l.available === 0"
                         style="width:80px; height:36px; font-size:16px; text-align:center"
                         [ngModel]="qty()[l.invoiceItemId] || 0" (ngModelChange)="setQty(l, $event)">
                </div>
              }
              <mat-form-field style="width:100%"><mat-label>Motivo (opcional)</mat-label>
                <input matInput maxlength="300" [(ngModel)]="reason"></mat-form-field>
              <div class="row"><strong style="flex:1">Total a devolver</strong><strong>{{ returnTotal() | currency:'COP':'symbol-narrow':'1.0-0' }}</strong></div>
              <br>
              <button mat-flat-button color="primary" style="width:100%" [disabled]="returnTotal() <= 0 || saving()" (click)="submitReturn()">Registrar devolución</button>
            }
          </div>

          <div class="card" style="flex: 3 1 420px">
            <h3>Devoluciones registradas</h3>
            <table mat-table [dataSource]="returnsList()">
              <ng-container matColumnDef="number"><th mat-header-cell *matHeaderCellDef>No.</th><td mat-cell *matCellDef="let r">{{ r.number }}</td></ng-container>
              <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Fecha</th><td mat-cell *matCellDef="let r">{{ r.date | date:'dd/MM/yy HH:mm' }}</td></ng-container>
              <ng-container matColumnDef="invoice"><th mat-header-cell *matHeaderCellDef>Factura</th><td mat-cell *matCellDef="let r">{{ invoiceNumber(r.invoiceId) }}</td></ng-container>
              <ng-container matColumnDef="customer"><th mat-header-cell *matHeaderCellDef>Cliente</th><td mat-cell *matCellDef="let r">{{ customerName(r.customerId) }}</td></ng-container>
              <ng-container matColumnDef="reason"><th mat-header-cell *matHeaderCellDef>Motivo</th><td mat-cell *matCellDef="let r">{{ r.reason }}</td></ng-container>
              <ng-container matColumnDef="total"><th mat-header-cell *matHeaderCellDef class="num">Total</th><td mat-cell *matCellDef="let r" class="num">{{ r.total | currency:'COP':'symbol-narrow':'1.0-0' }}</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="returnCols"></tr>
              <tr mat-row *matRowDef="let r; columns: returnCols"></tr>
            </table>
            @if (!returnsList().length) { <p class="muted">Sin devoluciones en el período.</p> }
          </div>
        </div>
      </mat-tab>
    </mat-tab-group>
  `,
})
export class InvoicingComponent implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  results = signal<Product[]>([]);
  customers = signal<Customer[]>([]);
  invoices = signal<Invoice[]>([]);
  cart = signal<CartLine[]>([]);
  saving = signal(false);
  private search$ = new Subject<string>();
  private customerNames = computed(() => new Map(this.customers().map(c => [c.id, c.name])));
  customerId: number | null = null;
  paymentType = 'CONTADO';
  q = '';
  from = new Date(Date.now() - 30 * 864e5).toLocaleDateString('sv-SE');
  to = new Date().toLocaleDateString('sv-SE');
  searchCols = ['name', 'price', 'stock', 'add'];
  cols = ['number', 'date', 'customer', 'pay', 'status', 'total', 'actions'];

  // Devoluciones
  tab = signal(0);
  returnCols = ['number', 'date', 'invoice', 'customer', 'reason', 'total'];
  returnInvoice = signal<Invoice | null>(null);
  returnLines = signal<Returnable[]>([]);
  returnsList = signal<SalesReturn[]>([]);
  qty = signal<Record<number, number>>({});
  reason = '';
  returnTotal = computed(() => {
    let total = 0;
    for (const l of this.returnLines()) {
      const q = this.qty()[l.invoiceItemId] ?? 0;
      const line = Math.round(q * l.unitPrice * 100) / 100;
      total += line + Math.round(line * l.ivaRate) / 100;
    }
    return total;
  });

  totals = computed(() => {
    let subtotal = 0, iva = 0;
    for (const l of this.cart()) {
      const line = Math.round(l.quantity * l.unitPrice * 100) / 100;
      subtotal += line;
      iva += Math.round(line * l.product.ivaRate) / 100;
    }
    return { subtotal, iva, total: subtotal + iva };
  });

  constructor() {
    // Espera a que el usuario deje de teclear y descarta respuestas de búsquedas anteriores.
    this.search$.pipe(debounceTime(250), distinctUntilChanged(), switchMap(q => this.api.products(q)), takeUntilDestroyed())
      .subscribe(p => this.results.set(p));
  }

  ngOnInit() {
    this.search();
    this.api.customers().subscribe(c => this.customers.set(c));
    this.loadInvoices();
    this.loadReturns();
  }

  onSearch(q: string) { this.q = q; this.search$.next(q); }
  search() { this.api.products(this.q).subscribe(p => this.results.set(p)); }
  loadInvoices() { this.api.invoices(this.from, this.to).subscribe(i => this.invoices.set(i)); }
  customerName(id?: number) { return id ? this.customerNames().get(id) ?? id : 'Consumidor final'; }

  /** El PDF requiere el token: se pide como blob y se abre en una pestaña (o se descarga si el navegador bloquea la ventana). */
  openPdf(id: number) {
    const win = window.open('', '_blank'); // se abre en el clic para no ser bloqueada por el navegador
    this.api.invoicePdf(id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        if (win) win.location.href = url;
        else { const a = document.createElement('a'); a.href = url; a.download = `factura-${id}.pdf`; a.click(); }
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => win?.close(),
    });
  }

  add(p: Product) {
    const existing = this.cart().find(l => l.product.id === p.id);
    if (existing) existing.quantity++;
    this.cart.update(c => existing ? [...c] : [...c, { product: p, quantity: 1, unitPrice: p.price }]);
  }

  touch() { this.cart.set([...this.cart()]); }

  remove(l: CartLine) { this.cart.update(c => c.filter(x => x !== l)); }

  submit() {
    if (this.saving()) return; // evita facturas duplicadas por doble clic
    this.saving.set(true);
    this.api.createInvoice({
      customerId: this.customerId,
      paymentType: this.paymentType,
      items: this.cart().map(l => ({ productId: l.product.id!, quantity: l.quantity, unitPrice: l.unitPrice })),
    }).pipe(finalize(() => this.saving.set(false))).subscribe(inv => {
      this.snack.open(`Factura ${inv.number} emitida`, 'Ver PDF', { duration: 8000 }).onAction()
        .subscribe(() => this.openPdf(inv.id));
      this.cart.set([]);
      this.paymentType = 'CONTADO';
      this.search();
      this.loadInvoices();
    });
  }

  statusLabel(i: Invoice) {
    if (i.status === 'EMITIDA') {
      if (i.returnStatus === 'TOTAL') return 'DEVUELTA';
      if (i.returnStatus === 'PARCIAL') return 'DEVOLUCIÓN PARCIAL';
    }
    return i.status;
  }

  loadReturns() { this.api.returns(this.from, this.to).subscribe(r => this.returnsList.set(r)); }
  invoiceNumber(id: number) { return this.invoices().find(i => i.id === id)?.number ?? id; }

  startReturn(i: Invoice) {
    this.api.returnable(i.id).subscribe(lines => {
      if (!lines.some(l => l.available > 0)) {
        this.snack.open('La factura ya fue devuelta por completo', undefined, { duration: 3000 });
        return;
      }
      this.returnInvoice.set(i);
      this.returnLines.set(lines);
      this.qty.set({});
      this.reason = '';
      this.tab.set(2);
    });
  }

  setQty(l: Returnable, value: number | string) {
    const n = Math.max(0, Math.min(l.available, Math.floor(Number(value) || 0)));
    this.qty.update(q => ({ ...q, [l.invoiceItemId]: n }));
  }

  submitReturn() {
    const inv = this.returnInvoice();
    if (!inv || this.saving()) return;
    const items = this.returnLines()
      .map(l => ({ invoiceItemId: l.invoiceItemId, quantity: this.qty()[l.invoiceItemId] ?? 0 }))
      .filter(i => i.quantity > 0);
    if (!items.length) return;
    this.saving.set(true);
    this.api.createReturn({ invoiceId: inv.id, reason: this.reason.trim() || undefined, items })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe(r => {
        this.snack.open(`Devolución ${r.number} registrada: el producto volvió al inventario`, undefined, { duration: 4000 });
        this.returnInvoice.set(null);
        this.returnLines.set([]);
        this.qty.set({});
        this.loadReturns();
        this.loadInvoices();
        this.search();
      });
  }

  cancel(i: Invoice) {
    if (!confirm(`¿Anular la factura ${i.number}? Se devolverá la cantidad y se revertirá el crédito.`)) return;
    this.api.cancelInvoice(i.id).subscribe(() => { this.snack.open('Factura anulada', undefined, { duration: 2500 }); this.loadInvoices(); this.search(); });
  }
}

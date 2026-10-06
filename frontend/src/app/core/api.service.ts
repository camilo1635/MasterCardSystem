import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account, AuthResponse, AuthUser, Category, CreditTransaction, Customer, CustomerCredit, DashboardSummary, Expense, IncomeStatement,
  InventoryMovement, Invoice, InvoiceRequest, JournalEntry, Product, Purchase, PurchaseRequest, Receivable,
  Supplier, TrialBalanceRow,
} from './models';
import { environment } from '../../environments/environment';

export const API_URL = environment.apiUrl;

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  private range(from: string, to: string) {
    return new HttpParams().set('from', from).set('to', to);
  }

  // Autenticación (la cookie de refresh viaja con withCredentials)
  login(username: string, password: string) {
    return this.http.post<AuthResponse>(`${API_URL}/auth/login`, { username, password }, { withCredentials: true });
  }
  refresh() { return this.http.post<AuthResponse>(`${API_URL}/auth/refresh`, null, { withCredentials: true }); }
  logout() { return this.http.post<void>(`${API_URL}/auth/logout`, null, { withCredentials: true }); }
  me() { return this.http.get<AuthUser>(`${API_URL}/auth/me`, { withCredentials: true }); }

  dashboard(): Observable<DashboardSummary> { return this.http.get<DashboardSummary>(`${API_URL}/dashboard`); }

  // Productos
  categories() { return this.http.get<Category[]>(`${API_URL}/categories`); }
  createCategory(c: Category) { return this.http.post<Category>(`${API_URL}/categories`, c); }
  products(q = '') { return this.http.get<Product[]>(`${API_URL}/products`, { params: new HttpParams().set('q', q) }); }
  saveProduct(p: Product) {
    return p.id ? this.http.put<Product>(`${API_URL}/products/${p.id}`, p) : this.http.post<Product>(`${API_URL}/products`, p);
  }

  // Inventario y compras
  movements(productId?: number) {
    let params = new HttpParams();
    if (productId) params = params.set('productId', productId);
    return this.http.get<InventoryMovement[]>(`${API_URL}/inventory/movements`, { params });
  }
  adjust(productId: number, quantity: number, note: string) {
    return this.http.post<Product>(`${API_URL}/inventory/adjust`, { productId, quantity, note });
  }
  suppliers() { return this.http.get<Supplier[]>(`${API_URL}/suppliers`); }
  saveSupplier(s: Supplier) {
    return s.id ? this.http.put<Supplier>(`${API_URL}/suppliers/${s.id}`, s) : this.http.post<Supplier>(`${API_URL}/suppliers`, s);
  }
  purchases() { return this.http.get<Purchase[]>(`${API_URL}/purchases`); }
  createPurchase(r: PurchaseRequest) { return this.http.post<Purchase>(`${API_URL}/purchases`, r); }

  // Clientes y crédito
  customers(q = '') { return this.http.get<Customer[]>(`${API_URL}/customers`, { params: new HttpParams().set('q', q) }); }
  saveCustomer(c: Customer) {
    return c.id ? this.http.put<Customer>(`${API_URL}/customers/${c.id}`, c) : this.http.post<Customer>(`${API_URL}/customers`, c);
  }
  customerCredit(id: number) { return this.http.get<CustomerCredit>(`${API_URL}/customers/${id}/credit`); }
  creditHistory(id: number) { return this.http.get<CreditTransaction[]>(`${API_URL}/customers/${id}/credit-history`); }
  pay(id: number, amount: number, method: string, note: string) {
    return this.http.post<CreditTransaction>(`${API_URL}/customers/${id}/payments`, { amount, method, note });
  }
  receivables() { return this.http.get<Receivable[]>(`${API_URL}/customers/receivables`); }

  // Facturación
  invoices(from?: string, to?: string) {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<Invoice[]>(`${API_URL}/invoices`, { params });
  }
  customerInvoices(customerId: number) {
    return this.http.get<Invoice[]>(`${API_URL}/invoices`, { params: new HttpParams().set('customerId', customerId) });
  }
  createInvoice(r: InvoiceRequest) { return this.http.post<Invoice>(`${API_URL}/invoices`, r); }
  cancelInvoice(id: number) { return this.http.post<Invoice>(`${API_URL}/invoices/${id}/cancel`, {}); }
  invoicePdf(id: number) { return this.http.get(`${API_URL}/invoices/${id}/pdf`, { responseType: 'blob' }); }

  // Contabilidad
  accounts() { return this.http.get<Account[]>(`${API_URL}/accounting/accounts`); }
  journal(from: string, to: string) { return this.http.get<JournalEntry[]>(`${API_URL}/accounting/journal`, { params: this.range(from, to) }); }
  trialBalance(from: string, to: string) {
    return this.http.get<TrialBalanceRow[]>(`${API_URL}/accounting/reports/trial-balance`, { params: this.range(from, to) });
  }
  incomeStatement(from: string, to: string) {
    return this.http.get<IncomeStatement>(`${API_URL}/accounting/reports/income-statement`, { params: this.range(from, to) });
  }
  expenses(from: string, to: string) { return this.http.get<Expense[]>(`${API_URL}/accounting/expenses`, { params: this.range(from, to) }); }
  createExpense(e: { date: string; accountId: number; description: string; amount: number }) {
    return this.http.post<Expense>(`${API_URL}/accounting/expenses`, e);
  }
}

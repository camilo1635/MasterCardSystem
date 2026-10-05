import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'login', title: 'Iniciar sesión', loadComponent: () => import('./pages/login.component').then(m => m.LoginComponent) },
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      { path: 'dashboard', title: 'Resumen', loadComponent: () => import('./pages/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'productos', title: 'Productos', loadComponent: () => import('./pages/products.component').then(m => m.ProductsComponent) },
      { path: 'inventario', title: 'Inventario', loadComponent: () => import('./pages/inventory.component').then(m => m.InventoryComponent) },
      { path: 'compras', title: 'Compras', canActivate: [roleGuard('ADMIN', 'CONTADOR')], loadComponent: () => import('./pages/purchases.component').then(m => m.PurchasesComponent) },
      { path: 'clientes', title: 'Clientes y crédito', loadComponent: () => import('./pages/customers.component').then(m => m.CustomersComponent) },
      { path: 'facturacion', title: 'Facturación', loadComponent: () => import('./pages/invoicing.component').then(m => m.InvoicingComponent) },
      { path: 'contabilidad', title: 'Contabilidad', canActivate: [roleGuard('ADMIN', 'CONTADOR')], loadComponent: () => import('./pages/accounting.component').then(m => m.AccountingComponent) },
      { path: 'usuarios', title: 'Usuarios', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/users.component').then(m => m.UsersComponent) },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];

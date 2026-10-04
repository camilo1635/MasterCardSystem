import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';

/** Módulos comunes que usan todas las páginas. */
export const SHARED_IMPORTS = [
  FormsModule, CurrencyPipe, DatePipe, DecimalPipe, MatButtonModule, MatCheckboxModule, MatFormFieldModule,
  MatIconModule, MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule, MatTabsModule,
];

export function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function monthStart(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toLocaleDateString('sv-SE');
}

export { MatSnackBar };

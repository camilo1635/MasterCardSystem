import { ChangeDetectionStrategy, Component, Injectable, input, signal } from '@angular/core';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';

/** Estado de paginación en cliente de una tabla; se usa con <app-pager>. */
export class Pager {
  readonly index = signal(0);
  readonly size = signal(10);

  /** Página efectiva: si los datos se reducen (filtros), nunca queda fuera de rango. */
  page(length: number): number {
    const last = Math.max(0, Math.ceil(length / this.size()) - 1);
    return Math.min(this.index(), last);
  }

  slice<T>(items: T[]): T[] {
    const start = this.page(items.length) * this.size();
    return items.slice(start, start + this.size());
  }

  reset() { this.index.set(0); }

  change(e: PageEvent) {
    this.index.set(e.pageIndex);
    this.size.set(e.pageSize);
  }
}

/** Textos del paginador en español. */
@Injectable()
export class SpanishPaginatorIntl extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Filas por página';
  override nextPageLabel = 'Página siguiente';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primera página';
  override lastPageLabel = 'Última página';
  override getRangeLabel = (page: number, pageSize: number, length: number) => {
    if (length === 0) return '0 de 0';
    const start = page * pageSize;
    return `${start + 1} – ${Math.min(start + pageSize, length)} de ${length}`;
  };
}

@Component({
  selector: 'app-pager',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatPaginatorModule],
  // Textos en español, solo para el paginador de este componente (mantiene el código en los chunks diferidos).
  providers: [{ provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  template: `
    @if (length() > 10) {
      <mat-paginator [length]="length()" [pageIndex]="pager().page(length())" [pageSize]="pager().size()"
                     [pageSizeOptions]="[10, 25, 50, 100]" showFirstLastButtons (page)="pager().change($event)" />
    }
  `,
})
export class PagerComponent {
  pager = input.required<Pager>();
  length = input.required<number>();
}

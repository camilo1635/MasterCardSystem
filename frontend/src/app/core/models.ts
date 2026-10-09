export interface Product {
  id?: number; sku: string; name: string; brand?: string;
  cost: number; price: number; ivaRate: number; stock: number;
}

export interface Customer {
  id?: number; document: string; name: string; phone?: string; email?: string; address?: string;
  active: boolean;
}

export interface Supplier { id?: number; nit?: string; name: string; phone?: string; email?: string; address?: string; }

export interface SalesReturnItem {
  invoiceItemId: number; productId: number; description: string; quantity: number; unitPrice: number; ivaRate: number;
}

export interface SalesReturn {
  id: number; number: number; invoiceId: number; customerId?: number; date: string; subtotal: number; iva: number; total: number;
  creditApplied: number; cashRefund: number; reason?: string; items: SalesReturnItem[];
}

export interface Returnable {
  invoiceItemId: number; productId: number; description: string; sold: number; returned: number;
  available: number; unitPrice: number; ivaRate: number;
}

export interface ReturnRequest {
  invoiceId: number; reason?: string; items: { invoiceItemId: number; quantity: number }[];
}

export interface InvoiceItem { id?: number; productId: number; description: string; quantity: number; unitPrice: number; ivaRate: number; }

export interface Invoice {
  id: number; number: number; customerId?: number; date: string; paymentType: 'CONTADO' | 'CREDITO';
  status: 'EMITIDA' | 'ANULADA'; returnStatus?: 'NINGUNA' | 'PARCIAL' | 'TOTAL'; paid?: number; pending?: number; subtotal: number; iva: number; total: number; notes?: string; items: InvoiceItem[];
}

export interface InvoiceRequest {
  customerId?: number | null; paymentType: string; notes?: string;
  items: { productId: number; quantity: number; unitPrice?: number }[];
}

export interface CreditTransaction {
  id: number; type: 'CARGO' | 'ABONO' | 'REVERSO'; amount: number; balance: number;
  invoiceId?: number; method?: string; note?: string; createdAt: string;
}

export interface CustomerCredit { customer: Customer; balance: number; }
export interface Receivable {
  customerId: number; name: string; document: string; phone?: string; balance: number; invoices: number;
}
export interface PaymentResult { balance: number; invoices: number; }

export interface InventoryMovement {
  id: number; productId: number; type: string; quantity: number; unitCost: number;
  reference?: string; note?: string; createdAt: string;
}

export interface Purchase {
  id: number; supplier: Supplier; docNumber?: string; date: string; paymentType: string;
  subtotal: number; iva: number; total: number;
}

export interface PurchaseRequest {
  supplierId: number; docNumber?: string; date?: string; paymentType: string;
  items: { productId: number; quantity: number; unitCost: number }[];
}

export interface Account { id: number; code: string; name: string; type: string; }
export interface JournalLine { accountCode: string; accountName: string; debit: number; credit: number; }
export interface JournalEntry { id: number; date: string; description: string; source: string; sourceId?: number; lines: JournalLine[]; }
export interface Expense { id: number; date: string; account: Account; description: string; amount: number; }
export interface TrialBalanceRow { code: string; name: string; type: string; debit: number; credit: number; balance: number; }
export interface IncomeStatement { income: number; costOfSales: number; grossProfit: number; expenses: number; netProfit: number; }

export interface DashboardSummary {
  salesToday: number; invoicesToday: number; salesMonth: number; receivable: number;
}

export interface AuthUser { id: number; username: string; }
export interface AuthResponse { accessToken: string; tokenType: string; expiresIn: number; user: AuthUser; }

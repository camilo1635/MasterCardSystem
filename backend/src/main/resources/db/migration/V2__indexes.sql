-- Índices para las consultas de listados, dashboard, reportes y relaciones hijas.
CREATE INDEX idx_invoice_date          ON invoice (date);
CREATE INDEX idx_invoice_item_invoice  ON invoice_item (invoice_id);
CREATE INDEX idx_purchase_item_purchase ON purchase_item (purchase_id);
CREATE INDEX idx_purchase_date         ON purchase (date DESC, id DESC);
CREATE INDEX idx_journal_entry_date    ON journal_entry (date);
CREATE INDEX idx_journal_entry_source  ON journal_entry (source, source_id);
CREATE INDEX idx_journal_line_entry    ON journal_line (entry_id);
CREATE INDEX idx_expense_date          ON expense (date);

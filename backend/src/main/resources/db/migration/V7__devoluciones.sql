-- ===== Devoluciones de venta =====
CREATE SEQUENCE sales_return_number_seq START 1;

CREATE TABLE sales_return (
    id          BIGSERIAL PRIMARY KEY,
    number      BIGINT         NOT NULL UNIQUE,
    invoice_id  BIGINT         NOT NULL REFERENCES invoice (id),
    date        TIMESTAMP      NOT NULL DEFAULT now(),
    subtotal    NUMERIC(14, 2) NOT NULL,
    iva         NUMERIC(14, 2) NOT NULL,
    total       NUMERIC(14, 2) NOT NULL,
    credit_applied NUMERIC(14, 2) NOT NULL DEFAULT 0, -- parte descontada del saldo del cliente
    cash_refund    NUMERIC(14, 2) NOT NULL DEFAULT 0, -- parte devuelta en efectivo
    reason      VARCHAR(300)
);
CREATE INDEX idx_sales_return_invoice ON sales_return (invoice_id);
CREATE INDEX idx_sales_return_date ON sales_return (date);

CREATE TABLE sales_return_item (
    id              BIGSERIAL PRIMARY KEY,
    return_id       BIGINT         NOT NULL REFERENCES sales_return (id) ON DELETE CASCADE,
    invoice_item_id BIGINT         NOT NULL REFERENCES invoice_item (id),
    product_id      BIGINT         NOT NULL REFERENCES product (id),
    description     VARCHAR(200)   NOT NULL,
    quantity        INT            NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(14, 2) NOT NULL,
    unit_cost       NUMERIC(14, 2) NOT NULL,
    iva_rate        NUMERIC(5, 2)  NOT NULL
);
CREATE INDEX idx_sales_return_item_invoice_item ON sales_return_item (invoice_item_id);

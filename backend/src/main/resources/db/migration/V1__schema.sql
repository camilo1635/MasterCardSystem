-- ===== Catálogo e inventario =====
CREATE TABLE category (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE product (
    id           BIGSERIAL PRIMARY KEY,
    sku          VARCHAR(50)    NOT NULL UNIQUE,
    name         VARCHAR(200)   NOT NULL,
    brand        VARCHAR(100),
    category_id  BIGINT REFERENCES category (id),
    cost         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    price        NUMERIC(14, 2) NOT NULL,
    iva_rate     NUMERIC(5, 2)  NOT NULL DEFAULT 19,
    stock        INT            NOT NULL DEFAULT 0,
    min_stock    INT            NOT NULL DEFAULT 0,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT product_stock_non_negative CHECK (stock >= 0)
);

CREATE TABLE inventory_movement (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT         NOT NULL REFERENCES product (id),
    type        VARCHAR(20)    NOT NULL,  -- ENTRADA, SALIDA, AJUSTE
    quantity    INT            NOT NULL,  -- con signo: positivo suma, negativo resta
    unit_cost   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    reference   VARCHAR(100),
    note        VARCHAR(300),
    created_at  TIMESTAMP      NOT NULL DEFAULT now()
);
CREATE INDEX idx_inv_mov_product ON inventory_movement (product_id, created_at DESC);

-- ===== Proveedores y compras =====
CREATE TABLE supplier (
    id       BIGSERIAL PRIMARY KEY,
    nit      VARCHAR(30),
    name     VARCHAR(200) NOT NULL,
    phone    VARCHAR(30),
    email    VARCHAR(150),
    address  VARCHAR(250)
);

CREATE TABLE purchase (
    id           BIGSERIAL PRIMARY KEY,
    supplier_id  BIGINT         NOT NULL REFERENCES supplier (id),
    doc_number   VARCHAR(50),
    date         DATE           NOT NULL,
    payment_type VARCHAR(10)    NOT NULL, -- CONTADO, CREDITO
    subtotal     NUMERIC(14, 2) NOT NULL,
    iva          NUMERIC(14, 2) NOT NULL,
    total        NUMERIC(14, 2) NOT NULL
);

CREATE TABLE purchase_item (
    id          BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT         NOT NULL REFERENCES purchase (id) ON DELETE CASCADE,
    product_id  BIGINT         NOT NULL REFERENCES product (id),
    quantity    INT            NOT NULL CHECK (quantity > 0),
    unit_cost   NUMERIC(14, 2) NOT NULL,
    iva_rate    NUMERIC(5, 2)  NOT NULL
);

-- ===== Clientes =====
CREATE TABLE customer (
    id            BIGSERIAL PRIMARY KEY,
    document      VARCHAR(30)    NOT NULL UNIQUE,
    name          VARCHAR(200)   NOT NULL,
    phone         VARCHAR(30),
    email         VARCHAR(150),
    address       VARCHAR(250),
    credit_limit  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    active        BOOLEAN        NOT NULL DEFAULT TRUE
);

-- ===== Facturación =====
CREATE SEQUENCE invoice_number_seq START 1;

CREATE TABLE invoice (
    id           BIGSERIAL PRIMARY KEY,
    number       BIGINT         NOT NULL UNIQUE,
    customer_id  BIGINT REFERENCES customer (id),
    date         TIMESTAMP      NOT NULL DEFAULT now(),
    payment_type VARCHAR(10)    NOT NULL, -- CONTADO, CREDITO
    status       VARCHAR(10)    NOT NULL DEFAULT 'EMITIDA', -- EMITIDA, ANULADA
    subtotal     NUMERIC(14, 2) NOT NULL,
    iva          NUMERIC(14, 2) NOT NULL,
    total        NUMERIC(14, 2) NOT NULL,
    notes        VARCHAR(300)
);
CREATE INDEX idx_invoice_customer ON invoice (customer_id);

CREATE TABLE invoice_item (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT         NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    product_id  BIGINT         NOT NULL REFERENCES product (id),
    description VARCHAR(200)   NOT NULL,
    quantity    INT            NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(14, 2) NOT NULL,
    unit_cost   NUMERIC(14, 2) NOT NULL,
    iva_rate    NUMERIC(5, 2)  NOT NULL
);

-- ===== Crédito de clientes =====
CREATE TABLE credit_transaction (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT         NOT NULL REFERENCES customer (id),
    type         VARCHAR(10)    NOT NULL, -- CARGO, ABONO, REVERSO
    amount       NUMERIC(14, 2) NOT NULL, -- con signo: CARGO +, ABONO/REVERSO -
    balance      NUMERIC(14, 2) NOT NULL, -- saldo del cliente después del movimiento
    invoice_id   BIGINT REFERENCES invoice (id),
    method       VARCHAR(20),             -- EFECTIVO, TRANSFERENCIA, TARJETA (abonos)
    note         VARCHAR(300),
    created_at   TIMESTAMP      NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_customer ON credit_transaction (customer_id, id);

-- ===== Contabilidad =====
CREATE TABLE account (
    id    BIGSERIAL PRIMARY KEY,
    code  VARCHAR(10)  NOT NULL UNIQUE,
    name  VARCHAR(150) NOT NULL,
    type  VARCHAR(15)  NOT NULL -- ACTIVO, PASIVO, PATRIMONIO, INGRESO, COSTO, GASTO
);

CREATE TABLE journal_entry (
    id          BIGSERIAL PRIMARY KEY,
    date        DATE         NOT NULL,
    description VARCHAR(300) NOT NULL,
    source      VARCHAR(20)  NOT NULL, -- FACTURA, ANULACION, ABONO, COMPRA, GASTO, MANUAL
    source_id   BIGINT
);

CREATE TABLE journal_line (
    id          BIGSERIAL PRIMARY KEY,
    entry_id    BIGINT         NOT NULL REFERENCES journal_entry (id) ON DELETE CASCADE,
    account_id  BIGINT         NOT NULL REFERENCES account (id),
    debit       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    credit      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    CONSTRAINT line_one_side CHECK (debit >= 0 AND credit >= 0 AND (debit = 0 OR credit = 0))
);
CREATE INDEX idx_line_account ON journal_line (account_id);

CREATE TABLE expense (
    id          BIGSERIAL PRIMARY KEY,
    date        DATE           NOT NULL,
    account_id  BIGINT         NOT NULL REFERENCES account (id),
    description VARCHAR(300)   NOT NULL,
    amount      NUMERIC(14, 2) NOT NULL CHECK (amount > 0)
);

-- ===== Plan de cuentas semilla (PUC simplificado) =====
INSERT INTO account (code, name, type) VALUES
    ('1105', 'Caja', 'ACTIVO'),
    ('1110', 'Bancos', 'ACTIVO'),
    ('1305', 'Clientes (Cuentas por cobrar)', 'ACTIVO'),
    ('1435', 'Inventario de mercancías', 'ACTIVO'),
    ('2205', 'Proveedores', 'PASIVO'),
    ('2408', 'IVA por pagar', 'PASIVO'),
    ('3115', 'Capital', 'PATRIMONIO'),
    ('4135', 'Ingresos por ventas', 'INGRESO'),
    ('6135', 'Costo de ventas', 'COSTO'),
    ('5105', 'Gastos de personal', 'GASTO'),
    ('5120', 'Arrendamientos', 'GASTO'),
    ('5135', 'Servicios', 'GASTO'),
    ('5195', 'Gastos diversos', 'GASTO');

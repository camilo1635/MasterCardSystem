# MasterCard Sound – Sistema de gestión

Facturación, inventario, crédito de clientes y contabilidad para un negocio de audio y accesorios para carro.
Stack: Spring Boot 3 (Java 17) · Angular 18 + Material · PostgreSQL 16. Login JWT con roles (ver CLAUDE.md, sección Seguridad).

## Arranque

```bash
docker compose up -d                 # PostgreSQL en localhost:5433
cd backend && mvn spring-boot:run    # API en http://localhost:8080  (Swagger: /swagger-ui.html)
cd frontend && npm install && npx ng serve   # UI en http://localhost:4200
```

El puerto 5433 evita choques con un PostgreSQL nativo en 5432. Flyway crea el esquema y el plan de cuentas al iniciar.

## Tests

```bash
cd backend && mvn test -Dtest=SalesFlowIT    # requiere Docker (Testcontainers)
```

## Reglas de negocio

- **Precios** se guardan sin IVA; la factura suma IVA por producto (19% por defecto).
- **Factura** (transacción única): descuenta stock, valida cupo si es a crédito, registra el cargo y el asiento contable. Si algo falla, no queda nada a medias.
- **Anulación**: devuelve stock al costo vendido, revierte el cargo de crédito y genera el asiento inverso.
- **Costo** de producto = promedio ponderado, recalculado en cada compra.
- **Crédito**: cupo por cliente, abonos parciales (no pueden superar el saldo), historial con saldo corrido.
- **Contabilidad**: asientos de partida doble automáticos (venta, compra, abono, gasto, ajuste de inventario); balance de comprobación y estado de resultados por rango de fechas.

## Pendiente / siguientes fases

- Facturación electrónica DIAN (requiere proveedor tecnológico).
- Abonos aplicados a una factura específica y antigüedad de cartera.
- Si se anula una factura a crédito ya abonada, el cliente queda con saldo a favor (negativo).

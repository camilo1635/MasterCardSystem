# MasterCard Sound

Sistema de facturación, inventario, crédito de clientes y contabilidad para un negocio de audio y accesorios de carro. Idioma de la UI y los mensajes de error: español. Moneda: COP. Sin login por ahora.

## Stack y comandos
- `backend/` – Spring Boot 3.3, Java 17, Maven, JPA + Flyway, PostgreSQL. Paquete base `com.mastercard.system`.
- `frontend/` – Angular 18 (standalone components, signals) + Angular Material 18.
- DB: `docker compose up -d` (Postgres en **localhost:5433**; el 5432 lo ocupa un Postgres nativo de Windows, no tocarlo).

```bash
cd backend && mvn spring-boot:run              # API :8080, Swagger en /swagger-ui.html
cd backend && mvn test -Dtest=SalesFlowIT      # requiere Docker (Testcontainers)
cd frontend && npx ng serve                    # UI :4200
cd frontend && npx ng build                    # verifica que compile
```

## Arquitectura
- Módulos backend por paquete: `product`, `inventory` (movimientos, proveedores, compras), `customer`, `credit`, `sales` (facturas, PDF, dashboard), `accounting`, `common`.
- Esquema solo por migraciones Flyway (`db/migration`); `ddl-auto: validate`. Para cambiar el esquema, crear `V2__...sql`, nunca editar `V1`.
- Frontend: una página por módulo en `src/app/pages`, todas llaman a `core/api.service.ts`; tipos en `core/models.ts`; imports comunes en `core/shared.ts`.

## Reglas de negocio que no deben romperse
- **El stock solo cambia vía `InventoryService.move`** (con bloqueo de fila). Nunca editar `product.stock` directamente; la API ignora el stock al crear/editar productos.
- Factura, anulación, compra y abono son **una sola transacción** que toca inventario, crédito y contabilidad. Los servicios `InventoryService.move`, `CreditService.charge/reverseCharge` y `AccountingService.post/reverse` usan `Propagation.MANDATORY`: deben llamarse desde una transacción ya abierta.
- Todo asiento debe cuadrar (debe = haber); `AccountingService.post` lo valida.
- Precios se guardan **sin IVA**; el IVA se calcula por producto. Costo = promedio ponderado recalculado en cada compra.
- Crédito: `credit_transaction.amount` lleva signo (CARGO +, ABONO/REVERSO −); el saldo del cliente es la suma. El cupo se valida al cargar.
- Códigos de cuenta contable fijos en `AccountingService` (1105 Caja, 1305 Clientes, 1435 Inventario, 2408 IVA, 4135 Ingresos, 6135 Costo de ventas…); salen del seed de `V1`.

## Convenciones
- Dinero siempre `BigDecimal` (`NUMERIC(14,2)`), redondeo con `common.Money`. Errores de negocio: `BusinessException` (400); no encontrado: `NotFoundException` (404).
- Frontend: temas claro/oscuro con variables CSS `--app-*` en `styles.scss`; no usar colores fijos en componentes. Angular no admite spread (`...`) en templates: usar métodos.

## Pendiente
Login/roles, facturación electrónica DIAN, abonos aplicados a factura específica, antigüedad de cartera.

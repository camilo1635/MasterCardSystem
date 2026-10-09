# MasterCard Sound

Sistema de facturación, inventario, crédito de clientes y contabilidad para un negocio de audio y accesorios de carro. Idioma de la UI y los mensajes de error: español. Moneda: COP. Login JWT de un único usuario (dueño), sin roles.

## Stack y comandos
- `backend/` – Spring Boot 3.3, Java 17, Maven, JPA + Flyway, PostgreSQL. Paquete base `com.mastercard.system`.
- `frontend/` – Angular 18 (standalone components, signals) + Angular Material 18.
- DB: `docker compose up -d` (Postgres en **localhost:5433**; el 5432 lo ocupa un Postgres nativo de Windows, no tocarlo).

```bash
cd backend && mvn spring-boot:run "-Dspring-boot.run.profiles=dev"   # (funciona en PowerShell y bash) API :8080; Swagger en /swagger-ui.html solo con perfil dev
cd backend && mvn test -Dtest='SalesFlowIT,SecurityIT'   # requiere Docker (Testcontainers)
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
- Crédito: `credit_transaction.amount` lleva signo (CARGO +, ABONO/REVERSO −); el saldo del cliente es la suma. **No hay cupo de crédito** (la columna `customer.credit_limit` quedó sin uso, con default 0, y la entidad ya no la mapea).
- Abonos por factura: un ABONO lleva `invoice_id` de la factura a la que se aplicó. El saldo de una factura a crédito = total − abonos de esa factura − deuda descontada por devoluciones (`InvoiceService.enrich`). El abono desde la ficha del cliente (`InvoiceService.payCustomer`) se reparte entre sus facturas pendientes, de la más antigua a la más reciente.
- Cartera por cobrar = clientes con facturas a crédito con saldo pendiente (`InvoiceService.receivables`); el historial de crédito de un cliente lista solo sus abonos.
- Códigos de cuenta contable fijos en `AccountingService` (1105 Caja, 1305 Clientes, 1435 Inventario, 2408 IVA, 4135 Ingresos, 6135 Costo de ventas…); salen del seed de `V1`.

## Convenciones
- Dinero siempre `BigDecimal` (`NUMERIC(14,2)`), redondeo con `common.Money`. Errores de negocio: `BusinessException` (400); no encontrado: `NotFoundException` (404).
- Frontend: temas claro/oscuro con variables CSS `--app-*` en `styles.scss`; no usar colores fijos en componentes. Angular no admite spread (`...`) en templates: usar métodos.

## Seguridad (login)
- Paquete `security`: JWT HS256 stateless. Access token 30 min (`Authorization: Bearer`); refresh token 8 h en cookie `HttpOnly; SameSite=Strict` (path `/api/auth`, hash en tabla `refresh_token`, rotación con detección de reutilización). Bloqueo 15 min tras 5 intentos fallidos.
- Variables de entorno: `JWT_SECRET` (obligatoria, >=32 caracteres; la app no arranca sin ella salvo perfil `dev`), `COOKIE_SECURE` (true en prod con https), `ADMIN_USERNAME`/`ADMIN_PASSWORD` (siembran el usuario dueño solo si no hay usuarios; clave 10-72 caracteres), `CORS_ORIGINS` vía `app.cors-origins`. Ver `.env.example`. El perfil `dev` trae valores locales (admin / admin-dev-1234) y habilita Swagger; no usarlo en producción.
- No hay roles ni gestión de usuarios: cualquier usuario autenticado puede todo (el sistema tiene un único dueño). Migración `V4` eliminó `app_user.role`.
- Todo endpoint requiere token salvo `/api/auth/login|refresh|logout`. Error 401 en JSON `{"message": ...}`.

## Pendiente
Facturación electrónica DIAN, antigüedad de cartera, anulación/corrección de abonos ya registrados.

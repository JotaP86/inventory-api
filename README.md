# Inventory API

API REST para gestionar el stock de un almacén: catálogo de productos, entradas y salidas de mercancía, ajustes por recuento de inventario e historial de movimientos.

Está escrita en Java 21 con JDBC y H2, sin frameworks. El servidor HTTP es el que incluye el propio JDK.

## Funcionalidades

- Alta, consulta, edición y archivado de productos (el archivado conserva el historial).
- Movimientos de stock: entrada, salida y ajuste por recuento, con historial por producto.
- El stock nunca puede quedar en negativo, tampoco con peticiones simultáneas.
- Búsqueda por nombre o SKU, filtro por categoría, listado de productos con stock bajo y paginación.
- Errores en un formato JSON único, con códigos HTTP coherentes.

## Tecnologías

- Java 21
- Maven
- H2 (base de datos SQL embebida)
- Jackson (JSON)
- JUnit 5

## Estructura

```
src/main/java/com/warehouse/inventory
├── App.java, Application.java   arranque y ensamblado de las piezas
├── api/                         servidor HTTP, router y controladores
├── service/                     reglas de negocio y validaciones
├── repository/                  acceso a datos con JDBC
├── model/, dto/                 entidades y cuerpos de las peticiones
├── config/                      configuración y conexión a la base de datos
└── exception/                   errores de la API
src/main/resources/schema.sql    esquema de la base de datos
src/test/java                    tests de servicios y de la API
```

Cada petición recorre las mismas capas: controlador, servicio y repositorio. El controlador solo traduce HTTP, el servicio valida y aplica las reglas, y el repositorio contiene el SQL.

## Puesta en marcha

Requisitos: JDK 21 y Maven 3.9 o superior.

```bash
mvn clean package
java -jar target/inventory-api.jar
```

La API queda disponible en `http://localhost:8080`. Para arrancar con datos de ejemplo:

```bash
SEED_DEMO_DATA=true java -jar target/inventory-api.jar
```

En PowerShell: `$env:SEED_DEMO_DATA="true"; java -jar target/inventory-api.jar`

Los datos se guardan en el directorio `data/`.

### Configuración

Se hace con variables de entorno.

| Variable | Por defecto | Descripción |
|---|---|---|
| `PORT` | `8080` | Puerto HTTP |
| `DB_URL` | `jdbc:h2:file:./data/inventory;DB_CLOSE_DELAY=-1` | URL JDBC de la base de datos |
| `DB_USER` | `sa` | Usuario de la base de datos |
| `DB_PASSWORD` | vacío | Contraseña de la base de datos |
| `SEED_DEMO_DATA` | `false` | Carga productos de ejemplo si la base de datos está vacía |

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/health` | Estado del servicio |
| GET | `/api/products` | Lista productos |
| POST | `/api/products` | Crea un producto |
| GET | `/api/products/{id}` | Detalle de un producto |
| PUT | `/api/products/{id}` | Modifica nombre, categoría, precio y stock mínimo |
| DELETE | `/api/products/{id}` | Archiva un producto |
| POST | `/api/products/{id}/movements` | Registra un movimiento de stock |
| GET | `/api/products/{id}/movements` | Historial de un producto, del más reciente al más antiguo |
| GET | `/api/movements` | Últimos movimientos de todo el almacén |

Parámetros de `GET /api/products`: `q` (texto en nombre o SKU), `category`, `lowStock` (`true` para ver solo los que están en el mínimo o por debajo), `limit` (1 a 200, por defecto 50) y `offset`.

Tipos de movimiento: `ENTRY` suma unidades, `EXIT` las resta y `ADJUSTMENT` fija el stock en la cantidad contada.

### Ejemplos

Crear un producto:

```bash
curl -i -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"cinta-75","name":"Cinta adhesiva 75 mm","category":"Embalaje","price":2.10,"initialStock":60,"minStock":20}'
```

```
HTTP/1.1 201 Created
Location: /api/products/9

{"id":9,"sku":"CINTA-75","name":"Cinta adhesiva 75 mm","category":"Embalaje","price":2.10,"stock":60,"minStock":20,"createdAt":"2026-11-10T09:01:11.055664Z","updatedAt":"2026-11-10T09:01:11.055664Z","lowStock":false}
```

Registrar una salida:

```bash
curl -X POST http://localhost:8080/api/products/1/movements \
  -H "Content-Type: application/json" \
  -d '{"type":"EXIT","quantity":18,"reason":"Pedido 1042"}'
```

Intentar sacar más unidades de las que hay:

```json
{"status":409,"error":"Conflict","message":"Insufficient stock for CHAL-HV: available 18, requested 500"}
```

Datos inválidos:

```json
{"status":400,"error":"Bad Request","message":"Validation failed","details":["sku is required","name is required","price must not be negative"]}
```

## Decisiones de diseño

- **Transacciones y bloqueo de fila.** Registrar un movimiento lee el producto con `SELECT ... FOR UPDATE`, calcula el nuevo stock y guarda producto y movimiento en una única transacción. Si dos salidas llegan a la vez, la segunda espera a la primera y ve el stock ya actualizado. Hay un test con 30 salidas simultáneas sobre 10 unidades que comprueba que solo prosperan 10.
- **Historial con `stock_after`.** Cada movimiento guarda el stock resultante, lo que permite auditar la evolución de un producto sin recalcularla.
- **Archivado en lugar de borrado.** Eliminar un producto lo marca como inactivo. Así no se pierde el historial ni se rompen las referencias.
- **`BigDecimal` para el precio.** `double` comete errores de redondeo con importes.
- **SQL parametrizado.** Ningún dato del usuario se concatena en una consulta, y los comodines de `LIKE` se escapan en las búsquedas.
- **Restricciones en la base de datos.** `UNIQUE` en el SKU y `CHECK` para impedir stock o precios negativos, además de las validaciones del servicio.
- **Sin framework.** La API usa el servidor HTTP del JDK y un router propio de poco más de 100 líneas. Es una decisión deliberada para trabajar los fundamentos de HTTP y JDBC; en un proyecto de equipo lo normal sería Spring Boot.

## Tests

```bash
mvn test
```

Hay 36 tests:

- Servicios contra una base de datos H2 en memoria: validaciones, reglas de stock, búsqueda y paginación.
- Concurrencia: salidas simultáneas que no pueden superar el stock disponible.
- Integración: se arranca el servidor real y se prueban los endpoints por HTTP, incluidos los casos de error.

El workflow de GitHub Actions en `.github/workflows/ci.yml` compila y ejecuta los tests en cada push.

## Limitaciones y siguientes pasos

- No hay autenticación ni control de roles.
- Cada operación abre su propia conexión. Con más carga haría falta un pool de conexiones (por ejemplo HikariCP).
- El esquema se crea con un script al arrancar. Para evolucionarlo con seguridad convendría usar migraciones (Flyway).
- H2 es suficiente para desarrollo; en producción se usaría PostgreSQL o similar.
- Migrar a Spring Boot y documentar la API con OpenAPI.

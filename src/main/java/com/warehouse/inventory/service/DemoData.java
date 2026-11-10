package com.warehouse.inventory.service;

import com.warehouse.inventory.dto.CreateProductRequest;
import com.warehouse.inventory.dto.MovementRequest;
import com.warehouse.inventory.model.MovementType;
import com.warehouse.inventory.model.Product;
import java.math.BigDecimal;

public final class DemoData {

    private DemoData() {
    }

    public static void loadIfEmpty(ProductService products, StockService stock) {
        if (!products.list(null, null, false, 1, 0).isEmpty()) {
            return;
        }
        Product pallet = create(products, "PAL-EUR-120", "Palé EUR 1200x800", "Embalaje", "14.50", 120, 40);
        Product box = create(products, "CAJ-40X30", "Caja de cartón 40x30x30", "Embalaje", "0.85", 800, 300);
        Product tape = create(products, "CINTA-48", "Cinta adhesiva 48 mm", "Embalaje", "1.20", 45, 50);
        create(products, "FILM-500", "Film extensible 500 mm", "Embalaje", "6.90", 30, 20);
        Product gloves = create(products, "GUANT-L", "Guantes de seguridad talla L", "EPI", "2.35", 60, 25);
        create(products, "CHAL-HV", "Chaleco alta visibilidad", "EPI", "4.10", 18, 20);
        create(products, "ETIQ-100X150", "Etiquetas térmicas 100x150 (rollo)", "Etiquetado", "7.75", 24, 10);
        create(products, "TRANS-2500", "Transpaleta manual 2500 kg", "Maquinaria", "289.00", 3, 2);

        move(stock, pallet, MovementType.EXIT, 18, "Pedido 1042");
        move(stock, box, MovementType.EXIT, 150, "Pedido 1043");
        move(stock, tape, MovementType.EXIT, 12, "Pedido 1043");
        move(stock, gloves, MovementType.ENTRY, 40, "Recepción proveedor");
        move(stock, pallet, MovementType.ADJUSTMENT, 100, "Recuento de inventario");
    }

    private static Product create(ProductService service, String sku, String name, String category,
                                  String price, int stock, int minStock) {
        return service.create(new CreateProductRequest(sku, name, category, new BigDecimal(price), stock,
                minStock));
    }

    private static void move(StockService service, Product product, MovementType type, int quantity,
                             String reason) {
        service.register(product.id(), new MovementRequest(type, quantity, reason));
    }
}

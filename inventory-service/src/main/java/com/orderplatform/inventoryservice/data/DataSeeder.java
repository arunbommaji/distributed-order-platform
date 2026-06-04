package com.orderplatform.inventoryservice.data;

import com.orderplatform.inventoryservice.domain.Product;
import com.orderplatform.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            log.info("Products already seeded — skipping");
            return;
        }

        List<Product> products = List.of(
                product("PROD-001", "Wireless Headphones",  150, 0),
                product("PROD-002", "Mechanical Keyboard",  75,  0),
                product("PROD-003", "USB-C Hub",            200, 0),
                product("PROD-004", "4K Webcam",            50,  0),
                product("PROD-005", "Desk Lamp",            300, 0),
                product("PROD-006", "Mouse Pad XL",         500, 0),
                product("PROD-007", "Laptop Stand",         120, 0),
                product("PROD-008", "Noise Cancelling Mic", 80,  0)
        );

        productRepository.saveAll(products);
        log.info("Seeded {} products", products.size());
    }

    private Product product(String id, String name, int qty, int reserved) {
        return Product.builder()
                .id(UUID.randomUUID())
                .productId(id)
                .productName(name)
                .availableQuantity(qty)
                .reservedQuantity(reserved)
                .build();
    }
}

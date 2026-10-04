package com.mastercard.system.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findByProductIdOrderByIdDesc(Long productId);

    List<InventoryMovement> findTop200ByOrderByIdDesc();
}

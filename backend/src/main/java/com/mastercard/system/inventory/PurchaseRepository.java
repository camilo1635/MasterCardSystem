package com.mastercard.system.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    @Query("select distinct p from Purchase p left join fetch p.items order by p.date desc, p.id desc")
    List<Purchase> findAllWithItems();
}

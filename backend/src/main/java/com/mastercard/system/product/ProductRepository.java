package com.mastercard.system.product;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p from Product p
            where lower(p.name) like lower(concat('%', :q, '%'))
               or lower(p.sku) like lower(concat('%', :q, '%'))
               or lower(coalesce(p.brand, '')) like lower(concat('%', :q, '%'))
            order by p.name""")
    List<Product> search(@Param("q") String q);

    @Query("select p from Product p where p.active = true and p.stock <= p.minStock order by p.stock")
    List<Product> findLowStock();

}

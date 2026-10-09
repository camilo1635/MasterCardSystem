package com.mastercard.system.sales;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    Long nextNumber();

    @Query("select distinct i from Invoice i left join fetch i.items where i.id = :id")
    Optional<Invoice> findWithItems(@Param("id") Long id);

    /** Bloquea la fila: serializa devoluciones y anulaciones concurrentes sobre la misma factura. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    @Query("select distinct i from Invoice i left join fetch i.items where i.date >= :from and i.date < :to order by i.id desc")
    List<Invoice> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select distinct i from Invoice i left join fetch i.items where i.customerId = :customerId order by i.id desc")
    List<Invoice> findByCustomer(@Param("customerId") Long customerId);

    @Query("select coalesce(sum(i.total), 0) from Invoice i where i.status = 'EMITIDA' and i.date >= :from and i.date < :to")
    BigDecimal salesTotal(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(i) from Invoice i where i.status = 'EMITIDA' and i.date >= :from and i.date < :to")
    long salesCount(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

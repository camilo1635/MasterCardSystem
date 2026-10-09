package com.mastercard.system.sales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {

    @Query(value = "select nextval('sales_return_number_seq')", nativeQuery = true)
    Long nextNumber();

    @Query("select distinct r from SalesReturn r left join fetch r.items where r.id = :id")
    Optional<SalesReturn> findWithItems(@Param("id") Long id);

    @Query("select distinct r from SalesReturn r left join fetch r.items where r.date >= :from and r.date < :to order by r.id desc")
    List<SalesReturn> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select distinct r from SalesReturn r left join fetch r.items where r.invoiceId = :invoiceId order by r.id desc")
    List<SalesReturn> findByInvoice(@Param("invoiceId") Long invoiceId);

    boolean existsByInvoiceId(Long invoiceId);

    /** Unidades ya devueltas por ítem de factura: filas [invoiceItemId, cantidad]. */
    @Query("select i.invoiceItemId, sum(i.quantity) from SalesReturnItem i "
            + "where i.salesReturn.invoiceId = :invoiceId group by i.invoiceItemId")
    List<Object[]> returnedQuantities(@Param("invoiceId") Long invoiceId);

    /** Unidades devueltas por factura, para varias facturas a la vez: filas [invoiceId, cantidad]. */
    @Query("select r.invoiceId, sum(i.quantity) from SalesReturnItem i join i.salesReturn r "
            + "where r.invoiceId in :invoiceIds group by r.invoiceId")
    List<Object[]> returnedByInvoice(@Param("invoiceIds") java.util.Collection<Long> invoiceIds);

    /** [invoiceId, monto] de las devoluciones que ya descontaron deuda de cada factura. */
    @Query("select r.invoiceId, sum(r.creditApplied) from SalesReturn r where r.invoiceId in :invoiceIds group by r.invoiceId")
    List<Object[]> creditAppliedByInvoice(@Param("invoiceIds") java.util.Collection<Long> invoiceIds);

    @Query("select coalesce(sum(r.total), 0) from SalesReturn r where r.date >= :from and r.date < :to")
    BigDecimal returnsTotal(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

package com.mastercard.system.credit;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    List<CreditTransaction> findByCustomerIdOrderByIdDesc(Long customerId);

    @Query("select coalesce(sum(t.amount), 0) from CreditTransaction t where t.customerId = :customerId")
    BigDecimal balanceOf(@Param("customerId") Long customerId);

    @Query("select coalesce(sum(t.amount), 0) from CreditTransaction t")
    BigDecimal totalReceivable();

    /** [invoiceId, abonado] por factura: suma de los abonos aplicados a cada una (en positivo). */
    @Query("select t.invoiceId, -sum(t.amount) from CreditTransaction t "
            + "where t.type = com.mastercard.system.credit.CreditTransaction.Type.ABONO and t.invoiceId in :invoiceIds "
            + "group by t.invoiceId")
    List<Object[]> paidByInvoice(@Param("invoiceIds") java.util.Collection<Long> invoiceIds);

    /** [customerId, saldo] de los clientes con deuda. */
    @Query("select t.customerId, sum(t.amount) from CreditTransaction t group by t.customerId having sum(t.amount) <> 0")
    List<Object[]> balancesByCustomer();
}

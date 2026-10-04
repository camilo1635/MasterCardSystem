package com.mastercard.system.sales;

import com.mastercard.system.credit.CreditTransactionRepository;
import com.mastercard.system.product.Product;
import com.mastercard.system.product.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    public record Summary(BigDecimal salesToday, long invoicesToday, BigDecimal salesMonth,
                          BigDecimal receivable, List<Product> lowStock) {}

    private final InvoiceRepository invoices;
    private final CreditTransactionRepository credit;
    private final ProductRepository products;

    @GetMapping
    @Transactional(readOnly = true)
    public Summary summary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        return new Summary(
                invoices.salesTotal(today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                invoices.salesCount(today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                invoices.salesTotal(monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                credit.totalReceivable(),
                products.findLowStock());
    }
}

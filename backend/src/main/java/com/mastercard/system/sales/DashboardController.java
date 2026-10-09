package com.mastercard.system.sales;

import com.mastercard.system.product.Product;
import com.mastercard.system.product.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final SalesReturnRepository returns;
    private final InvoiceService invoiceService;
    private final ProductRepository products;

    @GetMapping
    @Transactional(readOnly = true)
    public Summary summary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        // Ventas netas: las devoluciones del período se restan del total facturado.
        return new Summary(
                invoices.salesTotal(dayStart, dayEnd).subtract(returns.returnsTotal(dayStart, dayEnd)),
                invoices.salesCount(dayStart, dayEnd),
                invoices.salesTotal(monthStart.atStartOfDay(), dayEnd)
                        .subtract(returns.returnsTotal(monthStart.atStartOfDay(), dayEnd)),
                // Por cobrar = saldo de las facturas a crédito pendientes (la misma cartera de Clientes).
                invoiceService.receivables().stream().map(InvoiceService.Receivable::balance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                products.findLowStock());
    }
}

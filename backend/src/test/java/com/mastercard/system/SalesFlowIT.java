package com.mastercard.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastercard.system.accounting.AccountingService;
import com.mastercard.system.common.BusinessException;
import com.mastercard.system.credit.CreditService;
import com.mastercard.system.customer.Customer;
import com.mastercard.system.customer.CustomerRepository;
import com.mastercard.system.inventory.InventoryService;
import com.mastercard.system.inventory.PurchaseService;
import com.mastercard.system.inventory.PurchaseService.ItemRequest;
import com.mastercard.system.inventory.PurchaseService.PurchaseRequest;
import com.mastercard.system.inventory.Supplier;
import com.mastercard.system.inventory.SupplierRepository;
import com.mastercard.system.product.Product;
import com.mastercard.system.product.ProductRepository;
import com.mastercard.system.sales.Invoice;
import com.mastercard.system.sales.InvoiceService;
import com.mastercard.system.sales.InvoiceService.InvoiceRequest;
import com.mastercard.system.sales.SalesReturn;
import com.mastercard.system.sales.SalesReturnService;
import com.mastercard.system.sales.SalesReturnService.ReturnItemRequest;
import com.mastercard.system.sales.SalesReturnService.ReturnRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SalesFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired ProductRepository products;
    @Autowired CustomerRepository customers;
    @Autowired SupplierRepository suppliers;
    @Autowired PurchaseService purchases;
    @Autowired InvoiceService invoices;
    @Autowired SalesReturnService returns;
    @Autowired CreditService credit;
    @Autowired InventoryService inventory;
    @Autowired AccountingService accounting;

    private Product product() {
        Product p = new Product();
        p.setSku("SKU-" + System.nanoTime());
        p.setName("Parlante 6.5");
        p.setPrice(new BigDecimal("100000"));
        return products.save(p);
    }

    private Customer customer(String limit) {
        Customer c = new Customer();
        c.setDocument("D" + System.nanoTime());
        c.setName("Cliente prueba");
        c.setCreditLimit(new BigDecimal(limit));
        return customers.save(c);
    }

    private void stock(Product p, int qty, String cost) {
        Supplier s = new Supplier();
        s.setName("Proveedor");
        s = suppliers.save(s);
        purchases.create(new PurchaseRequest(s.getId(), "F-1", LocalDate.now(), "CONTADO",
                List.of(new ItemRequest(p.getId(), qty, new BigDecimal(cost)))));
    }

    @Test
    void creditSaleLifecycle() {
        Product p = product();
        stock(p, 10, "60000");
        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isEqualTo(10);

        Customer c = customer("500000");
        Invoice inv = invoices.create(new InvoiceRequest(c.getId(), "CREDITO", null,
                List.of(new InvoiceService.ItemRequest(p.getId(), 2, null))));
        // 2 x 100.000 + IVA 19% = 238.000
        assertThat(inv.getTotal()).isEqualByComparingTo("238000");
        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(credit.balanceOf(c.getId())).isEqualByComparingTo("238000");

        credit.pay(c.getId(), new BigDecimal("100000"), "EFECTIVO", null);
        assertThat(credit.balanceOf(c.getId())).isEqualByComparingTo("138000");
        assertThatThrownBy(() -> credit.pay(c.getId(), new BigDecimal("999999"), "EFECTIVO", null))
                .isInstanceOf(BusinessException.class);

        invoices.cancel(inv.getId());
        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(credit.balanceOf(c.getId())).isEqualByComparingTo("-100000");

        // La contabilidad siempre cuadra: débitos == créditos.
        var tb = accounting.trialBalance(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        BigDecimal debits = tb.stream().map(r -> r.debit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = tb.stream().map(r -> r.credit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo(credits);
    }

    @Test
    void partialReturnsRestockAndSettleCredit() {
        Product p = product();
        stock(p, 10, "60000");
        Customer c = customer("500000");
        Invoice inv = invoices.create(new InvoiceRequest(c.getId(), "CREDITO", null,
                List.of(new InvoiceService.ItemRequest(p.getId(), 2, null))));
        Long itemId = invoices.get(inv.getId()).getItems().get(0).getId();
        credit.pay(c.getId(), new BigDecimal("100000"), "EFECTIVO", null); // saldo 138.000

        // 1 unidad (119.000) se descuenta completa del saldo y vuelve al inventario.
        SalesReturn first = returns.create(new ReturnRequest(inv.getId(), "No le sirvió",
                List.of(new ReturnItemRequest(itemId, 1))));
        assertThat(first.getTotal()).isEqualByComparingTo("119000");
        assertThat(first.getCreditApplied()).isEqualByComparingTo("119000");
        assertThat(first.getCashRefund()).isEqualByComparingTo("0");
        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isEqualTo(9);
        assertThat(credit.balanceOf(c.getId())).isEqualByComparingTo("19000");

        // La segunda unidad supera el saldo: 19.000 al crédito y 100.000 en efectivo.
        SalesReturn second = returns.create(new ReturnRequest(inv.getId(), null,
                List.of(new ReturnItemRequest(itemId, 1))));
        assertThat(second.getCreditApplied()).isEqualByComparingTo("19000");
        assertThat(second.getCashRefund()).isEqualByComparingTo("100000");
        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(credit.balanceOf(c.getId())).isEqualByComparingTo("0");

        // Ya no queda nada por devolver y la factura no puede anularse.
        assertThatThrownBy(() -> returns.create(new ReturnRequest(inv.getId(), null,
                List.of(new ReturnItemRequest(itemId, 1)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("máximo 0");
        assertThatThrownBy(() -> invoices.cancel(inv.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("devoluciones");

        var tb = accounting.trialBalance(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        BigDecimal debits = tb.stream().map(r -> r.debit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = tb.stream().map(r -> r.credit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo(credits);
    }

    @Test
    void rejectsInsufficientStockAndExceededCredit() {
        Product p = product();
        stock(p, 1, "60000");
        Customer small = customer("50000");

        assertThatThrownBy(() -> invoices.create(new InvoiceRequest(small.getId(), "CREDITO", null,
                List.of(new InvoiceService.ItemRequest(p.getId(), 1, null)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Cupo");
        assertThatThrownBy(() -> invoices.create(new InvoiceRequest(null, "CONTADO", null,
                List.of(new InvoiceService.ItemRequest(p.getId(), 5, null)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Cantidad insuficiente");

        // Los fallos revierten todo: el stock no cambió.
        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isEqualTo(1);
    }
}

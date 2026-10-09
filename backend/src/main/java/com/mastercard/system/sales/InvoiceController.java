package com.mastercard.system.sales;

import com.mastercard.system.sales.InvoiceService.InvoiceRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private final InvoiceService service;
    private final InvoiceRepository repo;
    private final SalesReturnRepository returns;
    private final InvoicePdfService pdf;

    @GetMapping
    @Transactional(readOnly = true)
    public List<Invoice> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) Long customerId) {
        if (customerId != null) {
            return withReturnStatus(repo.findByCustomer(customerId));
        }
        LocalDate f = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now();
        return withReturnStatus(repo.findBetween(f.atStartOfDay(), t.plusDays(1).atStartOfDay()));
    }

    /** Marca cada factura como sin devoluciones, con devolución parcial o totalmente devuelta (una consulta). */
    private List<Invoice> withReturnStatus(List<Invoice> list) {
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, Long> returned = new HashMap<>();
        for (Object[] row : returns.returnedByInvoice(list.stream().map(Invoice::getId).toList())) {
            returned.put((Long) row[0], ((Number) row[1]).longValue());
        }
        for (Invoice inv : list) {
            long done = returned.getOrDefault(inv.getId(), 0L);
            long sold = inv.getItems().stream().mapToLong(InvoiceItem::getQuantity).sum();
            inv.setReturnStatus(done == 0 ? "NINGUNA" : done >= sold ? "TOTAL" : "PARCIAL");
        }
        return list;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Invoice get(@PathVariable Long id) {
        return withReturnStatus(List.of(service.get(id))).get(0);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Invoice create(@Valid @RequestBody InvoiceRequest r) {
        return service.create(r);
    }

    @PostMapping("/{id}/cancel")
    public Invoice cancel(@PathVariable Long id, Authentication auth) {
        Invoice inv = service.cancel(id);
        log.info("Factura {} anulada por {}", id, auth.getName());
        return inv;
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] bytes = pdf.render(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=factura-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}

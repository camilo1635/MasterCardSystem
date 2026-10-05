package com.mastercard.system.sales;

import org.springframework.security.access.prepost.PreAuthorize;
import com.mastercard.system.sales.InvoiceService.InvoiceRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
    private final InvoicePdfService pdf;

    @GetMapping
    @Transactional(readOnly = true)
    public List<Invoice> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) Long customerId) {
        if (customerId != null) {
            return repo.findByCustomer(customerId);
        }
        LocalDate f = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now();
        return repo.findBetween(f.atStartOfDay(), t.plusDays(1).atStartOfDay());
    }

    @GetMapping("/{id}")
    public Invoice get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public Invoice create(@Valid @RequestBody InvoiceRequest r) {
        return service.create(r);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
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

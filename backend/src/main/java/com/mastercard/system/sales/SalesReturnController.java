package com.mastercard.system.sales;

import com.mastercard.system.sales.SalesReturnService.Returnable;
import com.mastercard.system.sales.SalesReturnService.ReturnRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@Slf4j
public class SalesReturnController {

    private final SalesReturnService service;
    private final SalesReturnRepository repo;

    @GetMapping
    @Transactional(readOnly = true)
    public List<SalesReturn> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) Long invoiceId) {
        if (invoiceId != null) {
            return repo.findByInvoice(invoiceId);
        }
        LocalDate f = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now();
        return repo.findBetween(f.atStartOfDay(), t.plusDays(1).atStartOfDay());
    }

    @GetMapping("/{id}")
    public SalesReturn get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/invoice/{invoiceId}/available")
    public List<Returnable> available(@PathVariable Long invoiceId) {
        return service.returnable(invoiceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesReturn create(@Valid @RequestBody ReturnRequest r, Authentication auth) {
        SalesReturn ret = service.create(r);
        log.info("Devolución {} de la factura {} registrada por {}", ret.getNumber(), r.invoiceId(), auth.getName());
        return ret;
    }
}

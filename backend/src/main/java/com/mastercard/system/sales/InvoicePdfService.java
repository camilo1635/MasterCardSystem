package com.mastercard.system.sales;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mastercard.system.common.Money;
import com.mastercard.system.customer.Customer;
import com.mastercard.system.customer.CustomerRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);

    private final InvoiceService invoices;
    private final CustomerRepository customers;

    public byte[] render(Long invoiceId) {
        Invoice inv = invoices.get(invoiceId);
        Customer customer = inv.getCustomerId() == null ? null : customers.findById(inv.getCustomerId()).orElse(null);
        NumberFormat cop = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("MasterCard Sound - Audio y accesorios para carro", TITLE));
            doc.add(new Paragraph("Factura de venta No. " + inv.getNumber()
                    + ("ANULADA".equals(inv.getStatus()) ? "  (ANULADA)" : ""), BOLD));
            doc.add(new Paragraph("Fecha: " + inv.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    + "    Pago: " + inv.getPaymentType(), NORMAL));
            doc.add(new Paragraph("Cliente: " + (customer != null
                    ? customer.getName() + " - " + customer.getDocument() : "Consumidor final"), NORMAL));
            doc.add(new Paragraph(" "));

            PdfPTable t = new PdfPTable(new float[] {4, 1, 2, 1, 2});
            t.setWidthPercentage(100);
            for (String h : new String[] {"Descripción", "Cant.", "Valor unit.", "IVA %", "Subtotal"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, BOLD));
                c.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                t.addCell(c);
            }
            for (InvoiceItem it : inv.getItems()) {
                BigDecimal line = Money.round(it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
                t.addCell(new Phrase(it.getDescription(), NORMAL));
                t.addCell(right(String.valueOf(it.getQuantity())));
                t.addCell(right(cop.format(it.getUnitPrice())));
                t.addCell(right(it.getIvaRate().stripTrailingZeros().toPlainString()));
                t.addCell(right(cop.format(line)));
            }
            doc.add(t);
            doc.add(new Paragraph(" "));
            doc.add(totalLine("Subtotal", cop.format(inv.getSubtotal()), NORMAL));
            doc.add(totalLine("IVA", cop.format(inv.getIva()), NORMAL));
            doc.add(totalLine("TOTAL", cop.format(inv.getTotal()), BOLD));
            if (inv.getNotes() != null && !inv.getNotes().isBlank()) {
                doc.add(new Paragraph("Notas: " + inv.getNotes(), NORMAL));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private static PdfPCell right(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, NORMAL));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static Paragraph totalLine(String label, String value, Font font) {
        Paragraph p = new Paragraph(label + ": " + value, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }
}

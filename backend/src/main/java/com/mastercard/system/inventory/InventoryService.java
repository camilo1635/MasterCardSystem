package com.mastercard.system.inventory;

import com.mastercard.system.accounting.AccountingService;
import com.mastercard.system.accounting.AccountingService.Line;
import com.mastercard.system.common.BusinessException;
import com.mastercard.system.common.Money;
import com.mastercard.system.common.NotFoundException;
import com.mastercard.system.inventory.InventoryMovement.Type;
import com.mastercard.system.product.Product;
import com.mastercard.system.product.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository products;
    private final InventoryMovementRepository movements;
    private final AccountingService accounting;

    /**
     * Aplica un movimiento de stock sobre el producto (con bloqueo de fila).
     * Las ENTRADAS con costo recalculan el costo promedio ponderado.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Product move(Long productId, Type type, int signedQty, BigDecimal unitCost, String reference, String note) {
        Product p = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("Producto", productId));
        int newStock = p.getStock() + signedQty;
        if (newStock < 0) {
            throw new BusinessException("Cantidad insuficiente de '" + p.getName() + "': disponible "
                    + p.getStock() + ", requerido " + (-signedQty));
        }
        if (type == Type.ENTRADA && unitCost.signum() > 0 && newStock > 0) {
            BigDecimal value = p.getCost().multiply(BigDecimal.valueOf(p.getStock()))
                    .add(unitCost.multiply(BigDecimal.valueOf(signedQty)));
            p.setCost(value.divide(BigDecimal.valueOf(newStock), 2, RoundingMode.HALF_UP));
        }
        p.setStock(newStock); // entidad gestionada: el flush del commit persiste el cambio

        InventoryMovement m = new InventoryMovement();
        m.setProductId(productId);
        m.setType(type);
        m.setQuantity(signedQty);
        m.setUnitCost(type == Type.ENTRADA ? unitCost : p.getCost());
        m.setReference(reference);
        m.setNote(note);
        movements.save(m);
        return p;
    }

    /** Ajuste manual (conteo físico, merma, daño). La diferencia valorizada afecta Inventario vs Gastos diversos. */
    @Transactional
    public Product adjust(Long productId, int signedQty, String note) {
        if (signedQty == 0) {
            throw new BusinessException("La cantidad del ajuste no puede ser cero");
        }
        Product p = move(productId, Type.AJUSTE, signedQty, BigDecimal.ZERO, "AJUSTE", note);
        BigDecimal value = Money.round(p.getCost().multiply(BigDecimal.valueOf(Math.abs(signedQty))));
        if (signedQty > 0) {
            accounting.post(LocalDate.now(), "Ajuste inventario +: " + p.getName(), "AJUSTE", productId, List.of(
                    Line.debit(AccountingService.INVENTARIO, value),
                    Line.credit(AccountingService.GASTOS_DIVERSOS, value)));
        } else {
            accounting.post(LocalDate.now(), "Ajuste inventario -: " + p.getName(), "AJUSTE", productId, List.of(
                    Line.debit(AccountingService.GASTOS_DIVERSOS, value),
                    Line.credit(AccountingService.INVENTARIO, value)));
        }
        return p;
    }
}

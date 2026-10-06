-- Un producto ya no se activa/desactiva: no se puede vender cuando su cantidad es 0.
ALTER TABLE product DROP COLUMN active;

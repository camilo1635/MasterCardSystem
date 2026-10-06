-- Las categorías de producto ya no se usan.
ALTER TABLE product DROP COLUMN category_id;
DROP TABLE category;

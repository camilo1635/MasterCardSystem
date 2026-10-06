-- Sistema de un único dueño: se eliminan los roles.
ALTER TABLE app_user DROP COLUMN role;

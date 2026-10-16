-- 1. Rol
INSERT INTO Rol (id_rol, nombre, estado) VALUES
(1, 'ADMINISTRADOR', 1),
(2, 'JEFE_INVENTARIO', 1),
(3, 'OPERADOR_ALMACEN', 1);

-- 2. Sede
INSERT INTO Sede (id_sede, nombre, direccion, estado) VALUES
(1, 'Tottus MegaPlaza',    'Av. Alfredo Mendiola 3698, Independencia, Lima', 1),
(2, 'Tottus Jockey Plaza', 'Av. Javier Prado Este 4200, Surco, Lima', 1),
(3, 'Tottus San Miguel',   'Av. La Marina 2000, San Miguel, Lima', 1),
(4, 'Tottus Miraflores',   'Av. 28 de Julio 1005, Miraflores, Lima', 1);

-- 3. Usuario (reemplaza los hashes por los completos)
INSERT INTO Usuario (id_usuario, id_rol, id_sede, usuario, password_hash, estado) VALUES
(1, 1, 1, 'gerencia_admin', '$2a$10$RY56ooiw7lmwWkFLA90JoeYEwTx/vKWXb.6C6Eh5UAy6VTPw8NZRi', 1),
(2, 2, 1, 'jefe_almacen',   '$2a$10$V8h3JiG7Z3awK/ShglhMGOKqK0CoZ2iBETVExX5/V/DWuqC4wilOa', 1);

-- 4. Categoria
INSERT INTO Categoria (id_categoria, nombre, estado) VALUES
(1, 'Abarrotes y Despensa', 1),
(2, 'Lácteos y Huevos', 1),
(3, 'Bebidas y Licores', 1),
(4, 'Carnes, Aves y Pescados', 1),
(5, 'Frutas y Verduras', 1),
(6, 'Limpieza y Cuidado del Hogar', 1);

-- 5. Producto
INSERT INTO Producto (id_producto, id_categoria, codigo, nombre, precio, stock_minimo, estado) VALUES
(1,  1, 'TOT-ABA-001', 'Arroz Extra Añejo Tottus 5kg', 21.90, 18, 1),
(2,  1, 'TOT-ABA-002', 'Aceite Vegetal Primor Premium 900ml', 9.80, 20, 1),
(3,  1, 'TOT-ABA-003', 'Azúcar Rubia Tottus Selección 5kg', 18.90, 10, 1),
(4,  2, 'TOT-LAC-001', 'Leche Evaporada Gloria Azul Pack x6', 24.90, 15, 1),
(5,  2, 'TOT-LAC-002', 'Yogurt Gloria Fresa Parcialmente Descremado 1kg', 6.70, 12, 1),
(6,  3, 'TOT-BEB-001', 'Gaseosa Coca Cola Sabor Original 3 Litros', 13.50, 25, 1),
(7,  3, 'TOT-BEB-002', 'Agua Mineral San Mateo sin gas 2.5L', 4.20, 30, 1),
(8,  4, 'TOT-CAR-001', 'Pechuga de Pollo San Fernando Fresca x kg', 16.90, 20, 1),
(9,  5, 'TOT-FRU-001', 'Plátano de Seda Selección Tottus x kg', 3.49, 25, 1),
(10, 5, 'TOT-FRU-002', 'Manzana Royal Gala Importada x kg', 7.80, 15, 1),
(11, 6, 'TOT-LIM-001', 'Detergente Líquido Ariel Doble Poder 3L', 36.90, 10, 1),
(12, 6, 'TOT-LIM-002', 'Lavavajilla Ayudín Limón Crema 850g', 8.20, 12, 1);

-- 6. Inventario
INSERT INTO Inventario (id_inventario, id_sede, id_producto, stock_actual, ubicacion) VALUES
(1,  1, 1,  75,  'Pasillo 03 - Góndola A (Renovado)'),
(2,  2, 1,  52,  'Pasillo 03 - Góndola A'),
(3,  1, 2,  80,  'Pasillo 03 - Góndola B'),
(4,  2, 2,  64,  'Pasillo 03 - Góndola B'),
(5,  1, 3,  35,  'Pasillo 03 - Góndola C'),
(6,  2, 3,  28,  'Pasillo 03 - Góndola C'),
(7,  1, 4,  45,  'Pasillo 05 - Refrigerados 1'),
(8,  2, 4,  36,  'Pasillo 05 - Refrigerados 1'),
(9,  1, 5,  10,  'Pasillo 05 - Lácteos 2'),
(10, 2, 5,  8,   'Pasillo 05 - Lácteos 2'),
(11, 1, 6,  120, 'Pasillo 07 - Bebidas A'),
(12, 2, 6,  96,  'Pasillo 07 - Bebidas A'),
(13, 1, 7,  95,  'Pasillo 07 - Bebidas B'),
(14, 2, 7,  76,  'Pasillo 07 - Bebidas B'),
(15, 1, 8,  30,  'Pasillo 09 - Carnicería'),
(16, 2, 8,  24,  'Pasillo 09 - Carnicería'),
(17, 1, 9,  50,  'Isla 01 - Frutería'),
(18, 2, 9,  40,  'Isla 01 - Frutería'),
(19, 1, 10, 0,   'Isla 02 - Frutería'),
(20, 2, 10, 0,   'Isla 02 - Frutería'),
(21, 1, 11, 6,   'Pasillo 11 - Aseo 1'),
(22, 2, 11, 4,   'Pasillo 11 - Aseo 1'),
(23, 1, 12, 40,  'Pasillo 11 - Aseo 2'),
(24, 2, 12, 32,  'Pasillo 11 - Aseo 2');

-- 7. Movimiento
INSERT INTO Movimiento (id_movimiento, id_sede, id_producto, id_usuario, tipo, cantidad, fecha, referencia) VALUES
(1,  1, 1,  1, 'ENTRADA', 65,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(2,  1, 2,  1, 'ENTRADA', 80,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(3,  1, 3,  1, 'ENTRADA', 35,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(4,  1, 4,  1, 'ENTRADA', 45,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(5,  1, 5,  1, 'ENTRADA', 10,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(6,  1, 6,  1, 'ENTRADA', 120, '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(7,  1, 7,  1, 'ENTRADA', 95,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(8,  1, 8,  1, 'ENTRADA', 30,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(9,  1, 9,  1, 'ENTRADA', 50,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(10, 1, 11, 1, 'ENTRADA', 6,   '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(11, 1, 12, 1, 'ENTRADA', 40,  '2026-09-10 20:05:30', 'Carga inicial de inventario'),
(12, 1, 1,  1, 'ENTRADA', 10,  '2026-09-10 22:07:28', 'Guia de remision 001-492');
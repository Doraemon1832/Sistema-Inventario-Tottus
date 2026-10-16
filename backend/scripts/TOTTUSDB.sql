Create database inv_tottus_db;
USE inv_tottus_db;
Create Table Proveedor(
id_proveedor int auto_increment primary key, 
ruc varchar(20) unique,
razon_social varchar(150),
telefono varchar(20),
estado boolean
);
Create Table Rol (
id_rol int auto_increment Primary Key,
nombre varchar(50), 
estado boolean
);
Create Table Sede(
id_sede int auto_increment primary key,
nombre varchar(100),
direccion varchar(200),
estado boolean
);

Create table Usuario(
id_usuario int auto_increment primary key,
id_rol int,
id_sede int,
usuario varchar(50) unique,
password_hash varchar(255), 
estado boolean,

constraint fk_rol_usuario foreign key (id_rol) references Rol(id_rol),
constraint fk_sede_usuario foreign key (id_sede) references Sede(id_sede)
);

Create Table Categoria(
id_categoria int auto_increment primary key,
nombre varchar(100),
estado boolean
);

Create Table Producto(
id_producto int auto_increment primary key,
id_categoria int,
codigo varchar(150) unique,
nombre varchar(150),
precio decimal(10,2),
stock_minimo int,

constraint fk_categoria_producto foreign key (id_categoria) references Categoria(id_categoria)

);


Create Table Inventario(
id_inventario int auto_increment primary key,
id_sede int,
id_producto int,
stock_actual int,
ubicacion varchar(100), 
unique (id_sede, id_producto),

constraint fk_sede_inventario foreign key (id_sede) references Sede(id_sede),
constraint fk_producto_inventario foreign key (id_producto) references Producto(id_producto)
);

Create Table Compra(
id_compra int auto_increment primary key, 
id_sede int,
id_proveedor int,
id_usuario int, 
fecha datetime, 
total decimal(12,2),

constraint fk_sede_Compra foreign key (id_sede) references Sede(id_sede),
constraint fk_proveedor_Compra foreign key (id_proveedor) references Proveedor(id_proveedor),
constraint fk_usuario_Compra foreign key (id_usuario) references Usuario(id_usuario)
);

Create Table Detalle_compra(
id_det_compra int auto_increment primary key,
id_compra int,
id_producto int, 
cantidad int, 
precio decimal(10,2),

constraint fk_compra_detcompra foreign key (id_compra) references Compra(id_compra),
constraint fk_producto_detcompra foreign key (id_producto) references Producto(id_producto)
);

Create Table Movimiento(
id_movimiento int auto_increment primary key,
id_sede int,
id_producto int,
id_usuario int,
tipo varchar(20),
cantidad int,
fecha datetime,
referencia varchar(100),

constraint fk_sede_Movimiento foreign key (id_sede) references Sede(id_sede),
constraint fk_producto_Movimiento foreign key (id_producto) references Producto(id_producto),
constraint fk_usuario_Movimiento foreign key (id_usuario) references Usuario(id_usuario)
);

Create Table Auditoria(
id_auditoria int auto_increment primary key,
id_usuario int,
accion varchar(100),
fecha datetime,
ip varchar(45),
detalle text,

constraint fk_usuario_Auditoria foreign key (id_usuario) references Usuario(id_usuario)
);

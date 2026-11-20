import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { LayoutComponent } from './components/layout/layout.component';
import { InventarioComponent } from './components/inventario/inventario.component';
import { authGuard } from './guards/auth.guard';
import { OperacionesComponent } from './components/operaciones/operaciones.component';
import { AbastecimientoComponent } from './components/abastecimiento/abastecimiento.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { KardexComponent } from './components/kardex/kardex.component';
import { roleGuard } from './guards/role.guard';
import { TrasladosComponent } from './components/traslados/traslados.component';
import { UsuariosComponent } from './components/usuarios/usuarios.component';
import { CambiarPasswordComponent } from './components/cambiar-password/cambiar-password.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'cambiar-password', component: CambiarPasswordComponent },
      { path: 'usuarios', component: UsuariosComponent, canActivate: [roleGuard], data: { roles: ['ADMINISTRADOR'] } },
      { path: 'inventario', component: InventarioComponent, canActivate: [roleGuard], data: { roles: ['ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR'] } },
      { path: 'dashboard', component: DashboardComponent, canActivate: [roleGuard], data: { roles: ['ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR','ENCARGADO_COMPRAS'] } },
      { path: 'kardex', component: KardexComponent, canActivate: [roleGuard], data: { roles: ['ADMINISTRADOR','JEFE_INVENTARIO'] } },
      { path: 'ingresos', component: OperacionesComponent, canActivate: [roleGuard], data: { tipo: 'ingresos', roles: ['ADMINISTRADOR','OPERADOR_ALMACEN','JEFE_INVENTARIO'] } },
      { path: 'egresos', component: OperacionesComponent, canActivate: [roleGuard], data: { tipo: 'egresos', roles: ['ADMINISTRADOR','OPERADOR_ALMACEN','VENDEDOR','JEFE_INVENTARIO'] } },
      { path: 'traslados', component: TrasladosComponent, canActivate: [roleGuard], data: { roles: ['ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN'] } },
      { path: 'proveedores', component: AbastecimientoComponent, canActivate: [roleGuard], data: { mode: 'proveedores', roles: ['ADMINISTRADOR','JEFE_INVENTARIO','ENCARGADO_COMPRAS'] } },
      { path: 'compras', component: AbastecimientoComponent, canActivate: [roleGuard], data: { mode: 'compras', roles: ['ADMINISTRADOR','JEFE_INVENTARIO','ENCARGADO_COMPRAS'] } },
      { path: '', redirectTo: 'inventario', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];

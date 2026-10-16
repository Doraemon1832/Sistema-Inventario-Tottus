import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { LayoutComponent } from './components/layout/layout.component';
import { InventarioComponent } from './components/inventario/inventario.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'inventario', component: InventarioComponent },
      { path: '', redirectTo: 'inventario', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];

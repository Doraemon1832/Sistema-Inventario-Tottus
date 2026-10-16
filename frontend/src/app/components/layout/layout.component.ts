import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { InventarioService } from '../../services/inventario.service';
import { Sede, User } from '../../models/inventario.model';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, FormsModule],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss'
})
export class LayoutComponent implements OnInit {
  sedes = signal<Sede[]>([]);
  activeSedeId = signal<number>(1);
  currentUser = signal<User | null>(null);

  constructor(
    public authService: AuthService,
    private inventarioService: InventarioService
  ) {}

  ngOnInit(): void {
    this.currentUser.set(this.authService.currentUser());
    this.activeSedeId.set(this.authService.activeSedeId() || 1);

    this.inventarioService.getSedes().subscribe({
      next: (list) => {
        this.sedes.set(list);
      }
    });
  }

  onSedeChange(sedeId: number): void {
    this.activeSedeId.set(Number(sedeId));
    this.authService.setActiveSede(Number(sedeId));
  }

  logout(): void {
    this.authService.logout();
  }
}

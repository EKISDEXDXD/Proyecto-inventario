import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { MenuService } from '../core/menu.service';
import { ApiConfigService } from '../auth/api-config.service';

@Component({
  selector: 'app-my-stores',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './my-stores.component.html',
  styleUrls: ['./my-stores.component.css']
})
export class MyStoresComponent implements OnInit {
  stores: any[] = [];
  openMenuId: number | null = null;
  loading = true;
  private cacheKey = 'myStoresCache';
  private cacheTtlMs = 5 * 60 * 1000; // 5 minutos

  get isMenuOpen$() {
    return this.menuService.isMenuOpen$;
  }

  constructor(
    private router: Router, 
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private menuService: MenuService,
    private apiConfig: ApiConfigService
  ) {}

  ngOnInit() {
    this.loadStores();
  }

  toggleAppMenu() {
    this.menuService.toggleMenu();
  }

  loadStores() {
    const cached = this.getCachedStores();
    if (cached && cached.stores?.length) {
      this.stores = cached.stores;
      this.loading = true;
      this.cdr.detectChanges();
    }

    const apiUrl = this.apiConfig.getApiUrl('/api/stores');
    const token = localStorage.getItem('token'); 
    
    if (!token) {
      console.error("No se encontró token en localStorage");
      this.loading = false;
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.get<any[]>(apiUrl, { headers }).subscribe({
      next: (data) => {
        console.log('Tiendas cargadas exitosamente:', data);
        this.stores = data;
        this.saveStoresCache(data);
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando tiendas:', err);
        this.loading = false;
        if(err.status === 403 || err.status === 401) {
          alert("No tienes permiso o tu sesión expiró. Inicia sesión nuevamente.");
        }
      }
    });
  }

  private getCachedStores(): { stores: any[]; timestamp: number } | null {
    try {
      const cached = localStorage.getItem(this.cacheKey);
      if (!cached) return null;
      const parsed = JSON.parse(cached);
      if (!parsed || !parsed.stores || !parsed.timestamp) return null;
      if (Date.now() - parsed.timestamp > this.cacheTtlMs) {
        localStorage.removeItem(this.cacheKey);
        return null;
      }
      return parsed;
    } catch {
      return null;
    }
  }

  private saveStoresCache(stores: any[]) {
    try {
      localStorage.setItem(this.cacheKey, JSON.stringify({ stores, timestamp: Date.now() }));
    } catch {
      // Si el almacenamiento local falla, no rompemos la vista.
    }
  }

  trackByStoreId(index: number, store: any) {
    return store?.id ?? index;
  }

  goBack() {
    this.router.navigate(['/home']);
  }

  toggleMenu(storeId: number) {
    this.openMenuId = this.openMenuId === storeId ? null : storeId;
  }

  closeMenu() {
    this.openMenuId = null;
  }

  manageStore(storeId: number) {
    this.router.navigate(['/tienda', storeId]);
  }

  editStore(storeId: number) {
    this.router.navigate(['/edit-store', storeId]);
  }

  // Función para eliminar tienda con doble confirmación
  deleteStore(id: number) {
    const store = this.stores.find(s => s.id === id);
    if (!store) return;

    // Primera confirmación
    const firstConfirm = confirm(`¿Estás seguro de que quieres eliminar la tienda "${store.name}"?`);
    if (!firstConfirm) return;

    // Segunda confirmación
    const secondConfirm = confirm(`⚠ ATENCIÓN ⚠\n\nEsta acción no se puede deshacer.\n\n¿Realmente quieres eliminar permanentemente la tienda "${store.name}" y todos sus datos asociados?`);
    if (!secondConfirm) return;

    // Proceder con la eliminación
    const token = localStorage.getItem('token');
    if (!token) {
      alert('Sesión expirada. Inicia sesión nuevamente.');
      return;
    }

    const headers = new HttpHeaders({ 'Authorization': `Bearer ${token}` });
    const apiUrl = this.apiConfig.getApiUrl('/api/stores');

    this.http.delete(`${apiUrl}/${id}`, { headers }).subscribe({
      next: () => {
        this.stores = this.stores.filter(s => s.id !== id);
        this.cdr.detectChanges();
        alert(`La tienda "${store.name}" ha sido eliminada exitosamente.`);
      },
      error: (error) => {
        console.error('Error al eliminar:', error);
        alert('No se pudo eliminar la tienda. Inténtalo de nuevo.');
      }
    });
  }

  getColorGradient(hexColor: string): string {
    // Convierte hex a RGB
    const hex = hexColor.replace('#', '');
    const r = parseInt(hex.substring(0, 2), 16);
    const g = parseInt(hex.substring(2, 4), 16);
    const b = parseInt(hex.substring(4, 6), 16);

    // Crea una versión más clara del color (para el gradient)
    const lighten = (val: number) => Math.min(255, Math.round(val + (255 - val) * 0.2));
    const darken = (val: number) => Math.max(0, Math.round(val * 0.85));

    const lightColor = `rgb(${lighten(r)}, ${lighten(g)}, ${lighten(b)})`;
    const darkColor = `rgb(${darken(r)}, ${darken(g)}, ${darken(b)})`;

    // Retorna un gradient diagonal
    return `linear-gradient(135deg, ${lightColor} 0%, ${hexColor} 50%, ${darkColor} 100%)`;
  }
}
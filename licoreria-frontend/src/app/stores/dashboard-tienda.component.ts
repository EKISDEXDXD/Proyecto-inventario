import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MenuService } from '../core/menu.service';
import { ExportModalComponent } from './export-modal.component';
import { ApiConfigService } from '../auth/api-config.service';

@Component({
  selector: 'app-dashboard-tienda',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule],
  templateUrl: './dashboard-tienda.component.html',
  styleUrls: ['./dashboard-tienda.component.css']
})
export class DashboardTiendaComponent implements OnInit {
  storeId: number = 0;
  store: any = null;
  products: any[] = [];
  loading: boolean = true;
  isExternalMode: boolean = false; 

  private apiStoresUrl: string = '';
  private apiProductsUrl: string = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog,
    private menuService: MenuService,
    private apiConfig: ApiConfigService
  ) {}

  get isMenuOpen$() {
    return this.menuService.isMenuOpen$;
  }

  ngOnInit() {
    this.initializeApiUrls();

    this.route.params.subscribe(params => {
      this.storeId = +params['id'];
      
      // CRITICAL: Volvemos a validar el acceso cada vez que el ID de la tienda cambie
      this.checkExternalAccess(); 
      
      this.loadStoreData();
      this.loadStoreProducts();
    });
  }

  private initializeApiUrls() {
    this.apiStoresUrl = this.apiConfig.getApiUrl('/api/stores');
    this.apiProductsUrl = this.apiConfig.getApiUrl('/api/products');
  }

  private checkExternalAccess() {
    const externalStore = sessionStorage.getItem('externalStore');
    const currentUrlId = this.route.snapshot.paramMap.get('id'); // ID de la URL actual

    if (externalStore) {
      const data = JSON.parse(externalStore);
      
      // Solo activamos modo externo si el ID en sesión coincide con la tienda que estamos visitando
      if (data.id == currentUrlId && data.isExternal) {
        this.isExternalMode = true;
        return;
      }
    }
    
    // Si no hay datos en sesión o el ID no coincide, es una tienda propia[cite: 1]
    this.isExternalMode = false;
  }

  toggleAppMenu() {
    this.menuService.toggleMenu();
  }

  navigateTo(section: string) {
    if (this.isExternalMode && section !== 'movimientos') {
      alert('Acceso restringido: Solo puedes ver Movimientos.');
      return;
    }
    this.router.navigate([section], { relativeTo: this.route });
  }

  exportarReporte() {
    // Si llegamos aquí y es modo externo, bloqueamos[cite: 1]
    if (this.isExternalMode) {
      alert('Función no disponible para invitados.');
      return;
    }
    
    // Abrimos el modal pasando el storeId actual[cite: 1]
    this.dialog.open(ExportModalComponent, {
      width: '900px',
      maxHeight: '90vh',
      data: { storeId: this.storeId }
    });
  }

  loadStoreData() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({ 'Authorization': `Bearer ${token}` });
    
    // Seleccionamos endpoint basado en el modo validado[cite: 1]
    const endpoint = this.isExternalMode 
      ? `${this.apiStoresUrl}/external/${this.storeId}`
      : `${this.apiStoresUrl}/${this.storeId}`;

    this.http.get<any>(endpoint, { headers }).subscribe({
      next: (data) => {
        this.store = data;
        this.cdr.detectChanges(); // Previene error NG0100[cite: 1]
      },
      error: (err) => {
        console.error('Error cargando tienda:', err);
        // Si hay error 500 o de permisos, redirigimos por seguridad
        if (!this.isExternalMode) this.router.navigate(['/my-stores']);
      }
    });
  }

  loadStoreProducts() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({ 'Authorization': `Bearer ${token}` });
    const endpoint = this.isExternalMode 
      ? `${this.apiProductsUrl}/store/external/${this.storeId}`
      : `${this.apiProductsUrl}/store/${this.storeId}`;

    this.http.get<any[]>(endpoint, { headers }).subscribe({
      next: (data) => {
        this.products = data;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => this.loading = false
    });
  }

  getStoreThemeStyles(): Record<string, string> {
    const color = this.store?.color || '#667eea';
    const hex = color.replace('#', '');
    if (hex.length !== 6) {
      return { background: 'linear-gradient(120deg, #f8fafc 0%, #e0e7ff 100%)' };
    }
    const red = parseInt(hex.substring(0, 2), 16);
    const green = parseInt(hex.substring(2, 4), 16);
    const blue = parseInt(hex.substring(4, 6), 16);
    const soften = (value: number) => Math.round(value + (255 - value) * 0.86);
    const lightColor = `rgb(${soften(red)}, ${soften(green)}, ${soften(blue)})`;
    return {
      background: `linear-gradient(120deg, #ffffff 0%, ${lightColor} 100%)`
    };
  }

  getStoreAccentStyles(): Record<string, string> {
    const color = this.store?.color || '#667eea';
    const hex = color.replace('#', '');
    if (hex.length !== 6) {
      return { color: '#6366f1', background: '#eef2ff' };
    }
    const red = parseInt(hex.substring(0, 2), 16);
    const green = parseInt(hex.substring(2, 4), 16);
    const blue = parseInt(hex.substring(4, 6), 16);
    return {
      color,
      background: `rgba(${red}, ${green}, ${blue}, 0.1)`
    };
  }
}
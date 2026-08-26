import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { MenuService } from '../core/menu.service';
import { ExternalStoreService } from '../core/external-store.service';
import { ApiConfigService } from '../auth/api-config.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  readonly appVersion = '1.0';
  readonly versionLabel = 'Lanzamiento inicial';
  username = '';
  showExternalModal = false;
  externalStoreName = '';
  externalPassword = '';
  loadingExternal = false;

  get isMenuOpen$() {
    return this.menuService.isMenuOpen$;
  }

  constructor(
    private router: Router, 
    private http: HttpClient, 
    private menuService: MenuService, 
    private activatedRoute: ActivatedRoute, 
    private externalStoreService: ExternalStoreService, 
    private apiConfig: ApiConfigService
  ) {
    this.loadUsername();
  }

  ngOnInit() {
    this.activatedRoute.queryParams.subscribe(params => {
      if (params['openExternal'] === 'true') {
        this.openExternalModal();
      }
    });

    this.externalStoreService.openExternalModal$.subscribe(() => {
      this.openExternalModal();
    });
  }

  toggleMenu() {
    this.menuService.toggleMenu();
  }

  loadUsername() {
    const token = localStorage.getItem('token');
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        this.username = payload.sub || 'Usuario';
      } catch (error) {
        console.error('Error decodificando JWT:', error);
        this.username = 'Usuario';
      }
    }
  }

  goToCreateStore() {
    this.router.navigate(['/create-store']);
  }

  goToMyStores() {
    this.router.navigate(['/my-stores']);
  }

  openExternalModal() {
    this.showExternalModal = true;
    this.externalStoreName = '';
    this.externalPassword = '';
  }

  closeExternalModal() {
    this.showExternalModal = false;
    this.loadingExternal = false;
  }

  accessExternalStore() {
    if (!this.externalStoreName.trim() || !this.externalPassword.trim()) {
      alert('Por favor, ingresa el nombre de la tienda y la contraseña.');
      return;
    }

    this.loadingExternal = true;
    const token = localStorage.getItem('token');
    if (!token) {
      alert('Sesión expirada. Inicia sesión nuevamente.');
      this.closeExternalModal();
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const body = {
      storeName: this.externalStoreName.trim(),
      password: this.externalPassword.trim()
    };

    const apiUrl = this.apiConfig.getApiUrl('/api/stores');

    // Cambiamos 'store' por 'res' en el parámetro del next para evitar confusiones
    this.http.post<any>(`${apiUrl}/external-access`, body, { headers }).subscribe({
      next: (res) => {
        console.log('✅ Tienda externa obtenida:', res);
        
        // 1. Verificamos que el objeto de respuesta tenga el id (campo que devuelve el backend)
        if (res && res.id) {
          
          // 2. Preparamos los datos para identificar el acceso externo en otros componentes
          const externalData = {
            id: res.id,
            name: res.name,
            token: res.token,
            isExternal: true
          };

          console.log('💾 Guardando acceso externo en sessionStorage:', externalData);
          sessionStorage.setItem('externalStore', JSON.stringify(externalData));
          
          // 3. NAVEGACIÓN SEGURA: Redirigimos usando el ID verificado
          this.router.navigate(['/tienda', res.id]);
          
          this.closeExternalModal();
        } else {
          console.error('❌ Error: El servidor no envió un id válido', res);
          alert('Hubo un problema al obtener los datos de la tienda.');
          this.loadingExternal = false;
        }
      },
      error: (err) => {
        console.error('❌ Error accediendo a tienda externa:', err);
        if (err.status === 404) {
          alert('Tienda no encontrada o contraseña incorrecta.');
        } else {
          alert('Error al acceder a la tienda externa. Inténtalo de nuevo.');
        }
        this.loadingExternal = false;
      }
    });
  }
}
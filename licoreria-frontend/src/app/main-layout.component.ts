import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterModule, RouterOutlet } from '@angular/router';
import { AuthService } from './auth/auth.service';
import { MenuService } from './core/menu.service';
import { UserService } from './core/user.service';
import { ExternalStoreService } from './core/external-store.service';
import { GalleryNavigationService } from './core/gallery-navigation.service';
import { HasUnsavedChanges } from './common/without-unsaved-changes-guard.spec';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ExportModalComponent } from './stores/export-modal.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, RouterOutlet, MatDialogModule],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.css']
})
export class MainLayoutComponent implements HasUnsavedChanges, OnInit {
  username = '';
  isDarkMode = false;
  isMobileView = false;
  currentStoreId: number | null = null;
  isExternalStore = false;
  isStoreDashboardRoute = false;
  isStoreNavOpen = false;

  get isMenuOpen$() {
    return this.menuService.isMenuOpen$;
  }

  hasUnsavedChanges(): boolean {
    return false;
  }

  constructor(private authService: AuthService, private router: Router, private menuService: MenuService, private userService: UserService, private externalStoreService: ExternalStoreService, private galleryNavigationService: GalleryNavigationService, private dialog: MatDialog) {
    console.log('MainLayoutComponent - Inicializando...');
    this.loadUsername();
    this.checkWindowSize();
  }

  ngOnInit() {
    this.menuService.closeMenu();
    this.updateStoreNavigation(this.router.url);
    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        this.updateStoreNavigation(event.urlAfterRedirects);
      }
    });
    // Suscribirse a los cambios de nombre de usuario
    this.userService.getUsername().subscribe((newUsername) => {
      if (newUsername) {
        this.username = newUsername;
      }
    });
  }

  get showStoreNavigation(): boolean {
    return this.isMobileView && this.currentStoreId !== null && !this.isStoreDashboardRoute;
  }

  private updateStoreNavigation(url: string): void {
    const cleanUrl = url.split(/[?#]/)[0];
    const match = cleanUrl.match(/\/tienda\/(\d+)(?:\/|$)/);
    this.currentStoreId = match ? Number(match[1]) : null;
    this.isStoreDashboardRoute = /^\/tienda\/\d+\/?$/.test(cleanUrl);
    if (this.currentStoreId === null || this.isStoreDashboardRoute) {
      this.isStoreNavOpen = false;
    }
    this.isExternalStore = false;

    const externalStore = sessionStorage.getItem('externalStore');
    if (this.currentStoreId !== null && externalStore) {
      try {
        const data = JSON.parse(externalStore);
        this.isExternalStore = Number(data.id) === this.currentStoreId && data.isExternal === true;
      } catch {
        this.isExternalStore = false;
      }
    }
  }

  navigateStore(section: 'dashboard-info' | 'inventario' | 'movimientos'): void {
    if (this.currentStoreId === null) return;
    this.isStoreNavOpen = false;
    this.router.navigate(['/tienda', this.currentStoreId, section]);
  }

  openStoreGallery(): void {
    if (this.currentStoreId === null || this.isExternalStore) return;
    this.isStoreNavOpen = false;

    const currentPath = this.router.url.split(/[?#]/)[0];
    if (currentPath === `/tienda/${this.currentStoreId}/inventario`) {
      this.galleryNavigationService.triggerOpenGallery();
      return;
    }

    this.router.navigate(['/tienda', this.currentStoreId, 'inventario'], {
      queryParams: { openGallery: 'true' }
    });
  }

  toggleStoreNavigation(): void {
    this.isStoreNavOpen = !this.isStoreNavOpen;
  }

  openStoreExport(): void {
    if (this.currentStoreId === null || this.isExternalStore) return;
    this.isStoreNavOpen = false;
    this.dialog.open(ExportModalComponent, {
      width: '900px',
      maxHeight: '90vh',
      data: { storeId: this.currentStoreId }
    });
  }

  @HostListener('window:resize', ['$event'])
  onResize(event: any) {
    this.checkWindowSize();
  }

  checkWindowSize() {
    this.isMobileView = window.innerWidth <= 768;
    if (!this.isMobileView) {
      this.isStoreNavOpen = false;
    }
  }

  loadUsername() {
    console.log('MainLayoutComponent - Cargando username...');
    const currentUsername = this.userService.getCurrentUsername();
    if (currentUsername) {
      this.username = currentUsername;
    } else {
      // Si no hay username en el servicio, cargar desde el token
      const token = localStorage.getItem('token');
      console.log('MainLayoutComponent - Token encontrado:', token ? 'SÍ' : 'NO');
      if (token) {
        try {
          const payload = JSON.parse(atob(token.split('.')[1]));
          console.log('MainLayoutComponent - JWT payload:', payload);
          this.username = payload.sub || 'Usuario';
        } catch (error) {
          console.error('MainLayoutComponent - Error decodificando JWT:', error);
          this.username = 'Usuario';
        }
      }
    }
  }

  toggleMenu() {
    this.menuService.toggleMenu();
  }

  closeMenuOnMobile() {
    // Siempre cerrar el menú cuando se hace clic en una opción (aplica para tablet y móvil)
    if (window.innerWidth <= 768) {
      this.menuService.closeMenu();
    }
  }

  toggleDarkMode() {
    this.isDarkMode = !this.isDarkMode;
  }

  goToHome() {
    this.menuService.closeMenu();
    this.router.navigate(['/home']);
  }

  goToMyStores() {
    this.menuService.closeMenu();
    this.router.navigate(['/my-stores']);
  }

  goToCreateStore() {
    this.menuService.closeMenu();
    this.router.navigate(['/create-store']);
  }

  goToExternalStores() {
    this.menuService.closeMenu();
    // Navega a home
    this.router.navigate(['/home']);
    // Emite evento para abrir el modal (funciona incluso si ya estás en home)
    setTimeout(() => this.externalStoreService.triggerOpenExternalModal(), 100);
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
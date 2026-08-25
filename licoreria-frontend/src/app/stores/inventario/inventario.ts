import { Component, OnInit, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { catchError, EMPTY } from 'rxjs';
import { ApiConfigService } from '../../auth/api-config.service';
import { ProductGalleryModalComponent } from './product-gallery-modal.component';
import { LotesModalComponent } from './lotes-modal.component';
import { LotesService } from '../../services/lotes.service';
import { CurrencyService, Currency } from '../../services/currency.service';
import { CurrencyFormatPipe } from '../../pipes/currency-format.pipe';
import { ModalStackService } from './modal-stack.service';

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductGalleryModalComponent, LotesModalComponent, CurrencyFormatPipe],
  templateUrl: './inventario.html',
  styleUrl: './inventario.css'
})
export class InventarioComponent implements OnInit {
  storeId: number = 0;
  store: any = null;
  products: any[] = [];
  filteredProducts: any[] = [];
  displayProducts: any[] = [];
  productNameMap: Record<number, string> = {};
  productStockCache: Record<number, number> = {};
  productStockStatusCache: Record<number, 'normal' | 'low' | 'out'> = {};
  lowStockProducts: any[] = [];
  outOfStockProducts: any[] = [];
  normalStockProducts: any[] = [];
  activeAlertProducts: any[] = [];
  loading: boolean = true;
  searchTerm: string = '';
  showCreateForm: boolean = false;

  // Gallery Modal
  showGalleryModal: boolean = false;

  // Administrative Costs properties
  administrativeCosts: any[] = [];
  showCreateAdminCostForm: boolean = false;
  editingAdminCostId: number | null = null;
  loadingAdminCosts: boolean = false;

  newAdminCost = {
    name: '',
    cost: 0,
    description: ''
  };

  lowStockThreshold = 50;
  normalStockThreshold = 50;
  showThresholdConfig = false;
  showAlertPanel = false;
  selectedProduct: any = null;
  thresholdForm = {
    lowStockThreshold: 50,
    normalStockThreshold: 50
  };
  
  alertForm = {
    threshold: 0,
    isEnabled: true
  };

  // Propiedades para el sistema de lotes
  showLotesModal: boolean = false;
  selectedProductForLotes: any = null;

  // Currency properties
  availableCurrencies: Currency[] = [];
  selectedCurrency: Currency | null = null;

  // Description modal properties
  showDescriptionModal = false;
  selectedProductForDescription: any = null;
  descriptionModalDisplayLote: any = null;
  descriptionModalZIndex = 2000;
  editProductModalZIndex = 2000;

  // Lotes Modal properties
  lotesMap: Map<number, any[]> = new Map();
  showLoteDetailModal: boolean = false;
  selectedLoteForDetail: any = null;

  // Edit Product Modal properties
  showEditProductModal = false;
  isEditingProduct = false;
  canEditProduct = false;
  editProductError: string = '';
  editProductForm = {
    name: '',
    description: '',
    cost: 0,
    price: 0
  };

  // Product Image properties
  productImage: any = null;
  imageUploadMessage: string = '';
  imageUploadMessageType: string = ''; // 'success' or 'error'
  isUploadingImage: boolean = false;

  // Collapsible state variables
  showProductsList: boolean = false;
  showAdminCostsList: boolean = this.loadCollapsibleState('showAdminCostsList', false);

  // Form fields
  newProduct = {
    name: '',
    description: '',
    cost: 0,
    price: 0,
    stock: 0
  };

  private apiStoresUrl: string = '';
  private apiProductsUrl: string = '';
  private apiAdminCostsUrl: string = '';
  private apiProductImagesUrl: string = '';

  private initializeApiUrls() {
    this.apiStoresUrl = this.apiConfig.getApiUrl('/api/stores');
    this.apiProductsUrl = this.apiConfig.getApiUrl('/api/products');
    this.apiAdminCostsUrl = this.apiConfig.getApiUrl('/api/administrative-costs');
    this.apiProductImagesUrl = this.apiConfig.getApiUrl('/api/product-images');
  }

  private isVisibleRootProduct(product: any): boolean {
    if (!product || !product.id || product.parentId) {
      return false;
    }

    const isActive = product.isActive ?? true;
    const isDeleted = product.isDeleted ?? false;
    return isActive && !isDeleted;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
    private apiConfig: ApiConfigService,
    private currencyService: CurrencyService,
    private lotesService: LotesService,
    private modalStackService: ModalStackService
  ) {}

  ngOnInit() {
    // Verificar si hay token válido
    const token = localStorage.getItem('token');
    if (!token) {
      console.warn('⚠️ No hay token válido. Redirigiendo al login...');
      alert('⚠️ Tu sesión ha expirado. Por favor, inicia sesión nuevamente.');
      this.router.navigate(['/login']);
      return;
    }

    this.initializeApiUrls();
    this.tryLoadStoreData();
    this.watchStoreIdChanges();
    
    const savedLow = localStorage.getItem('lowStockThreshold');
    const savedNormal = localStorage.getItem('normalStockThreshold');

    if (savedLow) this.lowStockThreshold = Number(savedLow);
    if (savedNormal) this.normalStockThreshold = Number(savedNormal);
  
    // Sincronizar el formulario con los valores cargados
    this.thresholdForm.lowStockThreshold = this.lowStockThreshold;
    this.thresholdForm.normalStockThreshold = this.normalStockThreshold;

    // Initialize currency
    this.availableCurrencies = this.currencyService.getCurrencies();
    this.selectedCurrency = this.currencyService.getCurrentCurrency();
  }

  private tryLoadStoreData() {
    const initialStoreId = this.getStoreIdFromRoute(this.route);
    if (initialStoreId > 0) {
      this.storeId = initialStoreId;
      this.loadStoreData();
      this.loadStoreProducts();
      this.loadAdministrativeCosts();
    }
  }

  private watchStoreIdChanges() {
    let current: ActivatedRoute | null = this.route;
    while (current) {
      current.params.subscribe(params => {
        const nextStoreId = +params['id'];
        if (nextStoreId && nextStoreId !== this.storeId) {
          this.storeId = nextStoreId;
          this.loadStoreData();
          this.loadStoreProducts();
          this.loadAdministrativeCosts();
        }
      });
      current = current.parent;
    }
  }

  private getStoreIdFromRoute(route: ActivatedRoute): number {
    let current: ActivatedRoute | null = route;
    while (current) {
      const id = current.snapshot.params['id'];
      if (id) {
        return +id;
      }
      current = current.parent;
    }
    return 0;
  }

  loadStoreData() {
    const token = localStorage.getItem('token');
    console.log('loadStoreData - token:', !!token);
    if (!token) {
      console.warn('⚠️ No hay token en localStorage');
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    console.log('loadStoreData - haciendo GET a:', `${this.apiStoresUrl}/${this.storeId}`);
    this.http.get<any>(`${this.apiStoresUrl}/${this.storeId}`, { headers }).subscribe({
      next: (data) => {
        console.log('loadStoreData - SUCCESS:', data);
        this.store = data;
        this.ngZone.run(() => this.cdr.detectChanges());
      },
      error: (err) => {
        console.error('loadStoreData - ERROR:', err);
        // Si recibe 401 o 403, redirige al login
        if (err.status === 401 || err.status === 403) {
          console.warn('🔐 Token inválido o expirado. Redirigiendo al login...');
          localStorage.removeItem('token');
          alert('⚠️ Tu sesión ha expirado. Por favor, inicia sesión nuevamente.');
          this.router.navigate(['/login']);
          return;
        }
        this.ngZone.run(() => this.cdr.detectChanges());
      }
    });
  }

  loadStoreProducts() {
    const token = localStorage.getItem('token');
    console.log('loadStoreProducts - token:', !!token);
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    console.log('loadStoreProducts - haciendo GET a:', `${this.apiProductsUrl}/store/${this.storeId}`);
    this.http.get<any[]>(`${this.apiProductsUrl}/store/${this.storeId}`, { headers }).subscribe({
      next: (data) => {
        console.log('loadStoreProducts - SUCCESS, cantidad de productos:', data?.length);
        const all = Array.isArray(data) ? data : [];
        const products = all.filter(p => this.isVisibleRootProduct(p));
        // Ordenar productos por nombre para lista ordenada
        products.sort((a: any, b: any) => (a?.name || '').localeCompare(b?.name || ''));
        this.products = products;
        this.productNameMap = {};
        this.products.forEach(p => {
          if (p && p.id) {
            this.productNameMap[p.id] = p.name || `Producto ${p.id}`;
          }
        });

        // El GET a /store/{id} ya trae los lotes (productos con parentId) en la misma respuesta,
        // así que se agrupan aquí en vez de disparar una petición HTTP por producto (eso causaba
        // el parpadeo de las alertas: hasta N peticiones en paralelo, cada una re-renderizando todo).
        this.lotesMap = new Map();
        all.filter(p => p && p.parentId).forEach(lote => {
          const group = this.lotesMap.get(lote.parentId) ?? [];
          group.push(lote);
          this.lotesMap.set(lote.parentId, group);
        });
        this.lotesMap.forEach(group => group.sort((a: any, b: any) => (a?.orderIndex ?? 0) - (b?.orderIndex ?? 0)));

        this.updateFilteredProductsWithActiveLotes();
        this.loading = false;
        this.ngZone.run(() => this.cdr.detectChanges());
      },
      error: (err) => {
        console.error('loadStoreProducts - ERROR:', err);
        this.loading = false;
        this.ngZone.run(() => this.cdr.detectChanges());
      }
    });
  }

  private isProductOnSale(product: any): boolean {
    return !!product && (Boolean(product.isActiveForSale) || Boolean(product.activeLote?.isActiveForSale));
  }

  private getEffectiveAlert(product: any): any {
    const rootProduct = product?.rootProduct
      ?? (product?.parentId ? this.products.find(item => item.id === product.parentId) : product);
    return rootProduct?.alert;
  }

  private isAlertEnabled(alert: any): boolean {
    return alert?.isEnabled === true || alert?.isEnabled === 'true' || alert?.isEnabled === 1;
  }

  private updateProductCaches(updatedProductId?: number) {
    const displayed = this.displayProducts.length > 0 ? this.displayProducts : this.products;
    const low: any[] = [];
    const out: any[] = [];
    const normal: any[] = [];
    const activeAlerts: any[] = [];
    const threshold = this.lowStockThreshold;

    displayed.forEach(product => {
      const stock = this.getDisplayStockForProduct(product);
      const alert = this.getEffectiveAlert(product);
      const effectiveThreshold = alert?.threshold ?? threshold;
      const status: 'normal' | 'low' | 'out' = stock === 0 ? 'out' : stock <= effectiveThreshold ? 'low' : 'normal';

      if (product && product.id) {
        this.productStockCache[product.id] = stock;
        this.productStockStatusCache[product.id] = status;
      }

      if (status === 'normal') {
        normal.push(product);
      }

      if (this.isAlertEnabled(alert)) {
        activeAlerts.push(product);
        if (status === 'low') {
          low.push(product);
        }
        if (status === 'out') {
          out.push(product);
        }
      }
    });

    this.lowStockProducts = low;
    this.outOfStockProducts = out;
    this.normalStockProducts = normal;
    this.activeAlertProducts = activeAlerts;

    if (updatedProductId) {
      this.cdr.detectChanges();
    }
  }

  private computeDisplayStock(product: any): number {
    if (!product || !product.id) {
      return 0;
    }

    if (this.productStockCache[product.id] !== undefined) {
      return this.productStockCache[product.id];
    }

    const stock = product.displayStock !== undefined ? Number(product.displayStock) : this.calculateDisplayStock(product);
    this.productStockCache[product.id] = stock;
    return stock;
  }

  private calculateDisplayStock(product: any): number {
    if (!product) {
      return 0;
    }

    const rootStock = Number(product.stock ?? 0);
    const lotes = this.getLotesForProduct(product.id);

    if (!Array.isArray(lotes) || lotes.length === 0) {
      return rootStock;
    }

    return lotes.reduce((total, lote) => {
      if (!lote) {
        return total;
      }

      const isVisibleLote = (lote.isActive ?? true) && !(lote.isDeleted ?? false);
      if (!isVisibleLote) {
        return total;
      }

      return total + Number(lote.stock ?? 0);
    }, rootStock);
  }

  private buildDisplayProduct(product: any): any {
    if (!product || !product.id) {
      return product;
    }

    const rootName = product.name || '';
    const activeLote = this.getActiveLoteForProduct(product.id);

    if (product.isActiveForSale) {
      return {
        ...product,
        displayName: rootName,
        displayCost: Number(product.cost ?? 0),
        displayPrice: Number(product.price ?? 0),
        displayStock: this.calculateDisplayStock(product),
        rootProductId: product.id,
        rootProduct: product
      };
    }

    if (activeLote) {
      return {
        ...product,
        displayName: rootName,
        displayCost: Number(activeLote.cost ?? product.cost ?? 0),
        displayPrice: Number(activeLote.price ?? product.price ?? 0),
        displayStock: this.calculateDisplayStock(product),
        activeLoteId: activeLote.id,
        activeLote: activeLote,
        rootProductId: product.id,
        rootProduct: product
      };
    }

    return {
      ...product,
      displayName: rootName,
      displayCost: Number(product.cost ?? 0),
      displayPrice: Number(product.price ?? 0),
      displayStock: this.calculateDisplayStock(product),
      rootProductId: product.id,
      rootProduct: product
    };
  }

  private updateFilteredProductsWithActiveLotes() {
    try {
      // Limpiar caches porque la lista puede cambiar cuando llegan lotes activos.
      this.productStockCache = {};
      this.productStockStatusCache = {};

      this.displayProducts = this.products
        .filter(product => this.isVisibleRootProduct(product))
        .map(product => this.buildDisplayProduct(product));

      this.displayProducts.sort((a: any, b: any) => (a?.displayName || '').localeCompare(b?.displayName || ''));
      this.filteredProducts = [...this.displayProducts];
      this.updateProductCaches();
    } catch (error) {
      console.error('Error en updateFilteredProductsWithActiveLotes:', error);
      this.displayProducts = [];
      this.filteredProducts = [];
    }
  }

  // Helpers para mostrar costo/precio desde el lote activo cuando exista
  getDisplayCost(product: any): number {
    try {
      if (!product) return 0;
      if (product.displayCost !== undefined) {
        return Number(product.displayCost);
      }

      if (!product.id) {
        return Number(product.cost ?? 0);
      }

      const active = this.getActiveLoteForProduct(product.id);
      return Number(active?.cost ?? product.cost ?? 0);
    } catch (error) {
      console.error('Error en getDisplayCost:', error);
      return Number(product?.cost ?? 0);
    }
  }

  getDisplayPrice(product: any): number {
    try {
      if (!product) return 0;
      if (product.displayPrice !== undefined) {
        return Number(product.displayPrice);
      }

      if (!product.id) {
        return Number(product.price ?? 0);
      }

      const active = this.getActiveLoteForProduct(product.id);
      return Number(active?.price ?? product.price ?? 0);
    } catch (error) {
      console.error('Error en getDisplayPrice:', error);
      return Number(product?.price ?? 0);
    }
  }

  onSearch() {
    try {
      if (!Array.isArray(this.displayProducts) || this.displayProducts.length === 0) {
        this.filteredProducts = [];
        return;
      }

      if (!this.searchTerm || this.searchTerm.trim() === '') {
        this.filteredProducts = [...this.displayProducts];
      } else {
        const searchLower = this.searchTerm.toLowerCase().trim();

        this.filteredProducts = this.displayProducts.filter(displayProduct => {
          if (!displayProduct) {
            return false;
          }

          const productName = (displayProduct.name || '').toLowerCase();
          const productDesc = (displayProduct.description || '').toLowerCase();

          return productName.includes(searchLower) || productDesc.includes(searchLower);
        });
      }
    } catch (error) {
      console.error('Error en onSearch:', error);
      this.filteredProducts = [...this.displayProducts];
    }
  }

  toggleCreateForm() {
    this.showCreateForm = !this.showCreateForm;
  }

  createProduct() {
    if (!this.newProduct.name || !this.newProduct.description || this.newProduct.cost <= 0 || this.newProduct.price <= 0) {
      alert('Por favor completa todos los campos correctamente');
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const productData = {
      ...this.newProduct,
      storeId: this.storeId
    };

    // Actualización optimista: agregar inmediatamente a la lista
    const optimisticProduct = {
      id: Date.now(), // ID temporal
      ...productData,
      store: this.store
    };
    this.products.unshift(optimisticProduct); // Agregar al inicio
    this.filteredProducts = [...this.products]; // Actualizar filtered
    this.showCreateForm = false; // Ocultar formulario
    const originalProducts = [...this.products]; // Backup por si falla

    this.http.post(`${this.apiProductsUrl}`, productData, { headers }).subscribe({
      next: (createdProduct: any) => {
        // Reemplazar el producto optimista con el real
        const index = this.products.findIndex(p => p.id === optimisticProduct.id);
        if (index !== -1) {
          this.products[index] = createdProduct;
          this.filteredProducts = [...this.products];
        }
        this.newProduct = { name: '', description: '', cost: 0, price: 0, stock: 0 };
        alert('Producto creado correctamente');
      },
      error: (err) => {
        console.error('Error creando producto:', err);
        // Revertir cambios optimistas
        this.products = originalProducts;
        this.filteredProducts = [...this.products];
        this.showCreateForm = true; // Mostrar formulario de nuevo
        alert('Error al crear el producto. Inténtalo de nuevo.');
      }
    });
  }

  adjustStock(productId: number, delta: number) {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const body = {
      delta: delta,
      transactionType: delta > 0 ? 'ENTRADA' : 'SALIDA',
      userId: 1
    };

    this.http.patch(`${this.apiProductsUrl}/${productId}/adjust-stock`, body, { headers })
      .subscribe({
        next: () => {
          this.loadStoreProducts();
          alert('Stock actualizado correctamente');
        },
        error: (err) => {
          console.error('Error al ajustar stock:', err);
          alert('Error al ajustar el stock');
        }
      });
  }

  deleteProduct(productId: number) {
    if (confirm('¿Estás seguro de eliminar este producto?')) {
      const token = localStorage.getItem('token');
      if (!token) return;

      const headers = new HttpHeaders({
        'Authorization': `Bearer ${token}`
      });

      // Optimistic update: remover de la lista inmediatamente
      const initialProducts = this.products;
      const initialFiltered = this.filteredProducts;
      this.products = this.products.filter(p => p.id !== productId);
      this.filteredProducts = this.filteredProducts.filter(p => p.id !== productId);

      this.http.delete(`${this.apiProductsUrl}/${productId}`, { headers })
        .subscribe({
          next: () => {
            Promise.resolve().then(() => {
              alert('Producto eliminado correctamente');
            });
          },
          error: (err) => {
            console.error('Error al eliminar producto:', err);
            Promise.resolve().then(() => {
              // Restaurar la lista en caso de error
              this.products = initialProducts;
              this.filteredProducts = initialFiltered;
              alert('Error al eliminar el producto');
            });
          }
        });
    }
  }

  goBack() {
    this.router.navigate(['../'], { relativeTo: this.route });
  }

  toggleThresholdConfig() {
    this.showThresholdConfig = !this.showThresholdConfig;
    this.selectedProduct = null; // Cerrar panel de alertas del producto
    if (this.showThresholdConfig) {
      this.thresholdForm = {
        lowStockThreshold: this.lowStockThreshold,
        normalStockThreshold: this.normalStockThreshold
      };
    }
  }

  saveThresholdConfig() {
    if (this.thresholdForm.lowStockThreshold <= 0 || this.thresholdForm.normalStockThreshold <= 0) {
      alert('Los umbrales deben ser mayores que cero.');
      return;
    }
    if (this.thresholdForm.lowStockThreshold >= this.thresholdForm.normalStockThreshold) {
      alert('El nivel de stock bajo debe ser menor al nivel de stock normal.');
      return;
    }

    this.lowStockThreshold = this.thresholdForm.lowStockThreshold;
    this.normalStockThreshold = this.thresholdForm.normalStockThreshold;

    localStorage.setItem('lowStockThreshold', this.lowStockThreshold.toString());
    localStorage.setItem('normalStockThreshold', this.normalStockThreshold.toString());

    this.showThresholdConfig = false;
    this.updateProductCaches();
    alert('Ajustes guardados permanentemente en este navegador.');
  }

  changeCurrency(currencyCode: string | undefined): void {
    if (currencyCode) {
      this.currencyService.setCurrency(currencyCode);
      this.selectedCurrency = this.currencyService.getCurrentCurrency();
    }
  }

  toggleProductsList() {
    this.showProductsList = !this.showProductsList;
    this.saveCollapsibleState('showProductsList', this.showProductsList);
  }

  toggleAdminCostsList() {
    this.showAdminCostsList = !this.showAdminCostsList;
    this.saveCollapsibleState('showAdminCostsList', this.showAdminCostsList);
  }

  private loadCollapsibleState(key: string, defaultValue: boolean): boolean {
    const saved = localStorage.getItem(`inventario_${key}`);
    return saved !== null ? JSON.parse(saved) : defaultValue;
  }

  private saveCollapsibleState(key: string, value: boolean): void {
    localStorage.setItem(`inventario_${key}`, JSON.stringify(value));
  }

  get totalProducts() {
    return this.products.filter(product => this.isVisibleRootProduct(product)).length;
  }

  getLowStockProducts() {
    return this.lowStockProducts;
  }

  getOutOfStockProducts() {
    return this.outOfStockProducts;
  }

  getNormalStockProducts() {
    return this.normalStockProducts;
  }

  // Método para obtener el estado del stock de un producto
  getStockStatus(product: any): 'normal' | 'low' | 'out' {
    const threshold = product?.alert?.threshold ?? this.lowStockThreshold;
    const stock = this.getDisplayStockForProduct(product);

    if (stock === 0) {
      return 'out';
    }
    if (stock <= threshold) {
      return 'low';
    }
    return 'normal';
  }

  getDisplayStockForProduct(product: any): number {
    try {
      if (!product) {
        return 0;
      }

      if (product.displayStock !== undefined) {
        return Number(product.displayStock);
      }

      if (product.parentId) {
        return Number(product.stock ?? 0);
      }

      return this.calculateDisplayStock(product);
    } catch (error) {
      console.error('Error en getDisplayStockForProduct:', error);
      return Number(product?.stock ?? 0);
    }
  }

  // Métodos para gestionar alertas
  openAlertConfig(product: any) {
    this.selectedProduct = { ...product };
    this.alertForm = {
      threshold: product.alert?.threshold ?? 0,
      isEnabled: product.alert?.isEnabled ?? true
    };
    this.showAlertPanel = true;
  }

  closeAlertPanel() {
    this.showAlertPanel = false;
    this.selectedProduct = null;
    this.alertForm = { threshold: 0, isEnabled: true };
  }

  bringDescriptionModalToFront() {
    this.descriptionModalZIndex = this.modalStackService.bringToFront();
  }

  bringEditProductModalToFront() {
    this.editProductModalZIndex = this.modalStackService.bringToFront();
  }

  // Métodos para el modal de descripción
  private resolveProductForDescription(product: any): any {
    if (!product || !product.id) {
      return product;
    }

    const matchingRootProduct = this.products.find(p => p && p.id === product.id && !p.parentId);
    if (matchingRootProduct) {
      return { ...matchingRootProduct };
    }

    if (product.parentId) {
      const rootProduct = this.products.find(p => p && p.id === product.parentId);
      if (rootProduct) {
        return { ...rootProduct };
      }
    }

    return { ...product };
  }

  openDescriptionModal(product: any) {
    try {
      if (!product || !product.id) {
        console.warn('Product inválido:', product);
        return;
      }

      const productToOpen = this.resolveProductForDescription(product);

      this.selectedProductForDescription = productToOpen;
      this.showDescriptionModal = true;
      this.descriptionModalZIndex = this.modalStackService.bringToFront();
      this.refreshDescriptionModalDisplayState();
      this.loadProductImage(productToOpen.id);
      this.verifyCanEditProduct(productToOpen.id);
    } catch (error) {
      console.error('Error en openDescriptionModal:', error);
    }
  }

  closeDescriptionModal() {
    this.showDescriptionModal = false;
    this.selectedProductForDescription = null;
    this.descriptionModalDisplayLote = null;
    this.productImage = null;
    this.imageUploadMessage = '';
    this.closeEditProductModal();
  }

  openLoteEditModal(lote: any) {
    if (!lote || !lote.id) {
      console.warn('Lote inválido para editar:', lote);
      return;
    }

    this.selectedProductForDescription = { ...lote };
    this.showDescriptionModal = true;
    this.loadProductImage(lote.id);
    this.openEditProductModal();
    this.cdr.markForCheck();
  }

  // Métodos para el modal de edición de productos
  openEditProductModal() {
    if (!this.selectedProductForDescription) return;

    // Verificar si el producto puede editarse
    this.verifyCanEditProduct(this.selectedProductForDescription.id);
    
    // Preparar el formulario de edición
    this.editProductForm = {
      name: this.selectedProductForDescription.name,
      description: this.selectedProductForDescription.description,
      cost: this.selectedProductForDescription.cost,
      price: this.selectedProductForDescription.price
    };
    
    this.editProductError = '';
    this.showEditProductModal = true;
    this.editProductModalZIndex = this.modalStackService.bringToFront();
  }

  closeEditProductModal() {
    this.showEditProductModal = false;
    this.isEditingProduct = false;
    this.canEditProduct = false;
    this.editProductError = '';
    this.editProductForm = {
      name: '',
      description: '',
      cost: 0,
      price: 0
    };
  }

  verifyCanEditProduct(productId: number) {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.get<any>(`${this.apiProductsUrl}/${productId}/can-edit`, { headers })
      .subscribe({
        next: (response) => {
          this.canEditProduct = response.canEdit;
          if (!this.canEditProduct) {
            this.editProductError = response.message;
          }
          this.cdr.markForCheck();
        },
        error: (error) => {
          this.canEditProduct = false;
          this.editProductError = error.error?.message || 'No se puede editar este producto';
          this.cdr.markForCheck();
        }
      });
  }

  validateEditProductForm(): boolean {
    if (!this.editProductForm.name || this.editProductForm.name.trim() === '') {
      this.editProductError = 'El nombre del producto es requerido';
      return false;
    }

    if (!this.editProductForm.description || this.editProductForm.description.trim() === '') {
      this.editProductError = 'La descripción del producto es requerida';
      return false;
    }

    if (this.editProductForm.cost <= 0) {
      this.editProductError = 'El costo debe ser mayor a 0';
      return false;
    }

    if (this.editProductForm.price <= 0) {
      this.editProductError = 'El precio debe ser mayor a 0';
      return false;
    }

    if (this.editProductForm.price <= this.editProductForm.cost) {
      this.editProductError = 'El precio debe ser mayor al costo para obtener ganancia';
      return false;
    }

    return true;
  }

  saveEditedProduct() {
    if (!this.validateEditProductForm()) {
      return;
    }

    if (!this.selectedProductForDescription?.id) {
      this.editProductError = 'ID de producto no disponible';
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const productData = {
      name: this.editProductForm.name.trim(),
      description: this.editProductForm.description.trim(),
      cost: this.editProductForm.cost,
      price: this.editProductForm.price,
      stock: this.selectedProductForDescription.stock,
      storeId: this.storeId
    };

    this.isEditingProduct = true;
    this.cdr.markForCheck();

    this.http.put(`${this.apiProductsUrl}/${this.selectedProductForDescription.id}`, productData, { headers })
      .subscribe({
        next: (updatedProduct: any) => {
          // Actualizar el producto en la lista
          const index = this.products.findIndex(p => p.id === updatedProduct.id);
          if (index !== -1) {
            this.products[index] = updatedProduct;
            this.filteredProducts = [...this.products];
          }

          // Actualizar la selección actual
          this.selectedProductForDescription = updatedProduct;

          this.isEditingProduct = false;
          this.closeEditProductModal();
          this.showDescriptionModal = true; // Mantener modal de descripción abierto
          this.cdr.markForCheck();
          
          alert('Producto actualizado correctamente');
        },
        error: (err) => {
          console.error('Error editando producto:', err);
          this.editProductError = err.error?.message || 'Error al editar el producto. Inténtalo de nuevo.';
          this.isEditingProduct = false;
          this.cdr.markForCheck();
        }
      });
  }

  // Abrir descripción modal desde galería (mantiene galería abierta)
  openDescriptionModalFromGallery(product: any) {
    const productToOpen = this.resolveProductForDescription(product);

    this.selectedProductForDescription = productToOpen;
    this.showDescriptionModal = true;
    this.descriptionModalZIndex = this.modalStackService.bringToFront();
    this.refreshDescriptionModalDisplayState();
    this.loadProductImage(productToOpen.id);
    this.verifyCanEditProduct(productToOpen.id);
  }

  // Métodos para manejo de imágenes
  loadProductImage(productId: number) {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.get<any>(`${this.apiProductImagesUrl}/${productId}`, { headers })
      .subscribe({
        next: (image) => {
          this.productImage = image;
          this.cdr.markForCheck();
        },
        error: (error) => {
          this.productImage = null;
          this.cdr.markForCheck();
        }
      });
  }

  getProductImageUrl(productId: number | undefined): string {
    if (!productId) return '';
    return `${this.apiProductImagesUrl}/file/${productId}`;
  }

  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (!file) return;

    // Validar tamaño
    const maxSize = 2 * 1024 * 1024; // 2MB
    if (file.size > maxSize) {
      this.showImageMessage('El archivo supera 2MB', 'error');
      return;
    }

    // Validar tipo
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      this.showImageMessage('Formato no permitido. Solo JPG, PNG o WebP', 'error');
      return;
    }

    this.uploadProductImage(file);
  }

  uploadProductImage(file: File) {
    if (!this.selectedProductForDescription?.id) return;

    this.isUploadingImage = true;
    this.cdr.markForCheck();

    const token = localStorage.getItem('token');
    if (!token) {
      this.showImageMessage('No autorizado', 'error');
      this.isUploadingImage = false;
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    const formData = new FormData();
    formData.append('file', file);

    this.http.post<any>(
      `${this.apiProductImagesUrl}/${this.selectedProductForDescription.id}`,
      formData,
      { headers }
    )
      .pipe(
        catchError(error => {
          // Capturar el error 400 localmente para manejarlo aquí
          if (error.status === 400) {
            const errorMsg = error.error?.message || 'Error al subir la imagen';
            this.showImageMessage(errorMsg, 'error');
            this.isUploadingImage = false;
            this.cdr.markForCheck();
            return EMPTY;
          }
          // Propagar otros errores al interceptor global
          throw error;
        })
      )
      .subscribe({
        next: (response) => {
          this.productImage = response.image;
          this.showImageMessage(response.message, 'success');
          this.isUploadingImage = false;
          this.cdr.markForCheck();
        },
        error: (error) => {
          // Solo llega aquí si no fue 400
          const errorMsg = error.error?.message || 'Error al subir la imagen';
          this.showImageMessage(errorMsg, 'error');
          this.isUploadingImage = false;
          this.cdr.markForCheck();
        }
      });
  }

  deleteProductImage() {
    if (!this.selectedProductForDescription?.id) return;

    if (!confirm('¿Estás seguro que deseas eliminar la foto?')) return;

    const token = localStorage.getItem('token');
    if (!token) {
      this.showImageMessage('No autorizado', 'error');
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.delete<any>(
      `${this.apiProductImagesUrl}/${this.selectedProductForDescription.id}`,
      { headers }
    ).subscribe({
      next: () => {
        this.productImage = null;
        this.showImageMessage('Foto eliminada correctamente', 'success');
        this.cdr.markForCheck();
      },
      error: (error) => {
        const errorMsg = error.error?.message || 'Error al eliminar la imagen';
        this.showImageMessage(errorMsg, 'error');
        this.cdr.markForCheck();
      }
    });
  }

  onImageError() {
    this.productImage = null;
    this.cdr.markForCheck();
  }

  private showImageMessage(message: string, type: string) {
    this.imageUploadMessage = message;
    this.imageUploadMessageType = type;
    this.cdr.markForCheck();
    
    this.ngZone.runOutsideAngular(() => {
      setTimeout(() => {
        this.ngZone.run(() => {
          this.imageUploadMessage = '';
          this.cdr.markForCheck();
        });
      }, 4000);
    });
  }

  saveProductAlert() {
    if (this.alertForm.threshold < 0) {
      alert('El umbral debe ser mayor o igual a cero');
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const payload = {
      threshold: this.alertForm.threshold,
      isEnabled: this.alertForm.isEnabled
    };

    this.http.put(
      `${this.apiProductsUrl}/${this.selectedProduct.id}/alert`,
      payload,
      { headers }
    ).subscribe({
      next: (updatedAlert: any) => {
        // Actualizar el producto con la nueva alerta
        const productIndex = this.products.findIndex(p => p.id === this.selectedProduct.id);
        if (productIndex !== -1) {
          this.products[productIndex].alert = updatedAlert;
          this.updateFilteredProductsWithActiveLotes();
        }
        this.closeAlertPanel();
        alert('Alerta configurada correctamente');
        this.ngZone.run(() => this.cdr.detectChanges());
      },
      error: (err) => {
        console.error('Error al guardar alerta:', err);
        alert('Error al guardar la alerta');
      }
    });
  }

  // Obtener todos los productos con alertas activas
  getActiveAlerts() {
    return this.activeAlertProducts;
  }

  // Obtener productos con alertas activas que están en estado bajo
  getAlertsWithStatus(status: 'low' | 'out') {
    if (status === 'low') {
      return this.lowStockProducts.filter(p => this.isAlertEnabled(this.getEffectiveAlert(p)));
    }
    if (status === 'out') {
      return this.outOfStockProducts.filter(p => this.isAlertEnabled(this.getEffectiveAlert(p)));
    }
    return [];
  }

  getAlertThreshold(product: any): number {
    return Number(this.getEffectiveAlert(product)?.threshold ?? this.lowStockThreshold);
  }

  /**
   * Retorna únicamente los productos raíz para la sección de estadísticas y alertas.
   * Los lotes/variantes no deben aparecer como elementos principales en esta vista.
   * Además, solo se consideran los productos que están disponibles para venta.
   */
  private getDisplayProductsForAlerts(): any[] {
    try {
      return this.displayProducts.filter(product => this.isProductOnSale(product));
    } catch (error) {
      console.error('Error en getDisplayProductsForAlerts:', error);
      return [];
    }
  }

  // Administrative Costs Methods
  loadAdministrativeCosts() {
    const token = localStorage.getItem('token');
    console.log('loadAdministrativeCosts - token:', !!token);
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.loadingAdminCosts = true;
    console.log('loadAdministrativeCosts - haciendo GET a:', `${this.apiAdminCostsUrl}/store/${this.storeId}`);
    this.http.get<any[]>(`${this.apiAdminCostsUrl}/store/${this.storeId}`, { headers }).subscribe({
      next: (data) => {
        console.log('loadAdministrativeCosts - SUCCESS, cantidad de costos:', data?.length);
        this.administrativeCosts = data;
        this.loadingAdminCosts = false;
        this.ngZone.run(() => this.cdr.detectChanges());
      },
      error: (err) => {
        console.error('loadAdministrativeCosts - ERROR:', err);
        this.loadingAdminCosts = false;
        this.ngZone.run(() => this.cdr.detectChanges());
      }
    });
  }

  toggleCreateAdminCostForm() {
    this.showCreateAdminCostForm = !this.showCreateAdminCostForm;
    this.editingAdminCostId = null;
    if (!this.showCreateAdminCostForm) {
      this.newAdminCost = { name: '', cost: 0, description: '' };
    }
  }

  startEditAdminCost(cost: any) {
    this.editingAdminCostId = cost.id;
    this.newAdminCost = {
      name: cost.name,
      cost: cost.cost,
      description: cost.description
    };
    this.showCreateAdminCostForm = true;
  }

  cancelEditAdminCost() {
    this.editingAdminCostId = null;
    this.newAdminCost = { name: '', cost: 0, description: '' };
    this.showCreateAdminCostForm = false;
  }

  saveAdminCost() {
    if (!this.newAdminCost.name || !this.newAdminCost.description || this.newAdminCost.cost <= 0) {
      alert('Por favor completa todos los campos correctamente');
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const adminCostData = {
      ...this.newAdminCost,
      storeId: this.storeId
    };

    console.log('saveAdminCost -> adminCostData:', adminCostData, 'editingAdminCostId:', this.editingAdminCostId);

    if (this.editingAdminCostId) {
      // Update existing cost
      this.http.put(`${this.apiAdminCostsUrl}/${this.editingAdminCostId}`, adminCostData, { headers }).subscribe({
        next: (updatedCost: any) => {
          console.log('updateAdminCost -> updatedCost:', updatedCost);
          Promise.resolve().then(() => {
            this.administrativeCosts = this.administrativeCosts.map(cost =>
              cost.id === this.editingAdminCostId ? updatedCost : cost
            );
            this.cancelEditAdminCost();
            this.showAdminCostsList = true;
            alert('Costo administrativo actualizado correctamente');
          });
        },
        error: (err) => {
          console.error('Error actualizando costo administrativo:', err);
          const message = err?.error?.message || 'Error al actualizar el costo administrativo';
          alert(message);
        }
      });
    } else {
      // Create new cost
      
      const firstConfirm = confirm('¿Estás seguro de crear este costo administrativo?');
      if (!firstConfirm) return;

      // 2. Preparar el token y los headers
      const token = localStorage.getItem('token');
      if (!token) {
      alert('Sesión expirada. Por favor, inicia sesión de nuevo.');
      return;
      }

      const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
      });
      this.http.post(`${this.apiAdminCostsUrl}`, adminCostData, { headers }).subscribe({
        next: (createdCost: any) => {
          console.log('createAdminCost -> createdCost:', createdCost);
           
            this.administrativeCosts = [createdCost, ...this.administrativeCosts];
            this.newAdminCost = { name: '', cost: 0, description: '' };
            this.showCreateAdminCostForm = false;
            this.showAdminCostsList = true;

            this.ngZone.run(() => this.cdr.detectChanges());

            alert('Costo administrativo creado correctamente');
          
        },
        error: (err) => {
          console.error('Error creando costo administrativo:', err);
          const message = err?.error?.message || 'Error al crear el costo administrativo';
          alert(message);
        }
      });
    }
  }

  deleteAdminCost(costId: number) {
    if (confirm('¿Estás seguro de eliminar este costo administrativo? Esta acción no se puede deshacer.')) {
      const confirmDelete = confirm('Esta es su última advertencia. ¿Desea continuar con la eliminación?');
      if (!confirmDelete) return;

      const token = localStorage.getItem('token');
      if (!token) return;

      const headers = new HttpHeaders({
        'Authorization': `Bearer ${token}`
      });

      this.http.delete(`${this.apiAdminCostsUrl}/${costId}`, { headers }).subscribe({
        next: () => {
          this.loadAdministrativeCosts();
          alert('Costo administrativo eliminado correctamente');
        },
        error: (err) => {
          console.error('Error al eliminar costo administrativo:', err);
          alert('Error al eliminar el costo administrativo');
        }
      });
    }
  }

  /**
   * Gallery Modal methods
   */
  openGalleryModal() {
    this.showGalleryModal = true;
  }

  closeGalleryModal() {
    this.showGalleryModal = false;
  }

  /**
   * Lotes Modal methods
   */
  openLotesModal(product: any) {
    try {
      if (!product || !product.id) {
        console.warn('Product inválido:', product);
        return;
      }

      // Si es un lote (tiene parentId), abrir modal del raíz para ver todos los lotes
      let productToOpen = product;
      if (product.parentId) {
        console.log('🔍 Detectado lote clickeado. Buscando producto raíz ID:', product.parentId);
        const rootProduct = this.products.find(p => p && p.id === product.parentId);
        if (rootProduct) {
          productToOpen = rootProduct;
          console.log('✅ Producto raíz encontrado:', rootProduct.name);
        } else {
          console.warn(`⚠️ Producto raíz con ID ${product.parentId} no encontrado. Usando lote original.`);
          // Si no encontramos el raíz, usar el lote original
          productToOpen = product;
        }
      }
      
      console.log('📋 Abriendo modal de lotes para:', productToOpen.name);
      this.selectedProductForLotes = productToOpen;
      // Cargar lista completa de lotes cuando se abre el modal
      this.loadLotesForProduct(productToOpen.id);
      this.showLotesModal = true;
    } catch (error) {
      console.error('Error en openLotesModal:', error);
    }
  }

  closeLotesModal() {
    this.showLotesModal = false;
    this.selectedProductForLotes = null;
  }

  loadLotesForProduct(productId: number, onComplete?: () => void) {
    this.lotesService.getLotesByProductId(productId).subscribe({
      next: (lotes) => {
        this.lotesMap.set(productId, lotes);
        this.updateFilteredProductsWithActiveLotes();
        if (onComplete) {
          onComplete();
        }
      },
      error: (err) => {
        console.error('Error cargando lotes:', err);
        this.lotesMap.set(productId, []);
        this.updateFilteredProductsWithActiveLotes();
        if (onComplete) {
          onComplete();
        }
      }
    });
  }

  onLotesUpdated() {
    try {
      const rootId = this.selectedProductForLotes?.id || this.selectedProductForDescription?.parentId || this.selectedProductForDescription?.id;
      if (rootId) {
        this.loadLotesForProduct(rootId, () => {
          this.refreshDescriptionModalDisplayState();
          this.closeLotesModal();
          this.updateFilteredProductsWithActiveLotes();
          this.cdr.detectChanges();
        });
      } else {
        this.refreshDescriptionModalDisplayState();
        this.closeLotesModal();
        this.updateFilteredProductsWithActiveLotes();
        this.cdr.detectChanges();
      }
    } catch (error) {
      console.error('Error en onLotesUpdated:', error);
      this.closeLotesModal();
    }
  }

  getLotesForProduct(productId: number): any[] {
    try {
      // Validación: productId debe ser válido
      if (!productId || productId <= 0) {
        return [];
      }

      const lotes = this.lotesMap.get(productId);
      
      // Si lotes es null/undefined, retornar array vacío
      if (!lotes) {
        return [];
      }

      // Si lotes no es un array, retornar array vacío
      if (!Array.isArray(lotes)) {
        console.warn(`Lotes para productId ${productId} no es un array:`, lotes);
        return [];
      }

      return lotes;
    } catch (error) {
      console.error(`Error en getLotesForProduct(${productId}):`, error);
      return [];
    }
  }

  getActiveLoteForProduct(productId: number): any | null {
    try {
      // Validación: productId debe ser válido
      if (!productId || productId <= 0) {
        return null;
      }

      const lotes = this.getLotesForProduct(productId);
      
      // Validación: si lotes es null o undefined
      if (!Array.isArray(lotes) || lotes.length === 0) {
        return null;
      }

      // Buscar primero el lote activo para venta (isActiveForSale = true)
      const activeForSaleLote = lotes.find(l => 
        l && l.isActive && l.isActiveForSale
      );
      if (activeForSaleLote) {
        return activeForSaleLote;
      }
      
      // Si no hay lote activo para venta, devolver el primer lote activo
      const firstActiveLote = lotes.find(l => l && l.isActive);
      return firstActiveLote || null;
    } catch (error) {
      console.error(`Error en getActiveLoteForProduct(${productId}):`, error);
      return null;
    }
  }

  getDisplayProductName(product: any): string {
    try {
      if (!product) {
        return '';
      }

      if (product.displayName) {
        return product.displayName;
      }

      if (product.parentId) {
        const rootProduct = this.products.find(p => p && p.id === product.parentId);
        if (rootProduct?.name) {
          return rootProduct.name;
        }
      }

      return product.name || '';
    } catch (error) {
      console.error('Error en getDisplayProductName:', error);
      return product?.name || '';
    }
  }

  /**
   * Retorna el lote ACTIVO PARA VENTA a mostrar en el modal de descripción
   * SOLO si selectedProductForDescription es un RAÍZ
   * 
   * Uso en template:
   * - Si this.getActiveLoteToDisplay() retorna algo → usar sus datos
   * - Si retorna null → usar datos del raíz
   */
  refreshDescriptionModalDisplayState() {
    if (!this.selectedProductForDescription) {
      this.descriptionModalDisplayLote = null;
      return;
    }

    if (this.selectedProductForDescription.parentId) {
      this.descriptionModalDisplayLote = null;
      return;
    }

    const activeLote = this.getActiveLoteForProduct(this.selectedProductForDescription.id);
    this.descriptionModalDisplayLote = activeLote?.isActiveForSale ? activeLote : null;
  }

  getActiveLoteToDisplay(): any {
    try {
      if (!this.selectedProductForDescription) {
        return null;
      }

      if (this.selectedProductForDescription.parentId) {
        return null;
      }

      return this.descriptionModalDisplayLote;
    } catch (error) {
      console.error('Error en getActiveLoteToDisplay():', error);
      return null;
    }
  }

  /**
   * Obtiene los datos económicos correctos para mostrar en el modal
   * Simplificado: solo retorna valores basado en getActiveLoteToDisplay()
   */
  getDisplayProductData(product: any, field: 'cost' | 'price' | 'stock'): any {
    try {
      if (!product) return null;

      if (product.parentId) {
        return Number(product.stock ?? 0);
      }

      if (this.descriptionModalDisplayLote && field !== 'stock') {
        return this.descriptionModalDisplayLote[field];
      }

      if (field === 'stock') {
        return this.calculateDisplayStock(product);
      }

      return product[field];
    } catch (error) {
      console.error(`Error en getDisplayProductData(${field}):`, error);
      return product?.[field] || null;
    }
  }

  getTotalStockForProduct(productId: number): number {
    const product = this.products.find(p => p && p.id === productId);
    return this.getDisplayStockForProduct(product);
  }

  calculateMargin(cost: number, price: number): number {
    if (cost === 0) return 0;
    return Math.round(((price - cost) / cost) * 100);
  }

}


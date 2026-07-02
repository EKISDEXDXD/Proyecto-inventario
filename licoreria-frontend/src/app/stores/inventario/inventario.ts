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
  showProductsList: boolean = this.loadCollapsibleState('showProductsList', false);
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
        // Filtrar solo productos principales (parent_id IS NULL)
        this.products = data.filter(p => !p.parentId);
        
        // Cargar lotes para cada producto principal
        let lotesLoaded = 0;
        this.products.forEach(product => {
          this.loadLotesForProduct(product.id, () => {
            lotesLoaded++;
            // Cuando se hayan cargado TODOS los lotes, recalcular la vista
            if (lotesLoaded === this.products.length) {
              this.updateFilteredProductsWithActiveLotes();
              this.loading = false;
              this.ngZone.run(() => this.cdr.detectChanges());
            }
          });
        });
        
        // Si no hay productos, terminar loading
        if (this.products.length === 0) {
          this.loading = false;
          this.ngZone.run(() => this.cdr.detectChanges());
        }
      },
      error: (err) => {
        console.error('loadStoreProducts - ERROR:', err);
        this.loading = false;
        this.ngZone.run(() => this.cdr.detectChanges());
      }
    });
  }

  /**
   * Actualiza filteredProducts reemplazando el producto raíz con el lote activo para venta si existe
   */
  updateFilteredProductsWithActiveLotes() {
    try {
      this.filteredProducts = this.products.map(product => {
        // Validación: si el producto no existe, retornar como está
        if (!product) {
          return product;
        }

        // Si el producto raíz está marcado como activo para venta, mostrar el padre
        if (product.isActiveForSale) {
          return product;
        }
        
        // Si no, buscar un lote activo para venta
        const activeLote = this.getActiveLoteForProduct(product.id);
        if (activeLote) {
          // Devolver el lote activo para mostrar en la lista
          return activeLote;
        }
        
        // Si no hay lote activo, devolver el producto raíz (por defecto)
        return product;
      });
    } catch (error) {
      console.error('Error en updateFilteredProductsWithActiveLotes:', error);
      // Si hay error, mantener el estado actual de filteredProducts
    }
  }

  onSearch() {
    try {
      // Validación: si no hay products, retornar vacío
      if (!Array.isArray(this.products) || this.products.length === 0) {
        this.filteredProducts = [];
        return;
      }

      if (!this.searchTerm || this.searchTerm.trim() === '') {
        // Mostrar todos los productos (raíz) pero reemplazando con lote activo si existe
        const displayProducts = this.products.map(product => {
          if (!product || !product.id) {
            return product;
          }
          
          // Si el padre está activo para venta, mostrar padre
          if (product.isActiveForSale) {
            return product;
          }
          
          const activeLote = this.getActiveLoteForProduct(product.id);
          return activeLote || product;
        });
        this.filteredProducts = displayProducts;
      } else {
        // Filtrar por búsqueda, considerando el lote activo si existe
        const searchLower = this.searchTerm.toLowerCase().trim();
        
        const displayProducts = this.products.map(product => {
          if (!product || !product.id) {
            return product;
          }
          
          // Si el padre está activo para venta, mostrar padre
          if (product.isActiveForSale) {
            return product;
          }
          
          const activeLote = this.getActiveLoteForProduct(product.id);
          return activeLote || product;
        });
        
        this.filteredProducts = displayProducts.filter(displayProduct => {
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
      // Si hay error, mostrar todos los productos
      this.updateFilteredProductsWithActiveLotes();
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
    return this.products.length;
  }

  getLowStockProducts() {
    return this.getDisplayProductsForAlerts().filter(p => {
      const threshold = p.alert?.threshold ?? this.lowStockThreshold;
      return p.stock > 0 && p.stock <= threshold;
    });
  }

  getOutOfStockProducts() {
    return this.getDisplayProductsForAlerts().filter(p => p.stock === 0);
  }

  getNormalStockProducts() {
    return this.getDisplayProductsForAlerts().filter(p => {
      const threshold = p.alert?.threshold ?? this.lowStockThreshold;
      return p.stock > threshold;
    });
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

      if (product.parentId) {
        return Number(product.stock ?? 0);
      }

      const activeLote = this.getActiveLoteForProduct(product.id);
      if (activeLote && activeLote.isActiveForSale) {
        return Number(activeLote.stock ?? 0);
      }

      if (product.isActiveForSale) {
        return Number(product.stock ?? 0);
      }

      const lotes = this.getLotesForProduct(product.id);
      const activeLotes = (Array.isArray(lotes) ? lotes : []).filter(l => l && l.isActive);
      if (activeLotes.length > 0) {
        return activeLotes.reduce((total, lote) => total + Number(lote.stock || 0), 0);
      }

      return Number(product.stock ?? 0);
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
  openDescriptionModal(product: any) {
    try {
      if (!product || !product.id) {
        console.warn('Product inválido:', product);
        return;
      }

      // Si es un lote (tiene parentId), abrir el modal del producto raíz
      // Pero mostrar los DATOS del lote activo (precio, coste, stock)
      let productToOpen = product;
      if (product.parentId) {
        const rootProduct = this.products.find(p => p && p.id === product.parentId);
        if (rootProduct) {
          productToOpen = rootProduct;
        } else {
          console.warn(`Producto raíz con ID ${product.parentId} no encontrado`);
          productToOpen = product;
        }
      }

      this.selectedProductForDescription = { ...productToOpen };
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
    // Si es un lote (tiene parentId), abrir el modal del producto raíz
    // Pero mostrar los DATOS del lote activo
    let productToOpen = product;
    if (product.parentId) {
      const rootProduct = this.products.find(p => p.id === product.parentId);
      if (rootProduct) {
        productToOpen = rootProduct;
      }
    }
    // Abrir modal de descripción encima de la galería
    this.selectedProductForDescription = { ...productToOpen };
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
          this.filteredProducts = [...this.products];
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
    return this.getDisplayProductsForAlerts().filter(p => p.alert?.isEnabled);
  }

  // Obtener productos con alertas activas que están en estado bajo
  getAlertsWithStatus(status: 'low' | 'out') {
    return this.getDisplayProductsForAlerts().filter(p => {
      if (!p.alert?.isEnabled) return false;
      return this.getStockStatus(p) === status;
    });
  }

  /**
   * Retorna los productos/lotes a mostrar en alertas:
   * - Si padre tiene isActiveForSale=true → padre
   * - Si hay lote activo → lote activo
   * - Si no → padre (por defecto)
   */
  private getDisplayProductsForAlerts(): any[] {
    try {
      // Validación: si products no es un array, retornar array vacío
      if (!Array.isArray(this.products)) {
        return [];
      }

      return this.products.map(product => {
        // Validación: producto debe existir
        if (!product || !product.id) {
          return product;
        }

        // Si el padre está activo para venta, mostrar padre
        if (product.isActiveForSale) {
          return product;
        }
        
        // Si hay un lote activo, mostrar lote
        const activeLote = this.getActiveLoteForProduct(product.id);
        if (activeLote) {
          return activeLote;
        }
        
        // Por defecto mostrar padre
        return product;
      }).filter(p => p); // Filtrar null/undefined
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
        if (onComplete) {
          onComplete();
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando lotes:', err);
        this.lotesMap.set(productId, []);
        if (onComplete) {
          onComplete();
        }
      }
    });
  }

  onLotesUpdated() {
    try {
      this.refreshDescriptionModalDisplayState();
      // Cerrar modal primero
      this.closeLotesModal();
      // Luego recalcular la vista con el lote activo actualizado
      this.updateFilteredProductsWithActiveLotes();
      this.cdr.detectChanges();
    } catch (error) {
      console.error('Error en onLotesUpdated:', error);
      // Asegurar que al menos se cierre el modal
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

      if (this.descriptionModalDisplayLote) {
        return this.descriptionModalDisplayLote[field];
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


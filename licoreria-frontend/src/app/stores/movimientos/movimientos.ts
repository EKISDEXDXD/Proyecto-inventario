import { Component, OnInit, OnDestroy, ChangeDetectorRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { JwtHelper } from '../../core/jwt.helper';
import { ApiConfigService } from '../../auth/api-config.service';
import { DescriptionModalComponent } from '../inventario/description-modal.component';
import { LotesService } from '../../services/lotes.service';
import { ReportService, Report } from '../../home/dashboard-info/report.service';
import { CurrencyFormatPipe } from '../../pipes/currency-format.pipe';
import { ClickOutsideDirective } from '../../core/directives/click-outside.directive';
import { PaymentMethodModalComponent } from './payment-method-modal.component';
import { PaymentMethodConfig } from '../../settings/payment-method-config.service';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [CommonModule, FormsModule, CurrencyFormatPipe, ClickOutsideDirective, PaymentMethodModalComponent, DescriptionModalComponent],
  templateUrl: './movimientos.html',
  styleUrl: './movimientos.css'
})
export class MovimientosComponent implements OnInit, OnDestroy {
  @ViewChild(PaymentMethodModalComponent) paymentMethodModal!: PaymentMethodModalComponent;

  storeId: number = 0;
  store: any = null;
  products: any[] = [];
  filteredProducts: any[] = [];
  // Cache para mapa id -> nombre de producto (mejora rendimiento en filtros)
  private productNameMap: { [id: number]: string } = {};
  // Propiedades del selector de lotes
  selectedLote: any = null;
  lotesForSelected: any[] = [];
  activeLoteId: number | null = null;

  transactions: any[] = []; // Los datos originales de la API
  filteredMovimientos: any[] = []; // Los datos que se muestran
  // Estadísticas del día, calculadas una vez en recomputeTodayStats() en vez de en cada ciclo de CD
  todayTransactions: any[] = [];
  todayEntradas: any[] = [];
  todaySalidas: any[] = [];
  todayCount = 0;
  entradasCount = 0;
  salidasCount = 0;
  searchTerm: string = '';
  startDate: string = ''; // Para filtro de transacciones
  endDate: string = ''; // Para filtro de transacciones
  filterDateStart: string = ''; // Para filtrador de fecha en historial
  filterDateEnd: string = ''; // Para filtrador de fecha en historial
  loading: boolean = false;
  historyLoaded = false;
  historyLoading = false;
  historyHasMore = true;
  private historyPage = 0;
  private readonly historyPageSize = 20;
  
  userId: number | null = null;
  userName: string = '';

  // Administrative Costs properties
  administrativeCosts: any[] = [];
  adminCostMovements: any[] = [];
  loadingAdminCosts: boolean = false;
  showAdminCostMovementForm: boolean = false;

  // UI State
  showProductsList: boolean = false;
  showHistoryList: boolean = false;
  showAdminMovementsList: boolean = false;
  showTransactionDetailModal: boolean = false;
  showTodayEntriesModal: boolean = false;
  showTodaySalidasModal: boolean = false;
  showProductDescriptionModal = false;
  selectedProductForDescription: any = null;
  selectedTransaction: any = null;
  transactionComment: string = '';
  showMovementProductsModal: boolean = false;
  movementProductsSummary: any[] = [];

  // Shopping Cart State
  showCart: boolean = false;
  cartItems: any[] = [];
  isRegisteringAllMovements: boolean = false;
  isRegisteringQuickPurchase: boolean = false;
  selectedPaymentMethod: PaymentMethodConfig | null = null;
  selectedPaymentMethodConfigId: number | null = null;
  pendingCartRegistration: boolean = false;
  pendingQuickPurchase: boolean = false;
  quickPurchaseMovement: any | null = null;

  // Reports Properties
  reports: Report[] = [];
  filteredReports: Report[] = [];
  reportLoading: boolean = false;
  showReportForm: boolean = false;
  editingReportId: number | null = null;
  title: string = '';
  description: string = '';
  reportDate: string = '';
  reportColor: string = '#4f46e5';
  reportColors = [
    { label: 'Violeta', value: '#4f46e5' },
    { label: 'Verde', value: '#16a34a' },
    { label: 'Naranja', value: '#f97316' },
    { label: 'Rosa', value: '#ec4899' },
    { label: 'Azul', value: '#2563eb' }
  ];
  filterStartDate: string = '';
  filterEndDate: string = '';
  searchQuery: string = '';
  showFilters: boolean = false;
  currentPage: number = 0;
  pageSize: number = 10;
  totalPages: number = 0;

  // Form fields
  movement = {
    type: 'SALIDA',
    productId: 0,
    quantity: 1,
    reason: 'VENTA'
  };

  // Autocomplete de productos
  productSearchTerm: string = '';
  selectedProductId: number = 0;
  filteredProductsForAutocomplete: any[] = [];
  showProductDropdown: boolean = false;
  recentProducts: any[] = [];
  private productSearchSubject = new Subject<string>();
  private readonly RECENT_PRODUCTS_KEY = 'recent_products_movement';
  private readonly MAX_RECENT_PRODUCTS = 5;

  // Lotes del producto seleccionado
  availableLotes: any[] = [];
  selectedLoteId: number = 0;
  showLotesDropdown: boolean = false;

  // Motivos disponibles según el tipo
  availableReasons: Array<{ value: string; label: string }> = [];

  // Administrative Cost Movement form
  adminCostMovement = {
    administrativeCostId: 0,
    amountPaid: 0,
    type: 'PAGO',
    dateTime: new Date()
  };

  private apiStoresUrl: string = '';
  private apiProductsUrl: string = '';
  private apiTransactionsUrl: string = '';
  private apiAdminCostsUrl: string = '';
  private apiAdminCostMovementsUrl: string = '';
  private jwtHelper = new JwtHelper();
  private refreshInterval: any = null;
  isExternalMode: boolean = false;

  private initializeApiUrls() {
    this.apiStoresUrl = this.apiConfig.getApiUrl('/api/stores');
    this.apiProductsUrl = this.apiConfig.getApiUrl('/api/products');
    this.apiTransactionsUrl = this.apiConfig.getApiUrl('/api/transactions');
    this.apiAdminCostsUrl = this.apiConfig.getApiUrl('/api/administrative-costs');
    this.apiAdminCostMovementsUrl = this.apiConfig.getApiUrl('/api/administrative-cost-movements');
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private apiConfig: ApiConfigService,
    public reportService: ReportService,
    private lotesService: LotesService
  ) { }

  ngOnInit() {
    this.initializeApiUrls();
    this.checkExternalAccess();
    this.extractUserIdFromToken();
    this.tryLoadStoreData();
    this.watchStoreIdChanges();
    this.loadReports();
    this.initializeProductSearchDebounce();
    this.loadRecentProducts();
    this.updateAvailableReasons();
  }

  ngOnDestroy() {
    // Limpiar el intervalo cuando se destruye el componente
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
    this.productSearchSubject.complete();
  }

  onTitleClick() {
    this.goBack();
  }

  // =============== MÉTODOS PARA AUTOCOMPLETE DE PRODUCTOS ===============

  private initializeProductSearchDebounce() {
    this.productSearchSubject.pipe(
      debounceTime(150),
      distinctUntilChanged()
    ).subscribe(() => {
      this.filterProductsForAutocomplete();
    });
  }

  private loadRecentProducts() {
    const stored = localStorage.getItem(this.RECENT_PRODUCTS_KEY);
    if (stored) {
      try {
        const recentIds = JSON.parse(stored) as number[];
        const inventoryProducts = this.getInventoryDisplayProducts();
        this.recentProducts = inventoryProducts.filter(p => recentIds.includes(p.id)).slice(0, this.MAX_RECENT_PRODUCTS);
      } catch (e) {
        console.error('Error loading recent products:', e);
      }
    }
  }

  private saveRecentProduct(productId: number) {
    const stored = localStorage.getItem(this.RECENT_PRODUCTS_KEY);
    let recentIds: number[] = [];
    
    if (stored) {
      try {
        recentIds = JSON.parse(stored);
      } catch (e) {
        console.error('Error parsing recent products:', e);
      }
    }

    // Remover si ya existe y agregar al inicio
    recentIds = recentIds.filter(id => id !== productId);
    recentIds.unshift(productId);
    
    // Guardar solo los últimos MAX_RECENT_PRODUCTS
    recentIds = recentIds.slice(0, this.MAX_RECENT_PRODUCTS);
    localStorage.setItem(this.RECENT_PRODUCTS_KEY, JSON.stringify(recentIds));
    
    // Actualizar array local
    this.loadRecentProducts();
  }

  onProductSearch() {
    this.productSearchSubject.next(this.productSearchTerm);
    this.openProductDropdown();
  }

  private filterProductsForAutocomplete() {
    const inventoryProducts = this.getInventoryDisplayProducts();
    const searchLower = this.productSearchTerm.trim().toLowerCase();

    if (searchLower === '') {
      this.filteredProductsForAutocomplete = inventoryProducts;
    } else {
      this.filteredProductsForAutocomplete = inventoryProducts.filter(product => {
        const name = (product?.name || '').toLowerCase();
        const description = (product?.description || '').toLowerCase();
        return name.includes(searchLower) || description.includes(searchLower);
      });
    }

    this.showProductDropdown = true;
    this.cdr.detectChanges();
  }

  openProductDropdown() {
    this.productSearchTerm = this.productSearchTerm || '';
    this.filteredProductsForAutocomplete = this.getInventoryDisplayProducts();
    this.showProductDropdown = true;
    this.cdr.detectChanges();
  }

  closeProductDropdown() {
    this.showProductDropdown = false;
    this.cdr.detectChanges();
  }

  selectProductFromAutocomplete(product: any) {
    this.selectedProductId = product.id;
    this.movement.productId = product.id;
    this.productSearchTerm = product.name;
    this.showProductDropdown = false;
    this.saveRecentProduct(product.id);

    this.availableLotes = this.buildProductSelectionOptions(product);
    this.selectedLoteId = this.getDefaultSelectedLoteId(this.availableLotes);
    this.showLotesDropdown = false;

    if (this.selectedLoteId > 0) {
      this.movement.productId = this.selectedLoteId;
    } else {
      this.movement.productId = product.id;
    }

    setTimeout(() => {
      const quantityInput = document.querySelector('input[name="quantity"]') as HTMLInputElement;
      if (quantityInput) {
        quantityInput.focus();
      }
    }, 100);

    this.cdr.detectChanges();
  }

  private buildProductSelectionOptions(product: any): any[] {
    const inventoryProducts = Array.isArray(this.products) ? this.products : [];
    if (!product?.id) {
      return [];
    }

    const selectedProduct = inventoryProducts.find(item => item && item.id === product.id) || product;
    const rootProduct = selectedProduct.parentId == null
      ? selectedProduct
      : inventoryProducts.find(item => item && item.id === selectedProduct.parentId) || selectedProduct;

    const rootId = rootProduct?.id ?? selectedProduct.id;
    const relatedProducts = inventoryProducts.filter(item =>
      item && item.id && (item.id === rootId || item.parentId === rootId)
    );

    const options = relatedProducts.map(item => ({
      ...item,
      isRootOption: item.id === rootId,
      isActiveForSale: item.isActiveForSale === true,
      isActive: item.isActive !== false
    }));

    return options.filter((option, index, array) => array.findIndex(item => item.id === option.id) === index);
  }

  private getDefaultSelectedLoteId(options: any[]): number {
    const activeForSaleOption = options.find(option => option.isActiveForSale === true);
    if (activeForSaleOption) {
      return activeForSaleOption.id;
    }

    const activeOption = options.find(option => option.isActive !== false);
    return activeOption?.id ?? options[0]?.id ?? 0;
  }

  getSelectedLoteLabel(): string {
    const selected = this.availableLotes.find(lote => lote.id === this.selectedLoteId);
    if (!selected) {
      return 'Seleccionar lote...';
    }

    const parts = [selected.name];
    if (selected.isRootOption) {
      parts.push('Raíz');
    } else if (selected.isActiveForSale) {
      parts.push('Activo');
    }

    parts.push(`Stock: ${Number(selected.stock || 0)}`);
    parts.push(`Precio: $${Number(selected.price || 0)}`);
    return parts.join(' • ');
  }

  getLoteTypeLabel(lote: any): string {
    return lote?.isRootOption ? 'Producto raíz' : 'Lote';
  }

  selectLote(lote: any) {
    this.selectedLoteId = lote.id;
    this.movement.productId = lote.id;
    this.showLotesDropdown = false;
    this.cdr.detectChanges();
  }

  clearProductSearch() {
    this.productSearchTerm = '';
    this.selectedProductId = 0;
    this.movement.productId = 0;
    this.movement.quantity = 1;
    this.filteredProductsForAutocomplete = [];
    this.showProductDropdown = false;
    this.availableLotes = [];
    this.selectedLoteId = 0;
    this.showLotesDropdown = false;
    this.cdr.detectChanges();
  }

  onProductSearchKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      this.closeProductDropdown();
    }
  }

  // =============== MÉTODOS PARA MOTIVOS DINÁMICOS ===============

  private updateAvailableReasons() {
    if (this.movement.type === 'ENTRADA') {
      // ENTRADA: COMPRA, AJUSTE
      this.availableReasons = [
        { value: 'COMPRA', label: 'Compra' },
        { value: 'AJUSTE', label: 'Ajuste' }
      ];
      // Si el reason actual no es válido, resetear a COMPRA
      if (this.movement.reason !== 'COMPRA' && this.movement.reason !== 'AJUSTE') {
        this.movement.reason = 'COMPRA';
      }
    } else if (this.movement.type === 'SALIDA') {
      // SALIDA: VENTA, DEVOLUCIÓN, PÉRDIDA, AJUSTE
      this.availableReasons = [
        { value: 'VENTA', label: 'Venta' },
        { value: 'DEVOLUCION', label: 'Devolución' },
        { value: 'PERDIDA', label: 'Pérdida' },
        { value: 'AJUSTE', label: 'Ajuste' }
      ];
      // Si el reason actual no es válido, resetear a VENTA
      if (this.movement.reason !== 'VENTA' && this.movement.reason !== 'DEVOLUCION' && 
          this.movement.reason !== 'PERDIDA' && this.movement.reason !== 'AJUSTE') {
        this.movement.reason = 'VENTA';
      }
    }
    this.cdr.detectChanges();
  }

  onMovementTypeChange() {
    this.clearProductSearch(); // Limpiar producto al cambiar tipo
    this.updateAvailableReasons(); // Actualizar motivos disponibles
  }

  private checkExternalAccess() {
    const externalStore = sessionStorage.getItem('externalStore');

    // Obtener el ID de la ruta padre (tienda/:id)
    let currentUrlId: string | null = null;
    if (this.route.parent) {
      currentUrlId = this.route.parent.snapshot.paramMap.get('id');
    }

    // Fallback al método anterior si es necesario
    if (!currentUrlId) {
      currentUrlId = this.route.snapshot.paramMap.get('id');
    }

    if (externalStore && currentUrlId) {
      const data = JSON.parse(externalStore);

      if (data.id == currentUrlId && data.isExternal) {
        this.isExternalMode = true;
        return;
      }
    }

    this.isExternalMode = false;
  }

  private extractUserIdFromToken() {
    const token = localStorage.getItem('token');
    if (token) {
      this.userId = this.jwtHelper.getUserId(token);
      // Extraer nombre de usuario del token
      const payload = JSON.parse(atob(token.split('.')[1]));
      this.userName = payload.sub || 'Usuario';
      if (this.userId) {
        console.log('UserID extraído del token:', this.userId);
      } else {
        console.warn('No se pudo extraer el userId del token');
      }
    }
  }

  private tryLoadStoreData() {
    // Obtener ID de la ruta padre (tienda/:id)
    let initialStoreId = 0;
    if (this.route.parent) {
      const parentId = this.route.parent.snapshot.params['id'];
      if (parentId) {
        initialStoreId = +parentId;
      }
    }

    // Fallback al método anterior
    if (!initialStoreId) {
      initialStoreId = this.getStoreIdFromRoute(this.route);
    }

    if (initialStoreId > 0) {
      console.log('Cargando datos para storeId:', initialStoreId);
      this.storeId = initialStoreId;
      this.restoreHistoryState();
      this.loadStoreData();
      this.loadStoreProducts();
      this.loadAdministrativeCosts();
      this.loadAdministrativeCostMovements();
    }
  }

  private watchStoreIdChanges() {
    // Escuchar cambios en los parámetros de la ruta padre
    if (this.route.parent) {
      this.route.parent.params.subscribe(params => {
        const nextStoreId = +params['id'];
        if (nextStoreId && nextStoreId !== this.storeId) {
          console.log('Cambio de tienda detectado, nuevo storeId:', nextStoreId);
          this.storeId = nextStoreId;
          this.restoreHistoryState();
          this.loadStoreData();
          this.loadStoreProducts();
          this.loadAdministrativeCosts();
          this.loadAdministrativeCostMovements();
        }
      });
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
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.get<any>(`${this.apiStoresUrl}/${this.storeId}`, { headers }).subscribe({
      next: (data) => {
        this.store = data;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando tienda:', err);
      }
    });
  }

  private getInventoryDisplayProducts(): any[] {
    const inventoryProducts = Array.isArray(this.products) ? this.products : [];
    const visibleRootProducts = inventoryProducts.filter(product => {
      if (!product || !product.id) {
        return false;
      }
      return product.parentId == null && (product.isActive === true || product.isActive === null || product.isActive === undefined);
    });

    return visibleRootProducts.map(rootProduct => {
      if (!rootProduct) {
        return rootProduct;
      }

      let displayProduct = rootProduct;
      if (!rootProduct.isActiveForSale) {
        const activeLote = inventoryProducts.find(product =>
          product &&
          product.parentId === rootProduct.id &&
          product.isActive !== false &&
          product.isActiveForSale === true
        );

        if (activeLote) {
          displayProduct = activeLote;
        } else {
          const firstActiveLote = inventoryProducts.find(product =>
            product &&
            product.parentId === rootProduct.id &&
            product.isActive !== false
          );

          displayProduct = firstActiveLote || rootProduct;
        }
      }

      const relatedProducts = inventoryProducts.filter(product =>
        product && product.id && (product.id === rootProduct.id || product.parentId === rootProduct.id)
      );
      const totalStock = relatedProducts.reduce((sum, product) => sum + (Number(product?.stock) || 0), 0);

      return {
        ...displayProduct,
        stock: totalStock,
        displayStock: totalStock
      };
    });
  }

  loadStoreProducts() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    console.log('Cargando productos para storeId:', this.storeId);
    this.http.get<any[]>(`${this.apiProductsUrl}/store/${this.storeId}`, { headers }).subscribe({
      next: (data) => {
        console.log('Productos cargados, total:', data?.length);
        this.products = data || [];
        // Construir cache de nombres para búsquedas rápidas
        this.productNameMap = {};
        this.products.forEach(p => {
          if (p && p.id) this.productNameMap[p.id] = p.name || ('Producto ' + p.id);
        });
        this.filteredProducts = this.getInventoryDisplayProducts();
        this.filteredProductsForAutocomplete = this.getInventoryDisplayProducts();
        this.loadRecentProducts();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando productos:', err);
        this.products = [];
        this.filteredProducts = [];
        this.filteredProductsForAutocomplete = [];
        this.cdr.detectChanges();
      }
    });
  }

  loadTransactions(page = 0) {
    if (this.historyLoading || (!this.historyHasMore && page > 0)) return;
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.historyLoading = true;
    const endpoint = `${this.apiTransactionsUrl}/store/${this.storeId}`;
    this.http.get<any[]>(endpoint, { headers }).subscribe({
      next: (data) => {
        const pageItems = Array.isArray(data) ? data : [];
        this.transactions = page === 0 ? pageItems : [...this.transactions, ...pageItems];
        this.historyPage = page;
        this.historyHasMore = false;
        this.applyTransactionFilters(); // Aplicar filtros después de cargar
        this.historyLoaded = true;
        this.historyLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando transacciones:', err);
        this.historyLoading = false;
        this.historyLoaded = true;
        this.cdr.detectChanges();
      }
    });
  }

  private sortTransactions() {
    this.transactions.sort((a, b) => new Date(b.dateTime).getTime() - new Date(a.dateTime).getTime());
    this.applyTransactionFilters();
  }

  trackByTransactionId(_index: number, transaction: any): number {
    return transaction?.id;
  }

  private sortAdminCostMovements() {
    this.adminCostMovements = this.adminCostMovements.sort((a, b) => new Date(b.dateTime).getTime() - new Date(a.dateTime).getTime());
  }

  onSearch() {
    if (this.searchTerm.trim() === '') {
      this.filteredProducts = this.products;
    } else {
      this.filteredProducts = this.products.filter(product =>
        product.name.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        product.description.toLowerCase().includes(this.searchTerm.toLowerCase())
      );
    }
  }

  // Filtrar transacciones por búsqueda y rango de fechas
  private applyTransactionFilters() {
    this.filteredMovimientos = this.transactions.filter(transaction => {
      // Filtro por término de búsqueda (producto)
      const productName = this.getProductName(transaction.productId).toLowerCase();
      const matchesSearch = !this.searchTerm ||
        productName.includes(this.searchTerm.toLowerCase());

      // Filtro por rango de fechas (filtrador de fecha en historial)
      const transactionDate = new Date(transaction.dateTime);
      const filterStartDate = this.filterDateStart ? new Date(this.filterDateStart) : null;
      const filterEndDate = this.filterDateEnd ? new Date(this.filterDateEnd) : null;

      let matchesDateFilter = true;
      if (filterStartDate) {
        filterStartDate.setHours(0, 0, 0, 0);
        matchesDateFilter = transactionDate >= filterStartDate;
      }
      if (filterEndDate && matchesDateFilter) {
        filterEndDate.setHours(23, 59, 59, 999);
        matchesDateFilter = transactionDate <= filterEndDate;
      }

      // Filtro por rango de fechas (filtro compacto)
      const startDate = this.startDate ? new Date(this.startDate) : null;
      const endDate = this.endDate ? new Date(this.endDate) : null;

      let matchesCompactDateRange = true;
      if (startDate) {
        startDate.setHours(0, 0, 0, 0);
        matchesCompactDateRange = transactionDate >= startDate;
      }
      if (endDate && matchesCompactDateRange) {
        endDate.setHours(23, 59, 59, 999);
        matchesCompactDateRange = transactionDate <= endDate;
      }

      return matchesSearch && matchesDateFilter && matchesCompactDateRange;
    });
    this.recomputeTodayStats();
  }

  // Con historiales grandes, filtrar this.transactions dentro de un getter se ejecutaba en cada ciclo
  // de detección de cambios. Ahora se calcula una sola vez aquí, cada vez que cambian las transacciones.
  private recomputeTodayStats(): void {
    const today = this.startOfToday;
    this.todayTransactions = this.transactions.filter(t => new Date(t.dateTime) >= today);
    this.todayEntradas = this.todayTransactions.filter(t => this.normalizeTransactionType(t) === 'ENTRADA' && this.isCountableStatsMovement(t));
    this.todaySalidas = this.todayTransactions.filter(t => this.normalizeTransactionType(t) === 'SALIDA' && this.isCountableStatsMovement(t));
    this.todayCount = this.todayTransactions.length;
    this.entradasCount = this.todayEntradas.length;
    this.salidasCount = this.todaySalidas.length;
  }

  // Método para limpiar filtro de fecha
  clearDateFilter() {
    this.filterDateStart = '';
    this.filterDateEnd = '';
    this.applyFilters();
  }

  // Método público para aplicar filtros (llamado desde el template)
  public applyFilters() {
    this.applyTransactionFilters();
  }

  // =============== MÉTODOS DEL CARRITO ===============

  addToCart() {
    if (this.movement.productId === 0 || this.movement.quantity <= 0) {
      alert('Por favor selecciona un producto y cantidad válida');
      return;
    }

    if (!this.movement.reason || this.movement.reason.trim() === '') {
      alert('Por favor selecciona un motivo válido');
      return;
    }

    // Encontrar el producto en la lista para obtener su nombre y precio
    const product = this.products.find(p => p.id === this.movement.productId);
    if (!product) {
      alert('Producto no encontrado');
      return;
    }

    // Crear item del carrito
    const cartItem = {
      tempId: Date.now() + Math.random(), // ID temporal único para el carrito
      productId: this.movement.productId,
      productName: product.name,
      type: this.movement.type,
      quantity: this.movement.quantity,
      reason: this.movement.reason,
      price: product.price,
      paymentMethodConfigId: this.selectedPaymentMethod?.id || null,
      paymentMethodName: this.selectedPaymentMethod?.name || null,
      paymentMethodType: this.selectedPaymentMethod?.type || null
    };

    // Agregar al carrito
    this.cartItems.push(cartItem);
    
    // Abrir carrito automáticamente
    this.showCart = true;

    // Limpiar formulario
    this.clearProductSearch();
    this.movement = { 
      type: this.movement.type, 
      productId: 0, 
      quantity: 1, 
      reason: this.movement.reason 
    };

    this.cdr.detectChanges();
  }

  removeFromCart(index: number) {
    this.cartItems.splice(index, 1);
    this.cdr.detectChanges();
  }

  clearCart() {
    if (confirm('¿Estás seguro de que deseas limpiar el carrito?')) {
      this.cartItems = [];
      this.cdr.detectChanges();
    }
  }

  toggleCart() {
    this.showCart = !this.showCart;
    this.cdr.detectChanges();
  }

  registerAllMovements() {
    if (this.cartItems.length === 0) {
      alert('El carrito está vacío. Agrega productos primero.');
      return;
    }

    if (!this.userId) {
      alert('Error: No se pudo identificar el usuario');
      return;
    }

    // Mostrar modal de forma de pago
    this.pendingCartRegistration = true;
    this.pendingQuickPurchase = false;
    this.quickPurchaseMovement = null;
    if (this.selectedPaymentMethod) {
      this.selectedPaymentMethodConfigId = this.selectedPaymentMethod.id;
      this.proceedWithRegistration();
      return;
    }
    this.paymentMethodModal.open();
  }

  quickPurchase() {
    if (this.movement.productId === 0 || this.movement.quantity <= 0) {
      alert('Por favor selecciona un producto y cantidad válida');
      return;
    }

    if (!this.movement.reason || this.movement.reason.trim() === '') {
      alert('Por favor selecciona un motivo válido');
      return;
    }

    const product = this.products.find(p => p.id === this.movement.productId);
    if (!product) {
      alert('Producto no encontrado');
      return;
    }

    if (!this.userId) {
      alert('Error: No se pudo identificar el usuario');
      return;
    }

    this.quickPurchaseMovement = {
      productId: this.movement.productId,
      productName: product.name,
      type: this.movement.type,
      quantity: this.movement.quantity,
      reason: this.movement.reason,
      price: product.price,
      dateTime: null,
      paymentMethodConfigId: this.selectedPaymentMethod?.id || null,
      paymentMethodName: this.selectedPaymentMethod?.name || null
    };

    this.pendingQuickPurchase = true;
    this.pendingCartRegistration = false;

    if (this.selectedPaymentMethod) {
      this.selectedPaymentMethodConfigId = this.selectedPaymentMethod.id;
      this.proceedWithRegistration();
      return;
    }

    this.paymentMethodModal.open();
  }

  onPaymentMethodSelected(paymentMethod: PaymentMethodConfig) {
    this.selectedPaymentMethod = paymentMethod;
    this.selectedPaymentMethodConfigId = paymentMethod.id;
    if (this.pendingCartRegistration || this.pendingQuickPurchase) {
      this.proceedWithRegistration();
    }
  }

  clearPaymentMethodSelection() {
    this.selectedPaymentMethod = null;
    this.selectedPaymentMethodConfigId = null;
  }

  private proceedWithRegistration() {
    if (this.selectedPaymentMethodConfigId === null || (!this.pendingCartRegistration && !this.pendingQuickPurchase)) {
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const now = new Date();
    const timezoneOffset = now.getTimezoneOffset() * 60 * 1000;
    const localDateTime = new Date(now.getTime() - timezoneOffset).toISOString();

    if (this.pendingCartRegistration) {
      this.isRegisteringAllMovements = true;
      this.pendingCartRegistration = false;
      this.cdr.detectChanges();

      const transactionsToRegister = this.cartItems.map(item => ({
        productId: item.productId,
        type: item.type,
        quantity: item.quantity,
        reason: item.reason,
        dateTime: localDateTime,
        userId: this.userId,
        paymentMethodConfigId: item.paymentMethodConfigId || this.selectedPaymentMethodConfigId
      }));

      const optimisticTransactions = transactionsToRegister.map((trans, index) => ({
        id: Date.now() + index,
        ...trans,
        dateTime: localDateTime
      }));

      const originalTransactions = [...this.transactions];
      this.transactions.unshift(...optimisticTransactions);
      this.applyTransactionFilters();
      this.cdr.detectChanges();

      const originalStocks: { [key: number]: number } = {};
      this.cartItems.forEach(item => {
        const productIndex = this.products.findIndex(p => p.id === item.productId);
        if (productIndex !== -1) {
          originalStocks[item.productId] = this.products[productIndex].stock;
          this.products[productIndex].stock += item.type === 'ENTRADA' ? item.quantity : -item.quantity;
        }
      });
      this.cdr.detectChanges();

      const batchRequest = { transactions: transactionsToRegister };

      console.log('Enviando batch:', batchRequest);

      this.http.post(`${this.apiTransactionsUrl}/batch`, batchRequest, { headers }).subscribe({
        next: (response: any) => {
          console.log('Respuesta del servidor:', response);
          const createdTransactions = Array.isArray(response) ? response : response.transactions || [];
          createdTransactions.forEach((createdTrans: any, index: number) => {
            const optIndex = this.transactions.findIndex(t => t.id === optimisticTransactions[index].id);
            if (optIndex !== -1) {
              this.transactions[optIndex] = createdTrans;
            }
          });

          this.sortTransactions();
          this.cartItems = [];
          this.showCart = false;
          this.isRegisteringAllMovements = false;
          this.selectedPaymentMethod = null;
          this.selectedPaymentMethodConfigId = null;
          this.movement = { type: 'ENTRADA', productId: 0, quantity: 0, reason: 'COMPRA' };
          this.updateAvailableReasons();
          this.clearProductSearch();

          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Error registrando movimientos en lote:', err);
          console.error('Status:', err.status);
          console.error('Mensaje:', err.error?.message || err.statusText);
          this.transactions = originalTransactions;
          Object.keys(originalStocks).forEach(productIdStr => {
            const productId = parseInt(productIdStr);
            const productIndex = this.products.findIndex(p => p.id === productId);
            if (productIndex !== -1) {
              this.products[productIndex].stock = originalStocks[productId];
            }
          });

          this.isRegisteringAllMovements = false;
          this.selectedPaymentMethodConfigId = null;
          this.cdr.detectChanges();
          const errorMsg = err.error?.message || 'Error al registrar los movimientos. Inténtalo de nuevo.';
          alert(errorMsg);
        }
      });
    } else if (this.pendingQuickPurchase && this.quickPurchaseMovement) {
      this.isRegisteringQuickPurchase = true;
      this.pendingQuickPurchase = false;
      this.cdr.detectChanges();

      const transactionToRegister = {
        productId: this.quickPurchaseMovement.productId,
        type: this.quickPurchaseMovement.type,
        quantity: this.quickPurchaseMovement.quantity,
        reason: this.quickPurchaseMovement.reason,
        dateTime: localDateTime,
        userId: this.userId,
        paymentMethodConfigId: this.selectedPaymentMethodConfigId
      };

      const optimisticTransaction = {
        id: Date.now(),
        ...transactionToRegister
      };

      const originalTransactions = [...this.transactions];
      const originalStocks: { [key: number]: number } = {};
      const productIndex = this.products.findIndex(p => p.id === this.quickPurchaseMovement.productId);
      if (productIndex !== -1) {
        originalStocks[this.quickPurchaseMovement.productId] = this.products[productIndex].stock;
        this.products[productIndex].stock += this.quickPurchaseMovement.type === 'ENTRADA' ? this.quickPurchaseMovement.quantity : -this.quickPurchaseMovement.quantity;
      }

      this.transactions.unshift(optimisticTransaction);
      this.applyTransactionFilters();
      this.cdr.detectChanges();

      this.http.post(`${this.apiTransactionsUrl}`, transactionToRegister, { headers }).subscribe({
        next: (createdTransaction: any) => {
          const optIndex = this.transactions.findIndex(t => t.id === optimisticTransaction.id);
          if (optIndex !== -1) {
            this.transactions[optIndex] = createdTransaction;
          }

          this.sortTransactions();
          this.isRegisteringQuickPurchase = false;
          this.selectedPaymentMethod = null;
          this.selectedPaymentMethodConfigId = null;
          this.quickPurchaseMovement = null;
          this.movement = { type: 'ENTRADA', productId: 0, quantity: 0, reason: 'COMPRA' };
          this.updateAvailableReasons();
          this.clearProductSearch();
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Error registrando compra rápida:', err);
          this.transactions = originalTransactions;
          this.applyTransactionFilters();
          if (productIndex !== -1) {
            this.products[productIndex].stock = originalStocks[this.quickPurchaseMovement.productId];
          }
          this.isRegisteringQuickPurchase = false;
          this.selectedPaymentMethod = null;
          this.selectedPaymentMethodConfigId = null;
          this.quickPurchaseMovement = null;
          this.cdr.detectChanges();
          const errorMsg = err.error?.message || 'Error al registrar la compra rápida. Inténtalo de nuevo.';
          alert(errorMsg);
        }
      });
    }
  }

  registerMovement() {
    // Mantener el método original para compatibilidad
    this.addToCart();
  }

  deleteTransaction(transactionId: number) {
    if (!confirm('¿Estás seguro de que deseas eliminar este movimiento?')) {
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    // Guardar el estado original para revertir si hay error
    const originalTransactions = [...this.transactions];
    const deletedTransaction = this.transactions.find(t => t.id === transactionId);

    // Eliminación optimista: remover de la lista inmediatamente
    this.transactions = this.transactions.filter(t => t.id !== transactionId);
    this.cdr.detectChanges();

    this.http.delete(`${this.apiTransactionsUrl}/${transactionId}`, { headers }).subscribe({
      next: () => {
        console.log('Transacción eliminada:', transactionId);
        // Recargar productos para actualizar stock después de eliminar
        this.loadStoreProducts();
        this.closeTransactionDetail();
        alert('Movimiento eliminado correctamente');
      },
      error: (err) => {
        console.error('Error eliminando transacción:', err);
        // Revertir eliminación optimista
        this.transactions = originalTransactions;
        this.cdr.detectChanges();
        const message = err?.error?.message || 'Error al eliminar el movimiento. Inténtalo de nuevo.';
        alert(message);
      }
    });
  }

  adjustStock() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const delta = this.movement.type === 'ENTRADA' ? this.movement.quantity : -this.movement.quantity;

    const body = {
      delta: delta,
      transactionType: this.movement.type,
      userId: 1
    };

    this.http.patch(`${this.apiProductsUrl}/${this.movement.productId}/adjust-stock`, body, { headers })
      .subscribe({
        next: () => {
          // Recargar productos para actualizar stock
          this.loadStoreProducts();
        },
        error: (err) => {
          console.error('Error ajustando stock:', err);
        }
      });
  }

  goBack() {
    this.router.navigate(['../'], { relativeTo: this.route });
  }

  openTransactionDetail(transaction: any) {
    this.selectedTransaction = transaction;
    this.transactionComment = '';
    this.showTransactionDetailModal = true;
    this.cdr.detectChanges();
    this.loadTransactionComment(transaction.id);
  }

  closeTransactionDetail() {
    this.showTransactionDetailModal = false;
    this.selectedTransaction = null;
    this.transactionComment = '';
    this.cdr.detectChanges();
  }

  openMovementProductsModal() {
    this.showMovementProductsModal = true;
    this.buildMovementProductsSummary();
    this.cdr.detectChanges();
  }

  closeMovementProductsModal() {
    this.showMovementProductsModal = false;
    this.cdr.detectChanges();
  }

  private buildMovementProductsSummary() {
    const summaryMap: { [key: number]: { productName: string; totalMovements: number; entradas: number; salidas: number; netQuantity: number; } } = {};

    this.todayTransactions.forEach(transaction => {
      const productId = transaction.productId;
      const productName = this.getProductName(productId);
      const type = String(transaction?.type ?? transaction?.transactionType ?? '').trim().toUpperCase();
      const quantity = Number(transaction?.quantity ?? 0);

      if (!summaryMap[productId]) {
        summaryMap[productId] = {
          productName,
          totalMovements: 0,
          entradas: 0,
          salidas: 0,
          netQuantity: 0
        };
      }

      summaryMap[productId].totalMovements += 1;
      if (type === 'ENTRADA') {
        summaryMap[productId].entradas += 1;
        summaryMap[productId].netQuantity += quantity;
      } else if (type === 'SALIDA') {
        summaryMap[productId].salidas += 1;
        summaryMap[productId].netQuantity -= quantity;
      }
    });

    this.movementProductsSummary = Object.values(summaryMap).sort((a, b) => b.totalMovements - a.totalMovements);
  }

  private loadTransactionComment(transactionId: number) {
    const token = localStorage.getItem('token');
    if (!token) {
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.get<any>(`${this.apiTransactionsUrl}/${transactionId}/comments`, { headers }).subscribe({
      next: (response) => {
        this.transactionComment = response?.comment || '';
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando comentario de movimiento:', err);
        this.transactionComment = '';
      }
    });
  }

  saveTransactionComment() {
    if (!this.selectedTransaction) {
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) {
      alert('No se pudo identificar al usuario para guardar el comentario.');
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const body = { comment: this.transactionComment };
    this.http.post<any>(`${this.apiTransactionsUrl}/${this.selectedTransaction.id}/comments`, body, { headers }).subscribe({
      next: (response) => {
        this.transactionComment = response?.comment || '';
        const index = this.transactions.findIndex(t => t.id === this.selectedTransaction.id);
        if (index !== -1) {
          this.transactions[index] = { ...this.transactions[index], comment: this.transactionComment };
        }
        alert('Comentario guardado correctamente.');
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error guardando comentario:', err);
        const message = err?.error?.message || 'Error al guardar el comentario. Inténtalo de nuevo.';
        alert(message);
      }
    });
  }

  getProductName(productId: number): string {
    if (this.productNameMap && this.productNameMap[productId]) {
      return this.productNameMap[productId];
    }
    const product = this.products.find(p => p.id === productId);
    return product ? product.name : 'Producto desconocido';
  }

  private get startOfToday(): Date {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return today;
  }

  private padDate(value: number): string {
    return String(value).padStart(2, '0');
  }

  private normalizeReason(reason: any): string {
    return String(reason ?? '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim()
      .toUpperCase();
  }

  private normalizeTransactionType(transaction: any): string {
    return String(transaction?.type ?? transaction?.transactionType ?? '').trim().toUpperCase();
  }

  private isCountableStatsMovement(transaction: any): boolean {
    const type = this.normalizeTransactionType(transaction);
    const reason = this.normalizeReason(transaction?.reason ?? transaction?.transactionReason);

    if (type === 'ENTRADA') {
      return reason === 'COMPRA';
    }

    if (type === 'SALIDA') {
      return reason === 'VENTA';
    }

    return false;
  }

  get todayLabel(): string {
    const today = new Date();
    return `${this.padDate(today.getDate())}/${this.padDate(today.getMonth() + 1)}`;
  }

  // ===================== ADMINISTRATIVE COSTS METHODS =====================

  loadAdministrativeCosts() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    console.log('Cargando costos administrativos para storeId:', this.storeId);
    this.http.get<any[]>(`${this.apiAdminCostsUrl}/store/${this.storeId}`, { headers }).subscribe({
      next: (data) => {
        console.log('Costos administrativos cargados, total:', data?.length);
        this.administrativeCosts = data;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando costos administrativos:', err);
      }
    });
  }

  loadAdministrativeCostMovements() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    console.log('Cargando movimientos administrativos para storeId:', this.storeId);
    this.loadingAdminCosts = true;
    this.http.get<any[]>(`${this.apiAdminCostMovementsUrl}/store/${this.storeId}`, { headers }).subscribe({
      next: (data) => {
        console.log('Movimientos administrativos cargados, total:', data?.length);
        this.adminCostMovements = data.sort((a, b) => new Date(b.dateTime).getTime() - new Date(a.dateTime).getTime());
        this.loadingAdminCosts = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando movimientos administrativos:', err);
        this.loadingAdminCosts = false;
        this.cdr.detectChanges();
      }
    });
  }

  toggleAdminCostMovementForm() {
    this.showAdminCostMovementForm = !this.showAdminCostMovementForm;
    if (!this.showAdminCostMovementForm) {
      this.adminCostMovement = {
        administrativeCostId: 0,
        amountPaid: 0,
        type: 'PAGO',
        dateTime: new Date()
      };
    }
  }

  toggleProductsList() {
    this.showProductsList = !this.showProductsList;
  }

  openTodayEntriesModal() {
    this.showHistoryList = true;
    if (!this.historyLoaded) this.loadTransactions();
    this.showTodayEntriesModal = true;
  }

  closeTodayEntriesModal() {
    this.showTodayEntriesModal = false;
  }

  openTodaySalidasModal() {
    this.showHistoryList = true;
    if (!this.historyLoaded) this.loadTransactions();
    this.showTodaySalidasModal = true;
  }

  closeTodaySalidasModal() {
    this.showTodaySalidasModal = false;
  }

  openProductDescription(product: any) {
    this.selectedProductForDescription = product;
    this.showProductDescriptionModal = true;
  }

  closeProductDescription() {
    this.showProductDescriptionModal = false;
    this.selectedProductForDescription = null;
  }

  refreshProductDescriptionData() {
    this.loadStoreProducts();
  }

  toggleHistoryList() {
    this.showHistoryList = !this.showHistoryList;
    localStorage.setItem(`movimientos-history-open-${this.storeId}`, String(this.showHistoryList));
    if (this.showHistoryList && !this.historyLoaded) this.loadTransactions();
  }

  private restoreHistoryState() {
    this.showHistoryList = false;
    if (!this.historyLoaded) this.loadTransactions();
  }

  onHistoryScroll(event: Event) {
    const element = event.target as HTMLElement;
    if (element.scrollHeight - element.scrollTop - element.clientHeight < 140 && this.historyHasMore) {
      this.loadTransactions(this.historyPage + 1);
    }
  }

  toggleAdminMovementsList() {
    this.showAdminMovementsList = !this.showAdminMovementsList;
  }

  createAdminCostMovement() {
    console.log('createAdminCostMovement iniciando, adminCostMovement:', this.adminCostMovement);
    if (this.adminCostMovement.administrativeCostId === 0 || this.adminCostMovement.amountPaid <= 0) {
      alert('Por favor selecciona un costo administrativo y monto válido');
      return;
    }

    if (!this.userId) {
      alert('Error: No se pudo identificar el usuario');
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    // Asegurar que now es un Date (el input datetime-local envía string)
    const now = new Date(this.adminCostMovement.dateTime);
    const timezoneOffset = now.getTimezoneOffset() * 60 * 1000; // Convertir a milisegundos
    const localDateTime = new Date(now.getTime() - timezoneOffset).toISOString();

    const movementData = {
      administrativeCostId: this.adminCostMovement.administrativeCostId,
      type: this.adminCostMovement.type,
      amountPaid: this.adminCostMovement.amountPaid,
      dateTime: localDateTime
    };

    console.log('Enviando movimiento administrativo:', movementData);
    // Actualización optimista: crear movimiento con ID temporal
    const optimisticMovement = {
      id: Date.now(),
      ...movementData,
      dateTime: new Date(movementData.dateTime)
    };
    console.log('Movimiento optimista:', optimisticMovement);
    this.adminCostMovements.unshift(optimisticMovement);
    this.cdr.detectChanges();
    const originalMovements = [...this.adminCostMovements];

    this.http.post(`${this.apiAdminCostMovementsUrl}`, movementData, { headers }).subscribe({
      next: (createdMovement: any) => {
        console.log('Movimiento administrativo creado:', createdMovement);
        // Reemplazar el movimiento optimista con el real
        const index = this.adminCostMovements.findIndex(m => m.id === optimisticMovement.id);
        if (index !== -1) {
          this.adminCostMovements[index] = createdMovement;
          // Re-ordenar después de reemplazar
          this.sortAdminCostMovements();
        }
        // Recargar también lista de costos para actualizar datos
        this.loadAdministrativeCosts();
        this.adminCostMovement = {
          administrativeCostId: 0,
          amountPaid: 0,
          type: 'PAGO',
          dateTime: new Date()
        };
        this.showAdminCostMovementForm = false;
        this.cdr.detectChanges();
        alert('Movimiento registrado correctamente');
      },
      error: (err) => {
        console.error('Error registrando movimiento administrativo:', err);
        // Revertir cambios optimistas
        this.adminCostMovements = originalMovements;
        this.cdr.detectChanges();
        alert('Error al registrar el movimiento. Inténtalo de nuevo.');
      }
    });
  }

  deleteAdminCostMovement(movementId: number) {
    if (confirm('¿Estás seguro de eliminar este movimiento?')) {
      const token = localStorage.getItem('token');
      if (!token) return;

      const headers = new HttpHeaders({
        'Authorization': `Bearer ${token}`
      });

      this.http.delete(`${this.apiAdminCostMovementsUrl}/${movementId}`, { headers }).subscribe({
        next: () => {
          this.loadAdministrativeCostMovements();
          alert('Movimiento eliminado correctamente');
        },
        error: (err) => {
          console.error('Error eliminando movimiento:', err);
          alert('Error al eliminar el movimiento');
        }
      });
    }
  }

  getAdminCostName(costId: number): string {
    const cost = this.administrativeCosts.find(c => c.id === costId);
    return cost ? cost.name : 'Costo desconocido';
  }

  // ===================== REPORTS METHODS =====================

  loadReports() {
    if (!this.storeId) return;

    this.reportLoading = true;
    this.reportService.getReportsByStore(this.storeId, this.currentPage, this.pageSize).subscribe({
      next: (response: any) => {
        this.reports = (response.content || response).map((report: any) => ({
          ...report,
          color: report.color || '#4f46e5'
        }));
        this.totalPages = response.totalPages || Math.ceil(this.reports.length / this.pageSize);
        this.filteredReports = [...this.reports];
        this.reportLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando reportes:', err);
        this.reportLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  toggleReportForm() {
    this.showReportForm = !this.showReportForm;
    if (!this.showReportForm) {
      this.resetReportForm();
    }
  }

  resetReportForm() {
    this.title = '';
    this.description = '';
    this.reportDate = '';
    this.reportColor = '#4f46e5';
    this.editingReportId = null;
  }

  submitReport() {
    if (!this.title || !this.description || !this.reportDate) {
      alert('Por favor completa todos los campos');
      return;
    }

    this.reportLoading = true;
    if (this.editingReportId) {
      this.reportService.updateReport(this.editingReportId, this.title, this.description, this.reportDate, this.reportColor)
        .subscribe({
          next: () => {
            this.loadReports();
            this.resetReportForm();
            this.showReportForm = false;
            this.reportLoading = false;
            this.cdr.detectChanges();
            alert('Reporte actualizado correctamente');
          },
          error: (err) => {
            console.error('Error actualizando reporte:', err);
            this.reportLoading = false;
            this.cdr.detectChanges();
            alert('Error al actualizar el reporte');
          }
        });
    } else {
      this.reportService.createReport(this.storeId, this.title, this.description, this.reportDate, this.reportColor)
        .subscribe({
          next: () => {
            this.loadReports();
            this.resetReportForm();
            this.showReportForm = false;
            this.reportLoading = false;
            this.cdr.detectChanges();
            alert('Reporte creado correctamente');
          },
          error: (err) => {
            console.error('Error creando reporte:', err);
            this.reportLoading = false;
            this.cdr.detectChanges();
            alert('Error al crear el reporte');
          }
        });
    }
  }

  getReportStyle(report: Report): { [key: string]: string } {
    const color = report.color || '#4f46e5';
    const lightColor = this.lightenHexColor(color, 18);
    return {
      backgroundColor: color,
      backgroundImage: `linear-gradient(135deg, ${color} 0%, ${lightColor} 100%)`,
      borderTop: `4px solid ${color}`
    };
  }

  private lightenHexColor(hex: string, percent: number): string {
    const cleanedHex = hex.replace('#', '');
    const num = parseInt(cleanedHex, 16);
    let r = (num >> 16) + Math.round(255 * percent / 100);
    let g = ((num >> 8) & 0x00FF) + Math.round(255 * percent / 100);
    let b = (num & 0x0000FF) + Math.round(255 * percent / 100);

    r = Math.min(255, r);
    g = Math.min(255, g);
    b = Math.min(255, b);

    return `rgb(${r}, ${g}, ${b})`;
  }

  deleteReport(reportId: number) {
    if (confirm('¿Estás seguro de que deseas eliminar este reporte?')) {
      this.reportService.deleteReport(reportId).subscribe({
        next: () => {
          this.loadReports();
          alert('Reporte eliminado correctamente');
        },
        error: (err) => {
          console.error('Error eliminando reporte:', err);
          alert('Error al eliminar el reporte');
        }
      });
    }
  }

  toggleFilters() {
    this.showFilters = !this.showFilters;
  }

  onSearchChange() {
    this.applyReportFilters();
  }

  applyDateFilter() {
    this.currentPage = 0;
    this.applyReportFilters();
  }

  private applyReportFilters() {
    this.filteredReports = this.reports.filter(report => {
      const matchesSearch = !this.searchQuery ||
        report.title.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        report.description.toLowerCase().includes(this.searchQuery.toLowerCase());

      const reportDate = new Date(report.reportDate);
      const startDate = this.filterStartDate ? new Date(this.filterStartDate) : null;
      const endDate = this.filterEndDate ? new Date(this.filterEndDate) : null;

      let matchesDateRange = true;
      if (startDate) {
        startDate.setHours(0, 0, 0, 0);
        matchesDateRange = reportDate >= startDate;
      }
      if (endDate && matchesDateRange) {
        endDate.setHours(23, 59, 59, 999);
        matchesDateRange = reportDate <= endDate;
      }

      return matchesSearch && matchesDateRange;
    });

    this.totalPages = Math.ceil(this.filteredReports.length / this.pageSize);
    if (this.currentPage >= this.totalPages) {
      this.currentPage = Math.max(0, this.totalPages - 1);
    }
  }

  resetFilters() {
    // Reset transaction filters
    this.searchTerm = '';
    this.startDate = '';
    this.endDate = '';
    this.applyTransactionFilters();
    
    // Reset report filters
    this.searchQuery = '';
    this.filterStartDate = '';
    this.filterEndDate = '';
    this.currentPage = 0;
    this.filteredReports = [...this.reports];
    this.totalPages = Math.ceil(this.filteredReports.length / this.pageSize);
  }

  nextPage() {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
    }
  }

  previousPage() {
    if (this.currentPage > 0) {
      this.currentPage--;
    }
  }

  get paginatedReports(): Report[] {
    const start = this.currentPage * this.pageSize;
    const end = start + this.pageSize;
    return this.filteredReports.slice(start, end);
  }
}

import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { JwtHelper } from '../../core/jwt.helper';
import { ApiConfigService } from '../../auth/api-config.service';
import { ReportService, Report } from '../../home/dashboard-info/report.service';
import { CurrencyFormatPipe } from '../../pipes/currency-format.pipe';

@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [CommonModule, FormsModule, CurrencyFormatPipe],
  templateUrl: './movimientos.html',
  styleUrl: './movimientos.css'
})
export class MovimientosComponent implements OnInit, OnDestroy {
  storeId: number = 0;
  store: any = null;
  products: any[] = [];
  filteredProducts: any[] = [];
  transactions: any[] = []; // Los datos originales de la API
  filteredMovimientos: any[] = []; // Los datos que se muestran
  searchTerm: string = '';
  startDate: string = ''; // Para filtro de transacciones
  endDate: string = ''; // Para filtro de transacciones
  filterDateStart: string = ''; // Para filtrador de fecha en historial
  filterDateEnd: string = ''; // Para filtrador de fecha en historial
  loading: boolean = true;
  
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

  // Reports Properties
  reports: Report[] = [];
  filteredReports: Report[] = [];
  reportLoading: boolean = false;
  showReportForm: boolean = false;
  editingReportId: number | null = null;
  title: string = '';
  description: string = '';
  reportDate: string = '';
  filterStartDate: string = '';
  filterEndDate: string = '';
  searchQuery: string = '';
  showFilters: boolean = false;
  currentPage: number = 0;
  pageSize: number = 10;
  totalPages: number = 0;

  // Form fields
  movement = {
    type: 'ENTRADA',
    productId: 0,
    quantity: 0,
    reason: 'VENTA'
  };

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
    public reportService: ReportService
  ) { }

  ngOnInit() {
    this.initializeApiUrls();
    this.checkExternalAccess();
    this.extractUserIdFromToken();
    this.tryLoadStoreData();
    this.watchStoreIdChanges();
    this.loadReports();
  }

  ngOnDestroy() {
    // Limpiar el intervalo cuando se destruye el componente
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
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
        this.products = data;
        this.filteredProducts = data;
        this.cdr.detectChanges();
        this.loadTransactions();
      },
      error: (err) => {
        console.error('Error cargando productos:', err);
        this.cdr.detectChanges();
      }
    });
  }

  loadTransactions() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    console.log('Cargando transacciones para storeId:', this.storeId);
    // Para obtener transacciones de la tienda, asumimos que hay un endpoint o filtramos por productos de la tienda
    // Por ahora, cargamos todas y filtramos después
    this.http.get<any[]>(`${this.apiTransactionsUrl}`, { headers }).subscribe({
      next: (data) => {
        console.log('Transacciones cargadas, total:', data?.length);
        // Filtrar transacciones de productos de esta tienda
        const productIds = this.products.map(p => p.id);
        console.log('Product IDs de esta tienda:', productIds);
        const filteredTransactions = data.filter(t => productIds.includes(t.productId));
        console.log('Transacciones filtradas para esta tienda:', filteredTransactions?.length);
        this.transactions = filteredTransactions.sort((a, b) => new Date(b.dateTime).getTime() - new Date(a.dateTime).getTime());
        this.applyTransactionFilters(); // Aplicar filtros después de cargar
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error cargando transacciones:', err);
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private sortTransactions() {
    this.transactions = this.transactions.sort((a, b) => new Date(b.dateTime).getTime() - new Date(a.dateTime).getTime());
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

  registerMovement() {
    if (this.movement.productId === 0 || this.movement.quantity <= 0) {
      alert('Por favor selecciona un producto y cantidad válida');
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

    // Obtener la hora local correctamente ajustada
    const now = new Date();
    const timezoneOffset = now.getTimezoneOffset() * 60 * 1000; // Convertir a milisegundos
    const localDateTime = new Date(now.getTime() - timezoneOffset).toISOString();

    const transactionData = {
      productId: this.movement.productId,
      type: this.movement.type,
      quantity: this.movement.quantity,
      dateTime: localDateTime,
      userId: this.userId
    };

    // Actualización optimista: agregar transacción inmediatamente
    const optimisticTransaction = {
      id: Date.now(),
      ...transactionData,
      dateTime: localDateTime
    };
    this.transactions.unshift(optimisticTransaction); // Agregar al inicio
    this.cdr.detectChanges();
    const originalTransactions = [...this.transactions];

    // También actualizar stock optimistamente
    const productIndex = this.products.findIndex(p => p.id === this.movement.productId);
    let originalStock = 0;
    if (productIndex !== -1) {
      originalStock = this.products[productIndex].stock;
      this.products[productIndex].stock += this.movement.type === 'ENTRADA' ? this.movement.quantity : -this.movement.quantity;
      this.cdr.detectChanges();
    }

    this.http.post(`${this.apiTransactionsUrl}`, transactionData, { headers }).subscribe({
      next: (createdTransaction: any) => {
        console.log('Transacción creada:', createdTransaction);
        // Reemplazar la transacción optimista con la real
        const index = this.transactions.findIndex(t => t.id === optimisticTransaction.id);
        if (index !== -1) {
          this.transactions[index] = createdTransaction;
          // Re-ordenar después de reemplazar
          this.sortTransactions();
        }
        // Recargar también la lista de productos para actualizar stock
        this.loadStoreProducts();
        this.movement = { type: 'ENTRADA', productId: 0, quantity: 0, reason: 'VENTA' };
        this.cdr.detectChanges();
        alert('Movimiento registrado correctamente');
      },
      error: (err) => {
        console.error('Error registrando movimiento:', err);
        // Revertir cambios optimistas
        this.transactions = originalTransactions;
        if (productIndex !== -1) {
          this.products[productIndex].stock = originalStock;
        }
        this.cdr.detectChanges();
        alert('Error al registrar el movimiento. Inténtalo de nuevo.');
      }
    });
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

  getProductName(productId: number): string {
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

  get todayTransactions(): any[] {
    return this.transactions.filter(t => {
      const date = new Date(t.dateTime);
      return date >= this.startOfToday;
    });
  }

  get todayCount(): number {
    return this.todayTransactions.length;
  }

  get entradasCount(): number {
    return this.todayTransactions.filter(t => t.type === 'ENTRADA').length;
  }

  get salidasCount(): number {
    return this.todayTransactions.filter(t => t.type === 'SALIDA').length;
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

  toggleHistoryList() {
    this.showHistoryList = !this.showHistoryList;
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
        this.reports = response.content || response;
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
    this.editingReportId = null;
  }

  submitReport() {
    if (!this.title || !this.description || !this.reportDate) {
      alert('Por favor completa todos los campos');
      return;
    }

    this.reportLoading = true;
    if (this.editingReportId) {
      this.reportService.updateReport(this.editingReportId, this.title, this.description, this.reportDate)
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
      this.reportService.createReport(this.storeId, this.title, this.description, this.reportDate)
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

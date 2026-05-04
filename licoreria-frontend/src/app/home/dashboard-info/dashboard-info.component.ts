import { Component, OnInit, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ReportService, Report } from './report.service';
import { ApiConfigService } from '../../auth/api-config.service';

@Component({
  selector: 'app-dashboard-info',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard-info.component.html',
  styleUrls: ['./dashboard-info.component.css']
})
export class DashboardInfoComponent implements OnInit {
  // ===== DASHBOARD PROPERTIES =====
  storeId = 1;
  store: any = null;
  products: any[] = [];
  loading = true;

  // Métricas básicas
  totalProducts = 0;
  totalStock = 0;
  lowStock = 0;
  totalValue = 0;
  averagePrice = 0;
  lowPercent = 0;
  mediumPercent = 0;
  highPercent = 0;

  // Costos e Ingresos
  totalInventoryCost = 0;
  totalSaleValue = 0;
  profitMargin = 0;
  profitMarginAmount = 0;
  
  // Nuevas métricas
  topProducts: any[] = [];
  productsWithZeroStock: any[] = [];
  productsWithLowStock: any[] = [];
  topSoldProducts: any[] = [];
  bottomSoldProducts: any[] = [];
  
  // Control de secciones desplegables
  expandedSections: { [key: string]: boolean } = {
    stockLevels: true,
    topSold: true,
    bottomSold: true
  };

  // ===== REPORTS PROPERTIES =====
  // Formulario
  title = '';
  description = '';
  reportDate = '';

  // Reportes
  reports: Report[] = [];
  filteredReports: Report[] = [];
  reportLoading = false;
  showReportForm = false;
  editingReportId: number | null = null;

  // Filtros
  filterStartDate = '';
  filterEndDate = '';
  searchQuery = '';
  showFilters = false;

  // Paginación
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;

  private apiStoresUrl: string = '';
  private apiProductsUrl: string = '';

  private initializeApiUrls() {
    this.apiStoresUrl = this.apiConfig.getApiUrl('/api/stores');
    this.apiProductsUrl = this.apiConfig.getApiUrl('/api/products');
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
    public reportService: ReportService,
    private apiConfig: ApiConfigService
  ) {}

  ngOnInit() {
    this.initializeApiUrls();
    this.route.params.subscribe(params => {
      this.storeId = +params['id'] || 1;
      this.loadStoreData();
      this.loadStoreProducts();
      this.loadReports();
    });
    this.setDefaultDates();
  }

  // ===== DASHBOARD METHODS =====
  loadStoreData() {
    const token = localStorage.getItem('token');
    if (!token) {
      console.error('No se encontró token en localStorage');
      return;
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`
    });

    this.http.get<any>(`${this.apiStoresUrl}/${this.storeId}`, { headers }).subscribe({
      next: data => {
        this.store = data;
        this.cdr.detectChanges();
      },
      error: err => {
        console.error('Error al cargar la tienda:', err);
      }
    });
  }

  loadStoreProducts() {
    const token = localStorage.getItem('token');
    if (!token) {
      console.error('No se encontró token en localStorage');
      this.loading = false;
      return;
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`
    });

    this.http.get<any[]>(`${this.apiProductsUrl}/store/${this.storeId}`, { headers }).subscribe({
      next: data => {
        this.products = data || [];
        this.computeMetrics();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        console.error('Error cargando productos:', err);
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  computeMetrics() {
    this.totalProducts = this.products.length;
    this.totalStock = this.products.reduce((sum, item) => sum + (item.stock || 0), 0);
    this.totalValue = this.products.reduce((sum, item) => sum + ((item.stock || 0) * (item.price || 0)), 0);
    this.totalSaleValue = this.totalValue; // Valor de venta estimado

    // Calcular coste del inventario
    this.totalInventoryCost = this.products.reduce((sum, item) => {
      const itemCost = item.cost || item.costPrice || (item.price || 0) * 0.7; // Si no tiene coste, asumimos margen de 30%
      return sum + ((item.stock || 0) * itemCost);
    }, 0);

    // Calcular margen de ganancia
    this.profitMargin = this.totalInventoryCost > 0 
      ? ((this.totalSaleValue - this.totalInventoryCost) / this.totalSaleValue) * 100 
      : 0;

    // Calcular ganancia en dinero
    this.profitMarginAmount = this.totalSaleValue - this.totalInventoryCost;

    this.averagePrice = this.products.length > 0
      ? this.products.reduce((sum, item) => sum + (item.price || 0), 0) / this.products.length
      : 0;

    this.lowStock = this.products.filter(item => (item.stock || 0) < 10).length;
    const mediumStock = this.products.filter(item => (item.stock || 0) >= 10 && (item.stock || 0) < 30).length;
    const highStock = this.products.filter(item => (item.stock || 0) >= 30).length;
    const total = this.products.length || 1;
    this.lowPercent = Math.round((this.lowStock / total) * 100);
    this.mediumPercent = Math.round((mediumStock / total) * 100);
    this.highPercent = Math.round((highStock / total) * 100);

    this.productsWithZeroStock = this.products.filter(item => (item.stock || 0) === 0);
    this.productsWithLowStock = this.products.filter(item => (item.stock || 0) > 0 && (item.stock || 0) < 10);
    this.topProducts = [...this.products]
      .sort((a, b) => (b.stock || 0) - (a.stock || 0))
      .slice(0, 5);
    this.topSoldProducts = [...this.products]
      .sort((a, b) => (a.stock || 0) - (b.stock || 0))
      .slice(0, 5);
    this.bottomSoldProducts = [...this.products]
      .sort((a, b) => (b.stock || 0) - (a.stock || 0))
      .slice(0, 5);
  }

  toggleSection(section: string) {
    this.expandedSections[section] = !this.expandedSections[section];
  }

  // ===== REPORTS METHODS =====
  loadReports(): void {
    this.reportLoading = true;
    this.ngZone.run(() => {
      this.reportService.getReportsByStore(this.storeId, this.currentPage, this.pageSize).subscribe({
        next: (response) => {
          this.reports = response.content;
          this.filteredReports = this.reports;
          this.totalPages = response.totalPages;
          this.reportLoading = false;
          this.cdr.markForCheck();
        },
        error: (error) => {
          console.error('Error al cargar reportes:', error);
          this.reportLoading = false;
          this.cdr.markForCheck();
        }
      });
    });
  }

  setDefaultDates(): void {
    const today = new Date();
    const oneMonthAgo = new Date(today.getFullYear(), today.getMonth() - 1, today.getDate());

    this.filterEndDate = today.toISOString().split('T')[0];
    this.filterStartDate = oneMonthAgo.toISOString().split('T')[0];
    this.reportDate = today.toISOString().split('T')[0];
  }

  toggleReportForm(): void {
    this.showReportForm = !this.showReportForm;
    if (!this.showReportForm) {
      this.resetReportForm();
    }
  }

  submitReport(): void {
    if (!this.title.trim() || !this.description.trim() || !this.reportDate) {
      alert('Por favor completa todos los campos requeridos');
      return;
    }

    this.reportLoading = true;

    if (this.editingReportId) {
      this.reportService.updateReport(
        this.editingReportId,
        this.title,
        this.description,
        this.reportDate
      ).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadReports();
            this.resetReportForm();
            this.showReportForm = false;
            this.reportLoading = false;
            alert('Reporte actualizado exitosamente');
            this.cdr.markForCheck();
          });
        },
        error: (error) => {
          console.error('Error al actualizar reporte:', error);
          this.reportLoading = false;
          this.cdr.markForCheck();
        }
      });
    } else {
      this.reportService.createReport(
        this.storeId,
        this.title,
        this.description,
        this.reportDate
      ).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadReports();
            this.resetReportForm();
            this.showReportForm = false;
            this.reportLoading = false;
            alert('Reporte creado exitosamente');
            this.cdr.markForCheck();
          });
        },
        error: (error) => {
          console.error('Error al crear reporte:', error);
          this.reportLoading = false;
          this.cdr.markForCheck();
        }
      });
    }
  }

  applyDateFilter(): void {
    this.filterReports();
  }

  filterReports(): void {
    let filtered = [...this.reports];

    // Filtrar por búsqueda (título y descripción)
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(report =>
        report.title.toLowerCase().includes(query) ||
        report.description.toLowerCase().includes(query)
      );
    }

    // Filtrar por rango de fechas
    if (this.filterStartDate && this.filterEndDate) {
      filtered = filtered.filter(report => {
        const reportDate = new Date(report.reportDate);
        const startDate = new Date(this.filterStartDate);
        const endDate = new Date(this.filterEndDate);
        return reportDate >= startDate && reportDate <= endDate;
      });
    }

    this.filteredReports = filtered;
  }

  onSearchChange(): void {
    this.filterReports();
  }

  toggleFilters(): void {
    this.showFilters = !this.showFilters;
  }

  resetFilters(): void {
    this.filterStartDate = '';
    this.filterEndDate = '';
    this.searchQuery = '';
    this.filteredReports = this.reports;
  }

  editReport(report: Report): void {
    this.editingReportId = report.id;
    this.title = report.title;
    this.description = report.description;
    this.reportDate = report.reportDate;
    this.showReportForm = true;
  }

  deleteReport(reportId: number): void {
    if (!confirm('¿Estás seguro de que deseas eliminar este reporte?')) return;

    this.reportService.deleteReport(reportId).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.loadReports();
          alert('Reporte eliminado exitosamente');
          this.cdr.markForCheck();
        });
      },
      error: (error) => {
        console.error('Error al eliminar reporte:', error);
        this.cdr.markForCheck();
      }
    });
  }

  previousPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadReports();
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.loadReports();
    }
  }

  private resetReportForm(): void {
    this.title = '';
    this.description = '';
    this.reportDate = '';
    this.editingReportId = null;
  }
}

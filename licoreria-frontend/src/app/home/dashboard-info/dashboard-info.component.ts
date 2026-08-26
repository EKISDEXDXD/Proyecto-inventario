import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiConfigService } from '../../auth/api-config.service';

interface ProductRecord {
  id: number; name: string; parentId?: number | null; price?: number; cost?: number; stock?: number; isActive?: boolean;
  tags?: Array<{ tag?: { id: number; name: string } } | { id: number; name: string }>;
  alert?: { threshold?: number; isEnabled?: boolean };
}
interface TransactionRecord {
  id: number; type: string; reason: string; quantity: number; dateTime: string; product?: ProductRecord;
  paymentMethod?: { paymentMethodConfig?: { id: number; name: string } };
}
interface PaymentMethodConfigRecord { id: number; name: string; type?: string; isActive?: boolean; }
interface ProductSummary { root: ProductRecord; lots: ProductRecord[]; units: number; revenue: number; cost: number; profit: number; expanded: boolean; barWidth: number; }
interface CategoryBarSegment { productId: number; name: string; units: number; width: number; color: string; }
interface CategoryProfitability { name: string; revenue: number; cost: number; profit: number; margin: number; units: number; active: boolean; }
interface DailyFlow { date: string; label: string; entries: number; entryCost: number; sales: number; salesRevenue: number; salesCost: number; grossProfit: number; width: number; }
interface FilterChip { group: string; label: string; onRemove: () => void; }
interface StockRiskItem { id: number; name: string; stock: number; threshold: number; severity: 'critical' | 'warning'; }
interface DashboardSummaryDay { date: string; entries: number; entryCost: number; salesUnits: number; salesCount: number; salesRevenue: number; salesCost: number; grossProfit: number; losses: number; movements: number; adminCost: number; products: Array<{ productId?: number; name: string; units: number; revenue: number; cost: number; profit: number }>; categories: Array<{ name: string; units: number; revenue: number; cost: number; profit: number }>; payments: Record<string, number>; }
interface DashboardSummary { ready: boolean; generatedAt?: string; days: DashboardSummaryDay[]; }

@Component({
  selector: 'app-dashboard-info',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard-info.component.html',
  styleUrls: ['./dashboard-info.component.css'],
  // Con muchos datos, la estrategia por defecto recalculaba todos los getters en cada ciclo global de
  // detección de cambios (scroll, timers, etc). OnPush limita el recálculo a eventos propios del componente.
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardInfoComponent implements OnInit {
  @ViewChild('dailyFlowList') dailyFlowList?: ElementRef<HTMLElement>;
  storeId = 0; storeName = 'Tienda'; loading = true; errorMessage = ''; currency = 'Bs'; filtersOpen = false;
  transactions: TransactionRecord[] = []; products: ProductRecord[] = []; paymentMethodConfigs: PaymentMethodConfigRecord[] = []; adminMovements: Array<{ dateTime: string; amountPaid: number; description?: string; concept?: string; type?: string }> = [];
  dashboardSummary: DashboardSummary | null = null;
  expandedProductIds = new Set<number>();
  selectedTags = new Set<string>();
  selectedDates = new Set<string>();
  selectedProductIds = new Set<number>();
  selectedPayments = new Set<string>();
  executivePeriod: 'ALL' | 'TODAY' | '7D' | '30D' | 'CUSTOM' = 'CUSTOM';
  executiveLoaded = false;
  executiveLoading = false;
  executiveTransactions: TransactionRecord[] = [];
  executiveAdminMovements: Array<{ dateTime: string; amountPaid: number; description?: string; concept?: string; type?: string }> = [];
  private executiveDateFrom = '';
  private executiveDateTo = '';
  executiveFromInput = '';
  executiveToInput = '';
  hasLoadedPeriod = false;
  stockCriticalOpen = false;
  stockCriticalLoaded = false;
  lowStockOpen = false;
  lowStockLoaded = false;
  profitabilityOpen = false;
  profitabilityLoaded = false;
  dailyFlowVisibleDays = 14;

  // Con datasets grandes en producción, recalcular todo en cada acceso a un getter es muy costoso.
  // _cache memoriza el resultado de cada getter pesado hasta que bump() incrementa _version.
  private _version = 0;
  private _cache = new Map<string, { version: number; value: any }>();
  private _typeFilter: 'ALL' | 'ENTRADA' | 'SALIDA' = 'SALIDA';
  private _reasonFilter = 'ALL';
  private _sortBy: 'units' | 'revenue' | 'profit' = 'units';
  private _productSearchTerm = '';
  private _dateFrom = '';
  private _dateTo = '';

  get typeFilter(): 'ALL' | 'ENTRADA' | 'SALIDA' { return this._typeFilter; }
  set typeFilter(value: 'ALL' | 'ENTRADA' | 'SALIDA') { this._typeFilter = value; this.bump(); }
  get reasonFilter(): string { return this._reasonFilter; }
  set reasonFilter(value: string) { this._reasonFilter = value; this.bump(); }
  get sortBy(): 'units' | 'revenue' | 'profit' { return this._sortBy; }
  set sortBy(value: 'units' | 'revenue' | 'profit') { this._sortBy = value; this.bump(); }
  get productSearchTerm(): string { return this._productSearchTerm; }
  set productSearchTerm(value: string) { this._productSearchTerm = value; this.bump(); }
  searchInputValue = '';
  private searchDebounceHandle: ReturnType<typeof setTimeout> | undefined;

  onSearchInput(value: string): void {
    this.searchInputValue = value;
    clearTimeout(this.searchDebounceHandle);
    this.searchDebounceHandle = setTimeout(() => {
      this.productSearchTerm = value;
      this.cdr.markForCheck();
    }, 250);
  }
  get dateFrom(): string { return this._dateFrom; }
  set dateFrom(value: string) {
    this._dateFrom = value;
    this.bump();
  }
  get dateTo(): string { return this._dateTo; }
  set dateTo(value: string) { this._dateTo = value; this.bump(); }

  loadedFrom = '';
  fullHistoryLoaded = false;
  private loadedTransactionFrom = '';
  private loadedTransactionAll = false;
  private adminDataLoaded = false;
  private productsDataLoaded = false;
  private transactionCache = new Map<string, TransactionRecord[]>();

  private bump(): void { this._version++; }
  private restoreScrollPosition(scrollY: number): void {
    requestAnimationFrame(() => requestAnimationFrame(() => {
      window.scrollTo({ top: scrollY, behavior: 'smooth' });
    }));
  }
  private memo<T>(key: string, compute: () => T): T {
    const cached = this._cache.get(key);
    if (cached && cached.version === this._version) return cached.value;
    const value = compute();
    this._cache.set(key, { version: this._version, value });
    return value;
  }

  constructor(private route: ActivatedRoute, private router: Router, private http: HttpClient, private apiConfig: ApiConfigService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    const storeRoute = this.route.parent ?? this.route;
    storeRoute.paramMap.subscribe(params => {
      this.storeId = Number(params.get('id') ?? 0);
      this.storeName = `Tienda #${this.storeId}`;
      this.hasLoadedPeriod = false;
      this.executiveLoaded = false;
      this.dateFrom = '';
      this.dateTo = '';
      this.executiveFromInput = '';
      this.executiveToInput = '';
      this.loading = false;
      this.transactions = [];
      this.products = [];
      this.adminMovements = [];
      this.dashboardSummary = null;
      this.paymentMethodConfigs = [];
      this.executiveTransactions = [];
      this.executiveAdminMovements = [];
      this.executiveLoaded = false;
      this.loadedTransactionFrom = '';
      this.loadedTransactionAll = false;
      this.adminDataLoaded = false;
      this.productsDataLoaded = false;
      this.transactionCache.clear();
      this.stockCriticalOpen = false;
      this.stockCriticalLoaded = false;
      this.lowStockOpen = false;
      this.lowStockLoaded = false;
      this.profitabilityOpen = false;
      this.profitabilityLoaded = false;
      this.dailyFlowVisibleDays = 14;
      this.loadStoreInfo();
      this.bump();
      this.cdr.markForCheck();
    });
  }

  get apiBase(): string { return this.apiConfig.getApiUrl(''); }

  goBack(): void { this.router.navigate(['../'], { relativeTo: this.route }); }

  private loadStoreInfo(): void {
    const token = localStorage.getItem('token');
    if (!token || !this.storeId) return;
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
    const currentStoreId = this.storeId;
    this.http.get<any>(`${this.apiBase}/api/stores/${currentStoreId}`, { headers })
      .pipe(catchError(() => of(null)))
      .subscribe(store => {
        if (currentStoreId !== this.storeId) return;
        this.storeName = store?.name ?? `Tienda #${currentStoreId}`;
        this.cdr.markForCheck();
      });
  }

  loadData(fromDate: string | null = null): void {
    const token = localStorage.getItem('token');
    if (!token || !this.storeId) { this.errorMessage = 'No se pudo identificar la tienda.'; this.loading = false; return; }
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
    this.loading = true;
    const scrollY = window.scrollY;
    this.stockCriticalOpen = false;
    this.stockCriticalLoaded = false;
    this.lowStockOpen = false;
    this.lowStockLoaded = false;
    this.profitabilityOpen = false;
    this.profitabilityLoaded = false;
    this.dailyFlowVisibleDays = 14;
    this.errorMessage = '';
    this.fullHistoryLoaded = fromDate === null;
    this.loadedFrom = fromDate ?? '';
    const transactionCacheKey = fromDate ?? 'ALL';
    forkJoin({
      products: this.productsDataLoaded ? of(this.products) : this.http.get<ProductRecord[]>(`${this.apiBase}/api/products/store/${this.storeId}`, { headers }).pipe(catchError(() => of([]))),
      summary: this.http.get<DashboardSummary>(`${this.apiBase}/api/transactions/store/${this.storeId}/dashboard-summary`, { headers }).pipe(catchError(() => of({ ready: false, days: [] })))
    }).subscribe(({ products, summary }) => {
      this.products = products ?? [];
      this.productsDataLoaded = true;
      this.dashboardSummary = summary.ready ? summary : null;
      this.transactions = [];
      this.hasLoadedPeriod = true;
      this.loading = false;
      this.bump();
      this.cdr.detectChanges();
      this.restoreScrollPosition(scrollY);
      if (!summary.ready) this.loadDetailedTransactions(fromDate, headers, transactionCacheKey);
    });

    // Datos secundarios no deben bloquear el primer render del dashboard.
    this.http.get<any[]>(`${this.apiBase}/api/administrative-cost-movements/store/${this.storeId}`, { headers })
      .pipe(catchError(() => of([])))
      .subscribe(movements => {
        this.adminMovements = movements ?? [];
        this.adminDataLoaded = true;
        this.bump();
        this.cdr.markForCheck();
      });

    this.http.get<PaymentMethodConfigRecord[]>(`${this.apiBase}/api/payment-method-configs/active`, { headers })
      .pipe(catchError(() => of([])))
      .subscribe(paymentMethods => {
        this.paymentMethodConfigs = paymentMethods ?? [];
        this.bump();
        this.cdr.markForCheck();
      });
  }

  private loadDetailedTransactions(fromDate: string | null, headers: HttpHeaders, cacheKey: string): void {
    if (this.transactionCache.has(cacheKey)) {
      this.transactions = this.transactionCache.get(cacheKey)!;
      this.bump();
      this.cdr.markForCheck();
      return;
    }
    const url = fromDate
      ? `${this.apiBase}/api/transactions/store/${this.storeId}?desde=${fromDate}`
      : `${this.apiBase}/api/transactions/store/${this.storeId}`;
    this.http.get<TransactionRecord[]>(url, { headers }).pipe(catchError(() => of([]))).subscribe(transactions => {
      this.transactions = transactions.filter(item => item.product?.isActive !== false);
      this.transactionCache.set(cacheKey, this.transactions);
      this.loadedTransactionFrom = fromDate ?? '';
      this.loadedTransactionAll = fromDate === null;
      this.bump();
      this.cdr.markForCheck();
    });
  }

  get reasons(): string[] { return this.memo('reasons', () => [...new Set(this.transactions.map(item => item.reason).filter(Boolean))].sort()); }
  get tags(): Array<{ id: number; name: string }> { return this.memo('tags', () => { const result = new Map<number, string>(); this.products.forEach(product => this.productTags(product).forEach(tag => result.set(tag.id, tag.name))); return [...result.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name)); }); }
  get payments(): string[] { return this.memo('payments', () => [...new Set([...this.paymentMethodConfigs.filter(item => item.isActive !== false).map(item => item.name), ...this.transactions.map(item => item.paymentMethod?.paymentMethodConfig?.name).filter(Boolean) as string[]])].sort()); }
  get filteredTransactions(): TransactionRecord[] { return this.memo('filteredTransactions', () => this.matchingTransactions()); }
  private isAnalyticTransaction(item: TransactionRecord): boolean {
    if (this.typeFilter === 'ENTRADA') return item.type === 'ENTRADA' && item.reason === 'COMPRA';
    return item.type === 'SALIDA' && item.reason === 'VENTA';
  }
  private matchingTransactions(exclude: Array<'tags' | 'products' | 'dates' | 'payments' | 'type'> = []): TransactionRecord[] {
    return this.transactions.filter(transaction => {
      const date = transaction.dateTime?.slice(0, 10) ?? '';
      const tags = transaction.product ? this.productTags(transaction.product).map(tag => tag.name) : [];
      const rootId = this.rootId(transaction.product);
      const payment = transaction.paymentMethod?.paymentMethodConfig?.name;
      return (exclude.includes('type') || this.typeFilter === 'ALL' || transaction.type === this.typeFilter)
        && (this.reasonFilter === 'ALL' || transaction.reason === this.reasonFilter)
        && (exclude.includes('tags') || !this.selectedTags.size || tags.some(tag => this.selectedTags.has(tag)))
        && (exclude.includes('dates') || !this.selectedDates.size || this.selectedDates.has(date))
        && (exclude.includes('products') || !this.selectedProductIds.size || this.selectedProductIds.has(rootId))
        && (exclude.includes('payments') || !this.selectedPayments.size || (payment ? this.selectedPayments.has(payment) : false))
        && (!this.dateFrom || date >= this.dateFrom)
        && (!this.dateTo || date <= this.dateTo);
    });
  }
  get sales(): TransactionRecord[] { return this.memo('sales', () => this.filteredTransactions.filter(item => item.type === 'SALIDA' && item.reason === 'VENTA')); }
  private summaryDaysForPeriod(): DashboardSummaryDay[] {
    const days = this.dashboardSummary?.days ?? [];
    return days.filter(day => (!this.dateFrom || day.date >= this.dateFrom) && (!this.dateTo || day.date <= this.dateTo));
  }
  private hasDetailedTransactions(): boolean { return this.transactions.length > 0; }
  get totalRevenue(): number { return this.hasDetailedTransactions() ? this.sales.reduce((sum, item) => sum + this.amount(item, 'price'), 0) : this.summaryDaysForPeriod().reduce((sum, day) => sum + Number(day.salesRevenue), 0); }
  get totalCost(): number { return this.hasDetailedTransactions() ? this.sales.reduce((sum, item) => sum + this.amount(item, 'cost'), 0) : this.summaryDaysForPeriod().reduce((sum, day) => sum + Number(day.salesCost), 0); }
  get grossProfit(): number { return this.totalRevenue - this.totalCost; }
  get salesCount(): number { return this.hasDetailedTransactions() ? this.sales.length : this.summaryDaysForPeriod().reduce((sum, day) => sum + Number(day.salesCount), 0); }
  get margin(): number { return this.totalRevenue ? this.grossProfit / this.totalRevenue * 100 : 0; }
  get adminCost(): number { return this.hasDetailedTransactions() ? this.adminMovements.filter(item => (!this.dateFrom || item.dateTime?.slice(0, 10) >= this.dateFrom) && (!this.dateTo || item.dateTime?.slice(0, 10) <= this.dateTo)).reduce((sum, item) => sum + Number(item.amountPaid ?? 0), 0) : this.summaryDaysForPeriod().reduce((sum, day) => sum + Number(day.adminCost), 0); }
  get netProfit(): number { return this.grossProfit - this.adminCost; }
  get losses(): number { return this.hasDetailedTransactions() ? this.filteredTransactions.filter(item => item.type === 'SALIDA' && item.reason === 'PERDIDA').reduce((sum, item) => sum + item.quantity, 0) : this.summaryDaysForPeriod().reduce((sum, day) => sum + Number(day.losses), 0); }
  get rootProducts(): ProductRecord[] { return this.products.filter(item => !item.parentId); }
  get lowStockCount(): number { return this.rootProducts.filter(item => this.stockFor(item) <= this.threshold(item)).length; }
  get averageTicket(): number { return this.sales.length ? this.totalRevenue / this.sales.length : 0; }
  get executiveEntries(): number { return this.filteredTransactions.filter(item => item.type === 'ENTRADA').reduce((sum, item) => sum + item.quantity, 0); }
  get totalInvested(): number { return this.matchingTransactions(['type']).filter(item => item.type === 'ENTRADA' && item.reason !== 'AJUSTE').reduce((sum, item) => sum + this.amount(item, 'cost'), 0); }
  get executiveSalesUnits(): number { return this.sales.reduce((sum, item) => sum + item.quantity, 0); }
  get executiveLosses(): number { return this.losses; }
  get executiveMovements(): number { return this.filteredTransactions.length; }
  get executiveSummaryPeriod(): string {
    if (!this.executiveLoaded) return 'Sin periodo seleccionado';
    if (this.fullHistoryLoaded && !this.executiveDateFrom) return 'Todo el periodo disponible';
    return `${this.executiveDateFrom || 'Inicio'} - ${this.executiveDateTo || 'Hoy'}`;
  }
  get executiveSales(): TransactionRecord[] { return this.executiveTransactions.filter(item => item.type === 'SALIDA' && item.reason === 'VENTA'); }
  get executiveTotalRevenue(): number { return this.executiveSales.reduce((sum, item) => sum + this.amount(item, 'price'), 0); }
  get executiveTotalCost(): number { return this.executiveSales.reduce((sum, item) => sum + this.amount(item, 'cost'), 0); }
  get executiveGrossProfit(): number { return this.executiveTotalRevenue - this.executiveTotalCost; }
  get executiveMargin(): number { return this.executiveTotalRevenue ? this.executiveGrossProfit / this.executiveTotalRevenue * 100 : 0; }
  get executiveAdminCost(): number { return this.executiveAdminMovements.reduce((sum, item) => sum + Number(item.amountPaid ?? 0), 0); }
  get executiveNetProfit(): number { return this.executiveGrossProfit - this.executiveAdminCost; }
  get executiveInvested(): number { return this.executiveTransactions.filter(item => item.type === 'ENTRADA' && item.reason !== 'AJUSTE').reduce((sum, item) => sum + this.amount(item, 'cost'), 0); }
  get executiveUnits(): number { return this.executiveSales.reduce((sum, item) => sum + item.quantity, 0); }
  private get allDailyFlow(): DailyFlow[] {
    return this.memo('dailyFlow', () => {
      const map = new Map<string, DailyFlow>();
      this.executiveTransactions.filter(item => item.reason !== 'AJUSTE').forEach(item => {
        const date = item.dateTime?.slice(0, 10) ?? '';
        if (!date) return;
        const current = map.get(date) ?? { date, label: new Date(`${date}T12:00:00`).toLocaleDateString('es', { weekday: 'short', day: '2-digit', month: 'short' }), entries: 0, entryCost: 0, sales: 0, salesRevenue: 0, salesCost: 0, grossProfit: 0, width: 0 };
        if (item.type === 'ENTRADA') { current.entries += item.quantity; current.entryCost += this.amount(item, 'cost'); }
        if (item.type === 'SALIDA' && item.reason === 'VENTA') { current.sales += item.quantity; current.salesRevenue += this.amount(item, 'price'); current.salesCost += this.amount(item, 'cost'); current.grossProfit += this.amount(item, 'price') - this.amount(item, 'cost'); }
        map.set(date, current);
      });
      const days = [...map.values()].sort((first, second) => first.date.localeCompare(second.date));
      const max = Math.max(...days.map(item => Math.max(item.entries, item.sales)), 1);
      return days.map(item => ({ ...item, width: item.sales / max * 100 }));
    });
  }
  get dailyFlow(): DailyFlow[] { return this.allDailyFlow.slice(-this.dailyFlowVisibleDays); }
  get dailyFlowHasMore(): boolean { return this.dailyFlowVisibleDays < this.allDailyFlow.length; }
  onDailyFlowScroll(event: Event): void {
    const element = event.target as HTMLElement;
    if (element.scrollTop > 8 || !this.dailyFlowHasMore) return;
    const previousHeight = element.scrollHeight;
    this.dailyFlowVisibleDays = Math.min(this.dailyFlowVisibleDays + 14, this.allDailyFlow.length);
    this.bump();
    this.cdr.markForCheck();
    requestAnimationFrame(() => { element.scrollTop += element.scrollHeight - previousHeight; });
  }
  private scrollDailyFlowToEnd(): void {
    requestAnimationFrame(() => requestAnimationFrame(() => {
      if (this.dailyFlowList) this.dailyFlowList.nativeElement.scrollTop = this.dailyFlowList.nativeElement.scrollHeight;
    }));
  }
  get administrativeCostRows(): Array<{ date: string; label: string; amount: number; description: string }> {
    return this.memo('administrativeCostRows', () => this.executiveAdminMovements.map(item => ({ date: item.dateTime?.slice(0, 10) ?? '', label: item.dateTime ? new Date(item.dateTime).toLocaleDateString('es-BO', { day: '2-digit', month: 'short', year: 'numeric' }) : 'Sin fecha', amount: Number(item.amountPaid ?? 0), description: item.description || item.concept || item.type || 'Costo administrativo' })).sort((first, second) => second.date.localeCompare(first.date)));
  }
  get administrativeCostTotal(): number { return this.administrativeCostRows.reduce((sum, item) => sum + item.amount, 0); }
  get administrativeCostAverage(): number { return this.administrativeCostRows.length ? this.administrativeCostTotal / this.administrativeCostRows.length : 0; }
  get dailyFlowMax(): number { return Math.max(...this.dailyFlow.map(item => Math.max(item.entries, item.sales)), 1); }
  setDashboardPeriod(period: 'ALL' | 'TODAY' | '7D' | '30D'): void {
    this.executivePeriod = 'CUSTOM';
    if (period === 'ALL') { this.dateFrom = ''; this.dateTo = ''; this.loadData(null); return; }
    const today = new Date();
    const from = new Date(today);
    if (period === '7D') from.setDate(today.getDate() - 6);
    if (period === '30D') from.setDate(today.getDate() - 29);
    const toIso = (date: Date): string => { const year = date.getFullYear(); const month = String(date.getMonth() + 1).padStart(2, '0'); const day = String(date.getDate()).padStart(2, '0'); return `${year}-${month}-${day}`; };
    this.dateFrom = toIso(period === 'TODAY' ? today : from);
    this.dateTo = toIso(today);
    this.loadData(this.dateFrom);
  }
  applyDashboardPeriod(): void {
    if (!this.dateFrom || !this.dateTo || this.dateFrom > this.dateTo) return;
    this.loadData(this.dateFrom);
  }
  toggleStockPanel(panel: 'critical' | 'low'): void {
    if (panel === 'critical') {
      this.stockCriticalOpen = !this.stockCriticalOpen;
      if (this.stockCriticalOpen) this.stockCriticalLoaded = true;
    } else {
      this.lowStockOpen = !this.lowStockOpen;
      if (this.lowStockOpen) this.lowStockLoaded = true;
    }
    this.bump();
    this.cdr.markForCheck();
  }
  toggleProfitability(): void {
    this.profitabilityOpen = !this.profitabilityOpen;
    if (this.profitabilityOpen) this.profitabilityLoaded = true;
    this.bump();
    this.cdr.markForCheck();
  }
  setExecutivePeriod(period: 'ALL' | 'TODAY' | '7D' | '30D' | 'CUSTOM'): void {
    this.executivePeriod = period;
    if (period === 'CUSTOM') return;
    if (period === 'ALL') {
      this.loadExecutiveData(null, '');
      return;
    }
    const today = new Date();
    const from = new Date(today);
    if (period === '7D') from.setDate(today.getDate() - 6);
    if (period === '30D') from.setDate(today.getDate() - 29);
    const toIso = (date: Date): string => { const year = date.getFullYear(); const month = String(date.getMonth() + 1).padStart(2, '0'); const day = String(date.getDate()).padStart(2, '0'); return `${year}-${month}-${day}`; };
    this.executiveFromInput = toIso(period === 'TODAY' ? today : from);
    this.executiveToInput = toIso(today);
    this.loadExecutiveData(this.executiveFromInput, this.executiveToInput);
  }
  applyCustomPeriod(): void {
    if (!this.executiveFromInput || !this.executiveToInput || this.executiveFromInput > this.executiveToInput) return;
    this.executivePeriod = 'CUSTOM';
    this.loadExecutiveData(this.executiveFromInput, this.executiveToInput);
  }
  loadExecutiveData(fromDate: string | null, toDate: string): void {
    const token = localStorage.getItem('token');
    if (!token || !this.storeId) return;
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
    this.executiveLoading = true;
    this.executiveLoaded = false;
    const scrollY = window.scrollY;
    this.executiveDateFrom = fromDate ?? '';
    this.executiveDateTo = toDate;
    const canReuseTransactions = this.hasLoadedPeriod && (this.loadedTransactionAll || (!!this.loadedTransactionFrom && !!fromDate && fromDate >= this.loadedTransactionFrom));
    const transactionUrl = fromDate ? `${this.apiBase}/api/transactions/store/${this.storeId}?desde=${fromDate}` : `${this.apiBase}/api/transactions/store/${this.storeId}`;
    const transactionCacheKey = fromDate ?? 'ALL';
    const hasCachedTransactions = this.transactionCache.has(transactionCacheKey);
    forkJoin({
      transactions: canReuseTransactions ? of(this.transactions) : hasCachedTransactions ? of(this.transactionCache.get(transactionCacheKey)!) : this.http.get<TransactionRecord[]>(transactionUrl, { headers }).pipe(catchError(() => of(null))),
      movements: this.adminDataLoaded ? of(this.adminMovements) : this.http.get<any[]>(`${this.apiBase}/api/administrative-cost-movements/store/${this.storeId}`, { headers }).pipe(catchError(() => of([])))
    }).subscribe(({ transactions, movements }) => {
      this.executiveTransactions = transactions ? transactions.filter(item => item.product?.isActive !== false && (!toDate || item.dateTime.slice(0, 10) <= toDate)) : [];
      if (transactions !== null) this.transactionCache.set(transactionCacheKey, transactions.filter(item => item.product?.isActive !== false));
      this.executiveAdminMovements = (movements ?? []).filter(item => !fromDate || item.dateTime?.slice(0, 10) >= fromDate).filter(item => !toDate || item.dateTime?.slice(0, 10) <= toDate);
      this.executiveLoaded = transactions !== null;
      this.executiveLoading = false;
      this.fullHistoryLoaded = fromDate === null;
      this.bump();
      this.cdr.markForCheck();
      this.restoreScrollPosition(scrollY);
      this.scrollDailyFlowToEnd();
    });
  }

  get productSummaries(): ProductSummary[] {
    return this.memo('productSummaries', () => {
      const key = this.sortBy;
      const search = this.productSearchTerm.trim().toLocaleLowerCase();
      if (!this.hasDetailedTransactions()) {
        const totals = new Map<number, { units: number; revenue: number; cost: number; profit: number }>();
        this.summaryDaysForPeriod().forEach(day => day.products.forEach(item => {
          if (item.productId == null) return;
          const product = this.products.find(candidate => candidate.id === item.productId);
          const rootId = product?.parentId ?? item.productId;
          const current = totals.get(rootId) ?? { units: 0, revenue: 0, cost: 0, profit: 0 };
          current.units += Number(item.units); current.revenue += Number(item.revenue); current.cost += Number(item.cost); current.profit += Number(item.profit);
          totals.set(rootId, current);
        }));
        return this.rootProducts.map(root => {
          const lots = this.products.filter(product => product.parentId === root.id);
          const value = totals.get(root.id) ?? { units: 0, revenue: 0, cost: 0, profit: 0 };
          return { root, lots, expanded: this.expandedProductIds.has(root.id), ...value, barWidth: 0 };
        }).filter(item => item.units > 0 && (!search || item.root.name.toLocaleLowerCase().includes(search) || item.lots.some(lot => lot.name.toLocaleLowerCase().includes(search)))).sort((a, b) => b[key] - a[key]);
      }
      const lotsByRoot = new Map<number, ProductRecord[]>();
      this.products.filter(product => product.parentId).forEach(lot => {
        const lots = lotsByRoot.get(lot.parentId!) ?? [];
        lots.push(lot);
        lotsByRoot.set(lot.parentId!, lots);
      });
      const totalsByRoot = new Map<number, { units: number; revenue: number; cost: number; profit: number }>();
      this.matchingTransactions(['products']).filter(item => this.isAnalyticTransaction(item)).forEach(item => {
        const productId = item.product?.id;
        if (!productId) return;
        const rootId = this.rootId(item.product);
        const current = totalsByRoot.get(rootId) ?? { units: 0, revenue: 0, cost: 0, profit: 0 };
        const revenue = this.amount(item, 'price');
        const cost = this.amount(item, 'cost');
        current.units += item.quantity;
        current.revenue += revenue;
        current.cost += cost;
        current.profit += revenue - cost;
        totalsByRoot.set(rootId, current);
      });
      const raw = this.rootProducts.map(root => {
        const lots = lotsByRoot.get(root.id) ?? [];
        const totals = totalsByRoot.get(root.id) ?? { units: 0, revenue: 0, cost: 0, profit: 0 };
        return { root, lots, expanded: this.expandedProductIds.has(root.id), ...totals, barWidth: 0 };
      }).filter(item => item.units > 0 && (!search || item.root.name.toLocaleLowerCase().includes(search) || item.lots.some(lot => lot.name.toLocaleLowerCase().includes(search))));
      const max = Math.max(...raw.map(item => item[key]), 1);
      return raw.map(item => ({ ...item, barWidth: Math.max(item[key] / max * 100, 3) })).sort((a, b) => b[key] - a[key]);
    });
  }
  get categoryBreakdown(): Array<{ name: string; units: number; width: number; scale: number; active: boolean; selectedSegments: CategoryBarSegment[]; selectedUnits: number; remainder: number; remainderWidth: number }> {
    return this.memo('categoryBreakdown', () => {
      if (!this.hasDetailedTransactions()) {
        const totals = new Map<string, number>();
        this.summaryDaysForPeriod().forEach(day => day.categories.forEach(item => totals.set(item.name, (totals.get(item.name) ?? 0) + Number(item.units))));
        const max = Math.max(...totals.values(), 1);
        return [...totals.entries()].map(([name, units]) => ({ name, units, scale: Math.max(50, Math.ceil(units / 50) * 50), width: units / max * 100, active: this.selectedTags.has(name), selectedSegments: [], selectedUnits: 0, remainder: units, remainderWidth: 100 })).sort((a, b) => b.units - a.units);
      }
      const map = new Map<string, number>();
      const selectedMap = new Map<string, Map<number, { name: string; units: number }>>();
      this.matchingTransactions(['tags', 'products']).filter(item => this.isAnalyticTransaction(item)).forEach(item => {
        const tags = item.product ? this.productTags(item.product) : [];
        const names = tags.length ? tags.map(tag => tag.name) : ['Sin etiqueta'];
        names.forEach(name => {
          map.set(name, (map.get(name) ?? 0) + item.quantity);
          const rootId = this.rootId(item.product);
          if (this.selectedProductIds.has(rootId)) {
            const products = selectedMap.get(name) ?? new Map<number, { name: string; units: number }>();
            const current = products.get(rootId) ?? { name: item.product?.name ?? `#${rootId}`, units: 0 };
            current.units += item.quantity;
            products.set(rootId, current);
            selectedMap.set(name, products);
          }
        });
      });
      const maxUnits = Math.max(...map.values(), 1);
      const barScale = Math.floor(maxUnits / 50) * 50 + 50;
      return [...map.entries()].map(([name, units]) => {
        const selected = [...(selectedMap.get(name)?.entries() ?? [])].sort(([, first], [, second]) => second.units - first.units);
        const scale = Math.max(50, Math.ceil(units / 50) * 50);
        const selectedSegments = selected.map(([productId, value], index) => ({ productId, name: value.name, units: value.units, width: value.units / scale * 100, color: this.productColor(index) }));
        const selectedUnits = selectedSegments.reduce((sum, item) => sum + item.units, 0);
        return { name, units, scale, width: units / barScale * 100, active: this.selectedTags.has(name), selectedSegments, selectedUnits, remainder: Math.max(units - selectedUnits, 0), remainderWidth: Math.max(units - selectedUnits, 0) / scale * 100 };
      }).sort((a, b) => b.units - a.units);
    });
  }
  get categoryProfitability(): CategoryProfitability[] {
    return this.memo('categoryProfitability', () => {
      if (!this.hasDetailedTransactions()) {
        const totals = new Map<string, { revenue: number; cost: number; units: number }>();
        this.summaryDaysForPeriod().forEach(day => day.categories.forEach(item => { const current = totals.get(item.name) ?? { revenue: 0, cost: 0, units: 0 }; current.revenue += Number(item.revenue); current.cost += Number(item.cost); current.units += Number(item.units); totals.set(item.name, current); }));
        return [...totals.entries()].map(([name, value]) => { const profit = value.revenue - value.cost; return { name, ...value, profit, margin: value.revenue ? profit / value.revenue * 100 : 0, active: this.selectedTags.has(name) }; }).sort((a, b) => b.units - a.units);
      }
      const totals = new Map<string, { revenue: number; cost: number; units: number }>();
      this.matchingTransactions(['tags']).filter(item => this.isAnalyticTransaction(item)).forEach(item => {
        const tags = item.product ? this.productTags(item.product) : [];
        const names = tags.length ? tags.map(tag => tag.name) : ['Sin etiqueta'];
        names.forEach(name => {
          const current = totals.get(name) ?? { revenue: 0, cost: 0, units: 0 };
          current.revenue += this.amount(item, 'price');
          current.cost += this.amount(item, 'cost');
          current.units += item.quantity;
          totals.set(name, current);
        });
      });
      return [...totals.entries()].map(([name, value]) => {
        const profit = value.revenue - value.cost;
        return { name, ...value, profit, margin: value.revenue ? profit / value.revenue * 100 : 0, active: this.selectedTags.has(name) };
      }).sort((a, b) => b.units - a.units);
    });
  }
  get totalCategoryProfit(): number { return this.categoryProfitability.reduce((sum, item) => sum + item.profit, 0); }
  get maxCategoryMargin(): number { return Math.max(...this.categoryProfitability.map(item => item.margin), 1); }
  get maxCategoryProfit(): number { return Math.max(...this.categoryProfitability.map(item => item.profit), 1); }
  get categoryMarginPoints(): string { return this.categoryProfitability.map((item, index) => `${this.categoryPointX(index)},${this.categoryPointY(item.margin, this.maxCategoryMargin)}`).join(' '); }
  get categoryTotalProfitPoints(): string { return this.categoryProfitability.map((item, index) => `${this.categoryPointX(index)},${this.categoryPointY(item.profit, this.maxCategoryProfit)}`).join(' '); }
  categoryPointX(index: number): number { return this.categoryProfitability.length > 1 ? index * 900 / (this.categoryProfitability.length - 1) : 450; }
  categoryPointY(value: number, max: number): number { return 230 - Math.max(value, 0) / max * 200; }
  get dailySales(): Array<{ date: string; label: string; units: number; revenue: number; revenueWidth: number; unitsWidth: number; hours: string; active: boolean }> {
    return this.memo('dailySales', () => {
      if (!this.hasDetailedTransactions()) {
        const days = this.summaryDaysForPeriod().slice(-7);
        const maxRevenue = Math.max(...days.map(day => Number(day.salesRevenue)), 1);
        const maxUnits = Math.max(...days.map(day => Number(day.salesUnits)), 1);
        return days.map(day => ({ date: day.date, label: new Date(`${day.date}T12:00:00`).toLocaleDateString('es', { weekday: 'short' }), units: Number(day.salesUnits), revenue: Number(day.salesRevenue), revenueWidth: Number(day.salesRevenue) / maxRevenue * 100, unitsWidth: Number(day.salesUnits) / maxUnits * 100, hours: 'Resumen precalculado', active: this.selectedDates.has(day.date) }));
      }
      const map = new Map<string, { units: number; revenue: number; hours: Set<string> }>();
      this.matchingTransactions(['dates']).filter(item => this.isAnalyticTransaction(item)).forEach(item => { const key = item.dateTime?.slice(0, 10) ?? ''; const current = map.get(key) ?? { units: 0, revenue: 0, hours: new Set<string>() }; current.units += item.quantity; current.revenue += this.amount(item, 'price'); const time = item.dateTime?.slice(11, 16); if (time) current.hours.add(time); map.set(key, current); });
      const days = [...map.entries()].sort((a, b) => a[0].localeCompare(b[0])).slice(-7);
      const maxRevenue = Math.max(...days.map(([, value]) => value.revenue), 1); const maxUnits = Math.max(...days.map(([, value]) => value.units), 1);
      return days.map(([date, value]) => ({ date, label: new Date(`${date}T12:00:00`).toLocaleDateString('es', { weekday: 'short' }), units: value.units, revenue: value.revenue, revenueWidth: value.revenue / maxRevenue * 100, unitsWidth: value.units / maxUnits * 100, hours: [...value.hours].sort().join(', ') || 'Sin hora', active: this.selectedDates.has(date) }));
    });
  }
  get dailyRevenuePoints(): string { return this.dailySales.map((day, index) => `${this.dailyPointX(index)},${this.dailyPointY(day.revenueWidth)}`).join(' '); }
  get dailyUnitsPoints(): string { return this.dailySales.map((day, index) => `${this.dailyPointX(index)},${this.dailyPointY(day.unitsWidth)}`).join(' '); }
  dailyPointX(index: number): number { return this.dailySales.length > 1 ? index * 700 / (this.dailySales.length - 1) : 350; }
  dailyPointY(width: number): number { return 130 - width * 1.1; }
  get paymentBreakdown(): Array<{ name: string; units: number; width: number; active: boolean }> {
    return this.memo('paymentBreakdown', () => {
      const map = new Map<string, number>();
      if (!this.hasDetailedTransactions()) this.summaryDaysForPeriod().forEach(day => Object.entries(day.payments).forEach(([name, units]) => map.set(name, (map.get(name) ?? 0) + Number(units))));
      else { this.paymentMethodConfigs.filter(item => item.isActive !== false).forEach(item => map.set(item.name, 0)); this.matchingTransactions(['payments']).filter(item => this.isAnalyticTransaction(item)).forEach(item => { const name = item.paymentMethod?.paymentMethodConfig?.name ?? 'Sin método'; map.set(name, (map.get(name) ?? 0) + item.quantity); }); }
      const total = Math.max([...map.values()].reduce((sum, units) => sum + units, 0), 1);
      return [...map.entries()].map(([name, units]) => ({ name, units, width: units / total * 100, active: this.selectedPayments.has(name) })).sort((a, b) => b.units - a.units);
    });
  }
  toggleProduct(summary: ProductSummary): void { summary.expanded ? this.expandedProductIds.delete(summary.root.id) : this.expandedProductIds.add(summary.root.id); this.bump(); this.cdr.markForCheck(); }
  lotMetrics(lot: ProductRecord): { units: number; revenue: number; profit: number } {
    const metricsByProduct = this.memo('lotMetricsByProduct', () => {
      const metrics = new Map<number, { units: number; revenue: number; profit: number }>();
      this.sales.forEach(item => {
        const productId = item.product?.id;
        if (!productId) return;
        const current = metrics.get(productId) ?? { units: 0, revenue: 0, profit: 0 };
        const revenue = this.amount(item, 'price');
        const cost = this.amount(item, 'cost');
        current.units += item.quantity;
        current.revenue += revenue;
        current.profit += revenue - cost;
        metrics.set(productId, current);
      });
      return metrics;
    });
    return metricsByProduct.get(lot.id) ?? { units: 0, revenue: 0, profit: 0 };
  }
  toggleTag(name: string): void { this.toggleSet(this.selectedTags, name); }
  toggleDate(date: string): void { this.toggleSet(this.selectedDates, date); }
  togglePayment(name: string): void { this.toggleSet(this.selectedPayments, name); }
  toggleProductFilter(rootId: number): void { this.toggleSet(this.selectedProductIds, rootId); }
  isProductSelected(rootId: number): boolean { return this.selectedProductIds.has(rootId); }
  private productColor(index: number): string { const hue = (index * 137.508) % 360; return `hsl(${hue.toFixed(1)} 68% 42%)`; }
  clearFilters(): void { this.typeFilter = 'SALIDA'; this.reasonFilter = 'ALL'; this.dateFrom = ''; this.dateTo = ''; this.executivePeriod = 'ALL'; this.selectedTags.clear(); this.selectedDates.clear(); this.selectedProductIds.clear(); this.selectedPayments.clear(); this.bump(); this.cdr.markForCheck(); }
  toggleFilters(): void { this.filtersOpen = !this.filtersOpen; if (this.filtersOpen && !this.hasDetailedTransactions()) { const token = localStorage.getItem('token'); if (token) { const fromDate = this.dateFrom || null; this.loadDetailedTransactions(fromDate, new HttpHeaders({ Authorization: `Bearer ${token}` }), fromDate ?? 'ALL'); } } this.cdr.markForCheck(); }

  get activeFilterChips(): FilterChip[] {
    return this.memo('activeFilterChips', () => {
      const chips: FilterChip[] = [];
      this.selectedTags.forEach(name => chips.push({ group: 'Etiqueta', label: name, onRemove: () => this.toggleTag(name) }));
      this.selectedDates.forEach(date => chips.push({ group: 'Día', label: new Date(`${date}T12:00:00`).toLocaleDateString('es', { day: '2-digit', month: 'short' }), onRemove: () => this.toggleDate(date) }));
      this.selectedPayments.forEach(name => chips.push({ group: 'Pago', label: name, onRemove: () => this.togglePayment(name) }));
      this.selectedProductIds.forEach(id => { const product = this.rootProducts.find(item => item.id === id); chips.push({ group: 'Producto', label: product?.name ?? `#${id}`, onRemove: () => this.toggleProductFilter(id) }); });
      return chips;
    });
  }

  get lowStockProducts(): StockRiskItem[] {
    return this.memo('lowStockProducts', () => this.rootProducts
      .filter(product => product.alert?.isEnabled !== false)
      .map(product => ({ id: product.id, name: product.name, stock: this.stockFor(product), threshold: this.threshold(product) }))
      .filter(item => item.stock > 0 && item.stock <= item.threshold)
      .sort((a, b) => a.stock - b.stock)
      .map(item => ({ ...item, severity: (item.stock <= item.threshold / 2 ? 'critical' : 'warning') as 'critical' | 'warning' })));
  }

  get stockRisk(): StockRiskItem[] {
    return this.memo('stockRisk', () => this.rootProducts
      .map(product => ({ id: product.id, name: product.name, stock: this.stockFor(product), threshold: this.threshold(product) }))
      .filter(item => item.stock <= item.threshold)
      .map(item => ({ ...item, severity: (item.stock <= item.threshold / 2 ? 'critical' : 'warning') as 'critical' | 'warning' }))
        .sort((a, b) => a.stock - b.stock));
  }
  formatMoney(value: number): string { return `${this.currency} ${Number(value || 0).toLocaleString('es-BO', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`; }
  formatUnits(value: number): string { return Number(value || 0).toLocaleString('es-BO'); }
  private amount(transaction: TransactionRecord, field: 'price' | 'cost'): number { return Number(transaction.product?.[field] ?? 0) * Number(transaction.quantity ?? 0); }
  private threshold(product: ProductRecord): number { return product.alert?.isEnabled !== false ? Number(product.alert?.threshold ?? 5) : Number.MAX_SAFE_INTEGER; }
  private stockFor(product: ProductRecord): number {
    const stockByRoot = this.memo('stockByRoot', () => {
      const totals = new Map<number, number>();
      this.products.forEach(item => {
        const rootId = item.parentId ?? item.id;
        totals.set(rootId, (totals.get(rootId) ?? 0) + Number(item.stock ?? 0));
      });
      return totals;
    });
    return stockByRoot.get(product.id) ?? 0;
  }
  private rootId(product?: ProductRecord): number { if (!product) return 0; return product.parentId ?? product.id; }
  private toggleSet<T>(set: Set<T>, value: T): void { set.has(value) ? set.delete(value) : set.add(value); this.bump(); this.cdr.markForCheck(); }
  private productTags(product: ProductRecord): Array<{ id: number; name: string }> { return (product.tags ?? []).map(item => 'tag' in item && item.tag ? item.tag : item as { id: number; name: string }); }
}

import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ApiConfigService } from '../../auth/api-config.service';

interface ProductRecord {
  id: number; name: string; parentId?: number | null; price?: number; cost?: number; stock?: number;
  tags?: Array<{ tag?: { id: number; name: string } } | { id: number; name: string }>;
  alert?: { threshold?: number; isEnabled?: boolean };
}
interface TransactionRecord {
  id: number; type: string; reason: string; quantity: number; dateTime: string; product?: ProductRecord;
  paymentMethod?: { paymentMethodConfig?: { id: number; name: string } };
}
interface ProductSummary { root: ProductRecord; lots: ProductRecord[]; units: number; revenue: number; cost: number; profit: number; expanded: boolean; barWidth: number; }
interface FilterChip { group: string; label: string; onRemove: () => void; }
interface StockRiskItem { id: number; name: string; stock: number; threshold: number; severity: 'critical' | 'warning'; }

@Component({
  selector: 'app-dashboard-info',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard-info.component.html',
  styleUrls: ['./dashboard-info.component.css']
})
export class DashboardInfoComponent implements OnInit {
  storeId = 0; storeName = 'Tienda'; loading = true; errorMessage = ''; currency = 'Bs';
  typeFilter: 'ALL' | 'ENTRADA' | 'SALIDA' = 'SALIDA'; reasonFilter = 'ALL'; selectedTag = 'ALL'; selectedPayment = 'ALL';
  sortBy: 'units' | 'revenue' | 'profit' = 'units'; dateFrom = ''; dateTo = '';
  transactions: TransactionRecord[] = []; products: ProductRecord[] = []; adminMovements: Array<{ dateTime: string; amountPaid: number }> = [];
  expandedProductIds = new Set<number>();
  selectedTags = new Set<string>();
  selectedDates = new Set<string>();
  selectedProductIds = new Set<number>();
  selectedPayments = new Set<string>();

  constructor(private route: ActivatedRoute, private http: HttpClient, private apiConfig: ApiConfigService) {}

  ngOnInit(): void {
    const id = this.route.parent?.snapshot.paramMap.get('id') ?? this.route.snapshot.paramMap.get('id');
    this.storeId = Number(id ?? 0); this.loadData();
  }

  get apiBase(): string { return this.apiConfig.getApiUrl(''); }

  loadData(): void {
    const token = localStorage.getItem('token');
    if (!token || !this.storeId) { this.errorMessage = 'No se pudo identificar la tienda.'; this.loading = false; return; }
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` }); this.loading = true;
    this.http.get<any>(`${this.apiBase}/api/stores/${this.storeId}`, { headers }).subscribe({ next: store => this.storeName = store?.name ?? `Tienda #${this.storeId}`, error: () => this.storeName = `Tienda #${this.storeId}` });
    this.http.get<ProductRecord[]>(`${this.apiBase}/api/products/store/${this.storeId}`, { headers }).subscribe({ next: products => this.products = products ?? [], error: () => this.products = [] });
    this.http.get<TransactionRecord[]>(`${this.apiBase}/api/transactions/store/${this.storeId}`, { headers }).subscribe({ next: transactions => { this.transactions = transactions ?? []; this.loading = false; }, error: () => { this.errorMessage = 'No se pudieron cargar los movimientos de la tienda.'; this.loading = false; } });
    this.http.get<any[]>(`${this.apiBase}/api/administrative-cost-movements/store/${this.storeId}`, { headers }).subscribe({ next: movements => this.adminMovements = movements ?? [], error: () => this.adminMovements = [] });
  }

  get reasons(): string[] { return [...new Set(this.transactions.map(item => item.reason).filter(Boolean))].sort(); }
  get tags(): Array<{ id: number; name: string }> { const result = new Map<number, string>(); this.products.forEach(product => this.productTags(product).forEach(tag => result.set(tag.id, tag.name))); return [...result.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name)); }
  get payments(): string[] { return [...new Set(this.transactions.map(item => item.paymentMethod?.paymentMethodConfig?.name).filter(Boolean) as string[])].sort(); }
  get filteredTransactions(): TransactionRecord[] { return this.transactions.filter(transaction => { const date = transaction.dateTime?.slice(0, 10) ?? ''; const tags = transaction.product ? this.productTags(transaction.product).map(tag => tag.name) : []; const rootId = this.rootId(transaction.product); const payment = transaction.paymentMethod?.paymentMethodConfig?.name; return (this.typeFilter === 'ALL' || transaction.type === this.typeFilter) && (this.reasonFilter === 'ALL' || transaction.reason === this.reasonFilter) && (!this.selectedTags.size || tags.some(tag => this.selectedTags.has(tag))) && (!this.selectedDates.size || this.selectedDates.has(date)) && (!this.selectedProductIds.size || this.selectedProductIds.has(rootId)) && (!this.selectedPayments.size || (payment ? this.selectedPayments.has(payment) : false)) && (!this.dateFrom || date >= this.dateFrom) && (!this.dateTo || date <= this.dateTo); }); }
  get sales(): TransactionRecord[] { return this.filteredTransactions.filter(item => item.type === 'SALIDA' && item.reason === 'VENTA'); }
  get totalRevenue(): number { return this.sales.reduce((sum, item) => sum + this.amount(item, 'price'), 0); }
  get totalCost(): number { return this.sales.reduce((sum, item) => sum + this.amount(item, 'cost'), 0); }
  get grossProfit(): number { return this.totalRevenue - this.totalCost; }
  get margin(): number { return this.totalRevenue ? this.grossProfit / this.totalRevenue * 100 : 0; }
  get adminCost(): number { return this.adminMovements.filter(item => (!this.dateFrom || item.dateTime?.slice(0, 10) >= this.dateFrom) && (!this.dateTo || item.dateTime?.slice(0, 10) <= this.dateTo)).reduce((sum, item) => sum + Number(item.amountPaid ?? 0), 0); }
  get netProfit(): number { return this.grossProfit - this.adminCost; }
  get losses(): number { return this.filteredTransactions.filter(item => item.type === 'SALIDA' && item.reason === 'PERDIDA').reduce((sum, item) => sum + item.quantity, 0); }
  get rootProducts(): ProductRecord[] { return this.products.filter(item => !item.parentId); }
  get lowStockCount(): number { return this.rootProducts.filter(item => this.stockFor(item) <= this.threshold(item)).length; }
  get averageTicket(): number { return this.sales.length ? this.totalRevenue / this.sales.length : 0; }

  get productSummaries(): ProductSummary[] { const key = this.sortBy; const raw = this.rootProducts.map(root => { const lots = this.products.filter(product => product.parentId === root.id); const ids = new Set([root.id, ...lots.map(lot => lot.id)]); const rows = this.sales.filter(item => item.product?.id && ids.has(item.product.id)); return { root, lots, expanded: this.expandedProductIds.has(root.id), units: rows.reduce((sum, item) => sum + item.quantity, 0), revenue: rows.reduce((sum, item) => sum + this.amount(item, 'price'), 0), cost: rows.reduce((sum, item) => sum + this.amount(item, 'cost'), 0), profit: rows.reduce((sum, item) => sum + this.amount(item, 'price') - this.amount(item, 'cost'), 0), barWidth: 0 }; }).filter(item => item.units > 0); const max = Math.max(...raw.map(item => item[key]), 1); return raw.map(item => ({ ...item, barWidth: Math.max(item[key] / max * 100, 3) })).sort((a, b) => b[key] - a[key]); }
  get categoryBreakdown(): Array<{ name: string; units: number; width: number; active: boolean }> { const map = new Map<string, number>(); this.sales.forEach(item => { const tags = item.product ? this.productTags(item.product) : []; (tags.length ? tags.map(tag => tag.name) : ['Sin etiqueta']).forEach(name => map.set(name, (map.get(name) ?? 0) + item.quantity)); }); const max = Math.max(...map.values(), 1); return [...map.entries()].map(([name, units]) => ({ name, units, width: units / max * 100, active: this.selectedTags.has(name) })).sort((a, b) => b.units - a.units); }
  get dailySales(): Array<{ date: string; label: string; units: number; revenue: number; width: number; active: boolean }> { const map = new Map<string, { units: number; revenue: number }>(); this.sales.forEach(item => { const key = item.dateTime?.slice(0, 10) ?? ''; const current = map.get(key) ?? { units: 0, revenue: 0 }; current.units += item.quantity; current.revenue += this.amount(item, 'price'); map.set(key, current); }); const max = Math.max(...[...map.values()].map(item => item.revenue), 1); return [...map.entries()].sort((a, b) => a[0].localeCompare(b[0])).slice(-7).map(([date, value]) => ({ date, label: new Date(`${date}T12:00:00`).toLocaleDateString('es', { weekday: 'short' }), ...value, width: value.revenue / max * 100, active: this.selectedDates.has(date) })); }
  get paymentBreakdown(): Array<{ name: string; units: number; width: number; active: boolean }> { const map = new Map<string, number>(); this.sales.forEach(item => { const name = item.paymentMethod?.paymentMethodConfig?.name ?? 'Sin método'; map.set(name, (map.get(name) ?? 0) + item.quantity); }); const total = Math.max(this.sales.reduce((sum, item) => sum + item.quantity, 0), 1); return [...map.entries()].map(([name, units]) => ({ name, units, width: units / total * 100, active: this.selectedPayments.has(name) })).sort((a, b) => b.units - a.units); }
  toggleProduct(summary: ProductSummary): void { summary.expanded ? this.expandedProductIds.delete(summary.root.id) : this.expandedProductIds.add(summary.root.id); }
  lotMetrics(lot: ProductRecord): { units: number; revenue: number; profit: number } { const rows = this.sales.filter(item => item.product?.id === lot.id); return { units: rows.reduce((sum, item) => sum + item.quantity, 0), revenue: rows.reduce((sum, item) => sum + this.amount(item, 'price'), 0), profit: rows.reduce((sum, item) => sum + this.amount(item, 'price') - this.amount(item, 'cost'), 0) }; }
  toggleTag(name: string): void { this.toggleSet(this.selectedTags, name); }
  toggleDate(date: string): void { this.toggleSet(this.selectedDates, date); }
  togglePayment(name: string): void { this.toggleSet(this.selectedPayments, name); }
  toggleProductFilter(rootId: number): void { this.toggleSet(this.selectedProductIds, rootId); }
  isProductSelected(rootId: number): boolean { return this.selectedProductIds.has(rootId); }
  clearFilters(): void { this.typeFilter = 'SALIDA'; this.reasonFilter = 'ALL'; this.selectedTag = 'ALL'; this.selectedPayment = 'ALL'; this.dateFrom = ''; this.dateTo = ''; this.selectedTags.clear(); this.selectedDates.clear(); this.selectedProductIds.clear(); this.selectedPayments.clear(); }

  get activeFilterChips(): FilterChip[] {
    const chips: FilterChip[] = [];
    this.selectedTags.forEach(name => chips.push({ group: 'Etiqueta', label: name, onRemove: () => this.toggleTag(name) }));
    this.selectedDates.forEach(date => chips.push({ group: 'Día', label: new Date(`${date}T12:00:00`).toLocaleDateString('es', { day: '2-digit', month: 'short' }), onRemove: () => this.toggleDate(date) }));
    this.selectedPayments.forEach(name => chips.push({ group: 'Pago', label: name, onRemove: () => this.togglePayment(name) }));
    this.selectedProductIds.forEach(id => { const product = this.rootProducts.find(item => item.id === id); chips.push({ group: 'Producto', label: product?.name ?? `#${id}`, onRemove: () => this.toggleProductFilter(id) }); });
    return chips;
  }

  get stockRisk(): StockRiskItem[] {
    return this.rootProducts
      .map(product => ({ id: product.id, name: product.name, stock: this.stockFor(product), threshold: this.threshold(product) }))
      .filter(item => item.stock <= item.threshold)
      .map(item => ({ ...item, severity: (item.stock <= item.threshold / 2 ? 'critical' : 'warning') as 'critical' | 'warning' }))
      .sort((a, b) => a.stock - b.stock)
      .slice(0, 6);
  }
  formatMoney(value: number): string { return `${this.currency} ${Number(value || 0).toLocaleString('es-BO', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`; }
  formatUnits(value: number): string { return Number(value || 0).toLocaleString('es-BO'); }
  private amount(transaction: TransactionRecord, field: 'price' | 'cost'): number { return Number(transaction.product?.[field] ?? 0) * Number(transaction.quantity ?? 0); }
  private threshold(product: ProductRecord): number { return product.alert?.isEnabled !== false ? Number(product.alert?.threshold ?? 5) : Number.MAX_SAFE_INTEGER; }
  private stockFor(product: ProductRecord): number { return this.products.filter(item => item.id === product.id || item.parentId === product.id).reduce((sum, item) => sum + Number(item.stock ?? 0), 0); }
  private rootId(product?: ProductRecord): number { if (!product) return 0; return product.parentId ?? product.id; }
  private toggleSet<T>(set: Set<T>, value: T): void { set.has(value) ? set.delete(value) : set.add(value); }
  private productTags(product: ProductRecord): Array<{ id: number; name: string }> { return (product.tags ?? []).map(item => 'tag' in item && item.tag ? item.tag : item as { id: number; name: string }); }
}

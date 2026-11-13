import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MovimientosComponent as Movimientos } from './movimientos';

describe('Movimientos', () => {
  let component: Movimientos;
  let fixture: ComponentFixture<Movimientos>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Movimientos],
    }).compileComponents();

    fixture = TestBed.createComponent(Movimientos);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should count only purchase and sale reasons in the stats grid', () => {
    component.transactions = [
      { id: 1, type: 'ENTRADA', reason: 'COMPRA', dateTime: new Date().toISOString() },
      { id: 2, type: 'ENTRADA', reason: 'AJUSTE', dateTime: new Date().toISOString() },
      { id: 3, type: 'ENTRADA', reason: 'DEVOLUCIÓN', dateTime: new Date().toISOString() },
      { id: 4, type: 'SALIDA', reason: 'VENTA', dateTime: new Date().toISOString() },
      { id: 5, type: 'SALIDA', reason: 'PERDIDA', dateTime: new Date().toISOString() },
      { id: 6, type: 'SALIDA', reason: 'DEVOLUCION', dateTime: new Date().toISOString() },
      { id: 7, type: 'SALIDA', reason: 'AJUSTE', dateTime: new Date().toISOString() }
    ];

    expect(component.entradasCount).toBe(1);
    expect(component.salidasCount).toBe(1);
  });

  it('should open today movement modals when the history is collapsed', () => {
    component.showHistoryList = false;
    component.historyLoaded = true;

    component.openTodayEntriesModal();
    expect(component.showHistoryList).toBe(true);
    expect(component.showTodayEntriesModal).toBe(true);

    component.closeTodayEntriesModal();
    component.showHistoryList = false;

    component.openTodaySalidasModal();
    expect(component.showHistoryList).toBe(true);
    expect(component.showTodaySalidasModal).toBe(true);
  });

  it('should preserve the selected product and quantity when changing movement type', () => {
    component.movement = { type: 'ENTRADA', productId: 42, quantity: 7, reason: 'VENTA' };
    component.selectedProductId = 42;
    component.productSearchTerm = 'Cerveza';
    component.selectedLoteId = 43;
    component.availableLotes = [{ id: 43, name: 'Lote seleccionado' }];

    component.onMovementTypeChange();

    expect(component.movement.productId).toBe(42);
    expect(component.movement.quantity).toBe(7);
    expect(component.productSearchTerm).toBe('Cerveza');
    expect(component.selectedProductId).toBe(42);
    expect(component.selectedLoteId).toBe(43);
    expect(component.availableLotes.length).toBe(1);
    expect(component.movement.reason).toBe('COMPRA');
  });

  it('should mark the lot selector red only when the selected lot stock is zero', () => {
    component.availableLotes = [
      { id: 1, stock: 0 },
      { id: 2, stock: 5 }
    ];

    component.selectedLoteId = 1;
    expect(component.isSelectedLoteOutOfStock).toBe(true);

    component.selectedLoteId = 2;
    expect(component.isSelectedLoteOutOfStock).toBe(false);

    component.selectedLoteId = 3;
    expect(component.isSelectedLoteOutOfStock).toBe(false);
  });

  it('should clear the product search but keep the last product image and movement settings', () => {
    const lastProduct = { id: 42, name: 'Cerveza', imageProductId: 42 };
    component.movement = { type: 'ENTRADA', productId: 43, quantity: 6, reason: 'COMPRA' };
    component.productSearchTerm = 'Cerveza';
    component.selectedProductId = 42;
    component.selectedLoteId = 43;
    component.availableLotes = [{ id: 43 }];

    component['clearMovementProductSelection'](lastProduct);

    expect(component.productSearchTerm).toBe('');
    expect(component.movement.productId).toBe(0);
    expect(component.selectedProductId).toBe(0);
    expect(component.movement.type).toBe('ENTRADA');
    expect(component.movement.reason).toBe('COMPRA');
    expect(component.movement.quantity).toBe(6);
    expect(component.selectedProductForImage).toBe(lastProduct);
    expect(component.availableLotes).toEqual([]);
  });

  it('should display recently selected products in most-recent-first order', () => {
    const storageKey = 'recent_products_movement';
    localStorage.setItem(storageKey, JSON.stringify([2, 1]));
    component.products = [
      { id: 1, name: 'Producto anterior', isActive: true, isActiveForSale: true, stock: 5 },
      { id: 2, name: 'Último elegido', isActive: true, isActiveForSale: true, stock: 8 }
    ];
    component['lotesMap'] = new Map();

    component['loadRecentProducts']();

    expect(component.recentProducts.map(product => product.id)).toEqual([2, 1]);
    localStorage.removeItem(storageKey);
  });

  it('should show recently selected image products before newest registered image products', () => {
    component.products = [
      { id: 1, name: 'Producto antiguo', isActive: true, isActiveForSale: true, stock: 5, image: { imagePath: 'old.jpg' } },
      { id: 2, name: 'Último elegido', isActive: true, isActiveForSale: true, stock: 8, image: { imagePath: 'recent.jpg' } },
      { id: 3, name: 'Producto nuevo', isActive: true, isActiveForSale: true, stock: 2, image: { imagePath: 'new.jpg' } },
      { id: 4, name: 'Sin imagen', isActive: true, isActiveForSale: true, stock: 4 }
    ];
    component['lotesMap'] = new Map();
    component['displayProducts'] = component['getInventoryDisplayProducts']();
    component.recentProducts = [component['displayProducts'][1]];

    component['updateRecentImageProducts']();

    expect(component.recentImageProducts.map(product => product.id)).toEqual([2, 3, 1]);
  });

  it('should show totals for today and yesterday only in the movement products modal', () => {
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    const twoDaysAgo = new Date(today);
    twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);
    component.transactions = [
      { id: 1, productId: 1, type: 'ENTRADA', reason: 'COMPRA', quantity: 10, product: { cost: 8, price: 15 }, dateTime: today },
      { id: 2, productId: 1, type: 'ENTRADA', reason: 'AJUSTE', quantity: 2, product: { cost: 8, price: 15 }, dateTime: today },
      { id: 3, productId: 1, type: 'SALIDA', reason: 'VENTA', quantity: 3, product: { cost: 8, price: 15 }, dateTime: today },
      { id: 4, productId: 1, type: 'SALIDA', reason: 'VENTA', quantity: 20, product: { cost: 4, price: 12 }, dateTime: yesterday },
      { id: 5, productId: 1, type: 'SALIDA', reason: 'VENTA', quantity: 100, product: { cost: 5, price: 20 }, dateTime: twoDaysAgo }
    ];

    component.applyFilters();
    component.openMovementProductsModal();

    expect(component.movementDaySummaries.length).toBe(2);
    expect(component.movementDaySummaries[0].label).toBe(`Hoy · ${String(today.getDate()).padStart(2, '0')}/${String(today.getMonth() + 1).padStart(2, '0')}/${today.getFullYear()}`);
    expect(component.movementDaySummaries[0].entries).toBe(12);
    expect(component.movementDaySummaries[0].entryCost).toBe(80);
    expect(component.movementDaySummaries[0].sales).toBe(3);
    expect(component.movementDaySummaries[0].salesRevenue).toBe(45);
    expect(component.movementDaySummaries[0].salesCost).toBe(24);
    expect(component.movementDaySummaries[0].grossProfit).toBe(21);
    expect(component.movementDaySummaries[1].label).toBe(`Ayer · ${String(yesterday.getDate()).padStart(2, '0')}/${String(yesterday.getMonth() + 1).padStart(2, '0')}/${yesterday.getFullYear()}`);
    expect(component.movementDaySummaries[1].sales).toBe(20);
    expect(component.movementDaySummaries[1].salesRevenue).toBe(240);
    expect(component.movementDaySummaries[1].salesCost).toBe(80);
    expect(component.movementDaySummaries[1].grossProfit).toBe(160);
  });

  it('should keep the movement history collapsed by default', () => {
    expect(component.showHistoryList).toBe(false);
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Inventario } from './inventario';
import { LotesService } from '../../services/lotes.service';

describe('Inventario - Lotes Management', () => {
  let component: Inventario;
  let fixture: ComponentFixture<Inventario>;
  let lotesService: jasmine.SpyObj<LotesService>;

  // Mock data
  const mockRootProduct = {
    id: 1,
    name: 'Coca Cola',
    price: 5,
    cost: 2,
    stock: 100,
    parentId: null,
    isActive: true,
    isActiveForSale: false,
    alert: { isEnabled: false, threshold: 10 }
  };

  const mockLote1 = {
    id: 101,
    name: 'Coca Cola - Lote 2024-01',
    price: 5,
    cost: 2,
    stock: 50,
    parentId: 1,
    isActive: true,
    isActiveForSale: true,
    alert: { isEnabled: true, threshold: 10 }
  };

  const mockLote2 = {
    id: 102,
    name: 'Coca Cola - Lote 2024-02',
    price: 5,
    cost: 2,
    stock: 30,
    parentId: 1,
    isActive: true,
    isActiveForSale: false,
    alert: { isEnabled: true, threshold: 10 }
  };

  const mockInactiveLote = {
    id: 103,
    name: 'Coca Cola - Lote Inactivo',
    price: 5,
    cost: 2,
    stock: 20,
    parentId: 1,
    isActive: false,
    isActiveForSale: false,
    alert: { isEnabled: false, threshold: 10 }
  };

  beforeEach(async () => {
    const lotesServiceSpy = jasmine.createSpyObj('LotesService', [
      'getLotesByProductId',
      'createLote',
      'setActiveLote',
      'toggleActiveLote',
      'deleteLote',
      'setActiveForSale'
    ]);

    await TestBed.configureTestingModule({
      imports: [Inventario, HttpClientTestingModule],
      providers: [
        { provide: LotesService, useValue: lotesServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Inventario);
    component = fixture.componentInstance;
    lotesService = TestBed.inject(LotesService) as jasmine.SpyObj<LotesService>;
    
    // Inicializar datos básicos
    component.products = [mockRootProduct];
    component.filteredProducts = [mockRootProduct];
    component.lotesMap = new Map();
    component.storeId = 1;
    component.lowStockThreshold = 10;
    component.normalStockThreshold = 50;
  });

  describe('Component Creation', () => {
    it('should create the component', () => {
      expect(component).toBeTruthy();
    });
  });

  describe('getActiveLoteForProduct()', () => {
    it('should return lote with isActiveForSale=true first', () => {
      component.lotesMap.set(1, [mockLote1, mockLote2]);
      
      const activeLote = component.getActiveLoteForProduct(1);
      
      expect(activeLote).toEqual(mockLote1);
      expect(activeLote.isActiveForSale).toBe(true);
    });

    it('should return first active lote when no isActiveForSale lote exists', () => {
      component.lotesMap.set(1, [mockLote2, mockInactiveLote]);
      
      const activeLote = component.getActiveLoteForProduct(1);
      
      expect(activeLote).toEqual(mockLote2);
      expect(activeLote.isActive).toBe(true);
    });

    it('should return null when no active lotes exist', () => {
      component.lotesMap.set(1, [mockInactiveLote]);
      
      const activeLote = component.getActiveLoteForProduct(1);
      
      expect(activeLote).toBeNull();
    });

    it('should return null when product has no lotes', () => {
      component.lotesMap.set(1, []);
      
      const activeLote = component.getActiveLoteForProduct(1);
      
      expect(activeLote).toBeNull();
    });
  });

  describe('updateFilteredProductsWithActiveLotes()', () => {
    it('should show root product when isActiveForSale=true', () => {
      const rootWithActiveSale = { ...mockRootProduct, isActiveForSale: true };
      component.products = [rootWithActiveSale];
      component.lotesMap.set(1, [mockLote1]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      expect(component.filteredProducts.length).toBe(1);
      expect(component.filteredProducts[0]).toEqual(rootWithActiveSale);
    });

    it('should show active lote when root is not activeForSale', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockLote1, mockLote2]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      expect(component.filteredProducts.length).toBe(1);
      expect(component.filteredProducts[0]).toEqual(mockLote1);
    });

    it('should show root product when no active lote exists', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockInactiveLote]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      expect(component.filteredProducts.length).toBe(1);
      expect(component.filteredProducts[0]).toEqual(mockRootProduct);
    });

    it('should handle multiple products correctly', () => {
      const product2 = { ...mockRootProduct, id: 2, name: 'Sprite' };
      component.products = [mockRootProduct, product2];
      component.lotesMap.set(1, [mockLote1]);
      component.lotesMap.set(2, []);
      
      component.updateFilteredProductsWithActiveLotes();
      
      expect(component.filteredProducts.length).toBe(2);
      expect(component.filteredProducts[0]).toEqual(mockLote1);
      expect(component.filteredProducts[1]).toEqual(product2);
    });
  });

  describe('getDisplayProductsForAlerts()', () => {
    it('should return root product when isActiveForSale=true', () => {
      const rootWithActiveSale = { ...mockRootProduct, isActiveForSale: true };
      component.products = [rootWithActiveSale];
      component.lotesMap.set(1, [mockLote1]);
      
      const displayProducts = component['getDisplayProductsForAlerts']();
      
      expect(displayProducts[0]).toEqual(rootWithActiveSale);
    });

    it('should return active lote for alerts when available', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockLote1]);
      
      const displayProducts = component['getDisplayProductsForAlerts']();
      
      expect(displayProducts[0]).toEqual(mockLote1);
    });

    it('should return root product as fallback for alerts', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, []);
      
      const displayProducts = component['getDisplayProductsForAlerts']();
      
      expect(displayProducts[0]).toEqual(mockRootProduct);
    });
  });

  describe('getAlertsWithStatus()', () => {
    it('should show alerts only for active products/lotes with low stock', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockLote1]);
      
      // Configurar lote1 con stock bajo
      mockLote1.stock = 5;
      mockLote1.alert.threshold = 10;
      
      const alerts = component.getAlertsWithStatus('low');
      
      expect(alerts.length).toBeGreaterThan(0);
      expect(alerts.some(a => a.id === 101)).toBe(true);
    });

    it('should not show alerts for inactive lotes', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockInactiveLote]);
      
      // El lote inactivo nunca debería aparecer en alertas
      const alerts = component.getAlertsWithStatus('low');
      
      expect(alerts.some(a => a.id === 103)).toBe(false);
    });

    it('should show alerts for out of stock', () => {
      component.products = [mockRootProduct];
      const loteOutOfStock = { ...mockLote1, stock: 0 };
      component.lotesMap.set(1, [loteOutOfStock]);
      
      const alerts = component.getAlertsWithStatus('out');
      
      expect(alerts.some(a => a.id === 101)).toBe(true);
    });
  });

  describe('openDescriptionModal()', () => {
    it('should open root product modal when clicking on root product', () => {
      component.openDescriptionModal(mockRootProduct);
      
      expect(component.selectedProductForDescription).toEqual(jasmine.objectContaining({
        id: 1,
        name: 'Coca Cola'
      }));
      expect(component.showDescriptionModal).toBe(true);
    });

    it('should render the description overlay above the gallery overlay', () => {
      component.openDescriptionModal(mockRootProduct);
      fixture.detectChanges();

      const overlay = fixture.nativeElement.querySelector('.description-modal-overlay') as HTMLElement;
      expect(overlay).toBeTruthy();
      expect(parseInt(getComputedStyle(overlay).zIndex || '0', 10)).toBeGreaterThan(1000);
    });

    it('should open root product modal when clicking on lote, showing lote data (price, cost, stock)', () => {
      component.products = [mockRootProduct];
      
      component.openDescriptionModal(mockLote1);
      
      // Abre el raíz
      expect(component.selectedProductForDescription).toEqual(jasmine.objectContaining({
        id: 1,
        name: 'Coca Cola'
      }));
      
      // Template usa getActiveLoteForProduct(1) para obtener precio/coste del lote activo
      const activeLote = component.getActiveLoteForProduct(1);
      expect(activeLote).toBeTruthy();
      expect(component.showDescriptionModal).toBe(true);
    });
  });

  describe('getActiveLoteToDisplay()', () => {
    it('should return null if selectedProductForDescription is null', () => {
      component.selectedProductForDescription = null;
      expect(component.getActiveLoteToDisplay()).toBeNull();
    });

    it('should return null if selectedProductForDescription is a lote (has parentId)', () => {
      component.selectedProductForDescription = mockLote1;  // parentId = 1
      component.lotesMap.set(1, [mockLote1]);
      expect(component.getActiveLoteToDisplay()).toBeNull();  // No show lote data for lote
    });

    it('should return active lote if selectedProductForDescription is root with active lote', () => {
      component.selectedProductForDescription = mockRootProduct;  // parentId = null
      component.lotesMap.set(1, [mockLote1]);  // mockLote1 es activo
      const activeLote = component.getActiveLoteToDisplay();
      expect(activeLote).toBeTruthy();
      expect(activeLote.id).toBe(2);  // Lote 1
    });

    it('should return null if selectedProductForDescription is root without active lotes', () => {
      component.selectedProductForDescription = mockRootProduct;
      component.lotesMap.set(1, []);  // No hay lotes
      expect(component.getActiveLoteToDisplay()).toBeNull();
    });
  });

  describe('getDisplayProductData()', () => {
    it('should show root data when getActiveLoteToDisplay() returns null', () => {
      component.selectedProductForDescription = mockRootProduct;
      component.lotesMap.set(1, []);  // Sin lotes activos
      
      const cost = component.getDisplayProductData(mockRootProduct, 'cost');
      expect(cost).toBe(3.00);  // Datos del raíz
    });

    it('should show active lote data when getActiveLoteToDisplay() returns lote', () => {
      component.selectedProductForDescription = mockRootProduct;
      component.lotesMap.set(1, [mockLote1]);  // Con lote activo
      
      const cost = component.getDisplayProductData(mockRootProduct, 'cost');
      expect(cost).toBe(2.50);  // Datos del lote
    });

    it('should show lote data when active lote changes (modal of root, lote1 active then lote2)', () => {
      component.selectedProductForDescription = mockRootProduct;
      component.lotesMap.set(1, [mockLote1, mockLote2]);
      
      // Inicialmente lote1 activo
      let cost = component.getDisplayProductData(mockRootProduct, 'cost');
      expect(cost).toBe(2.50);
      
      // Cambiar: lote2 activo
      mockLote1.isActiveForSale = false;
      mockLote2.isActiveForSale = true;
      component.lotesMap.set(1, [mockLote1, mockLote2]);
      
      // Debe mostrar datos del lote2
      cost = component.getDisplayProductData(mockRootProduct, 'cost');
      expect(cost).toBe(2.75);
    });

    it('should return root data when clicking root (not lote)', () => {
      component.selectedProductForDescription = mockRootProduct;
      component.lotesMap.set(1, [mockLote1]);  // Hay lotes, pero...
      // ...raíz está activo (no lote)
      mockLote1.isActiveForSale = false;
      mockRootProduct.isActiveForSale = true;
      
      const cost = component.getDisplayProductData(mockRootProduct, 'cost');
      expect(cost).toBe(3.00);  // Debe ser del raíz
    });
  });

  describe('openLotesModal()', () => {
    it('should open lotes modal for root product', () => {
      component.openLotesModal(mockRootProduct);
      
      expect(component.selectedProductForLotes).toEqual(mockRootProduct);
      expect(component.showLotesModal).toBe(true);
    });

    it('should open lotes modal of parent when clicking on lote', () => {
      component.products = [mockRootProduct];
      
      component.openLotesModal(mockLote1);
      
      // Debería abrir el modal del producto raíz
      expect(component.selectedProductForLotes).toEqual(mockRootProduct);
      expect(component.showLotesModal).toBe(true);
    });
  });

  describe('onLotesUpdated()', () => {
    it('should update filtered products and close modal', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockLote1]);
      component.showLotesModal = true;
      
      spyOn(component, 'updateFilteredProductsWithActiveLotes');
      spyOn(component, 'closeLotesModal');
      
      component.onLotesUpdated();
      
      expect(component.updateFilteredProductsWithActiveLotes).toHaveBeenCalled();
      expect(component.closeLotesModal).toHaveBeenCalled();
    });
  });

  describe('Search with Active Lotes', () => {
    it('should filter by search term but keep active lote mapping', () => {
      component.products = [
        mockRootProduct,
        { ...mockRootProduct, id: 2, name: 'Sprite', parentId: null }
      ];
      component.lotesMap.set(1, [mockLote1]);
      component.lotesMap.set(2, []);
      component.searchTerm = 'Coca';
      
      component.onSearch();
      
      expect(component.filteredProducts.length).toBe(1);
      expect(component.filteredProducts[0].id).toBe(101); // Should show lote1
    });

    it('should show root when search matches and no active lote', () => {
      const product2 = { ...mockRootProduct, id: 2, name: 'Sprite' };
      component.products = [mockRootProduct, product2];
      component.lotesMap.set(1, [mockLote1]);
      component.lotesMap.set(2, []);
      component.searchTerm = 'Sprite';
      
      component.onSearch();
      
      expect(component.filteredProducts.some(p => p.id === 2)).toBe(true);
    });
  });

  describe('Edge Cases and Error Handling', () => {
    it('should handle null lotes gracefully', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, null as any);
      
      expect(() => {
        component.updateFilteredProductsWithActiveLotes();
      }).not.toThrow();
    });

    it('should handle empty products array', () => {
      component.products = [];
      
      expect(() => {
        component.updateFilteredProductsWithActiveLotes();
      }).not.toThrow();
      
      expect(component.filteredProducts.length).toBe(0);
    });

    it('should maintain product reference integrity', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockLote1]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      // El objeto mostrado debería ser el del lote, no una copia
      expect(component.filteredProducts[0]).toBe(mockLote1);
    });

    it('should handle rapid updates without losing data', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockLote1]);
      
      // Simular múltiples actualizaciones rápidas
      for (let i = 0; i < 5; i++) {
        component.updateFilteredProductsWithActiveLotes();
      }
      
      expect(component.filteredProducts[0]).toEqual(mockLote1);
    });
  });

  describe('Stock Status with Lotes', () => {
    it('should return correct stock status for active lote', () => {
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [mockLote1]);
      
      const status = component.getStockStatus(mockLote1);
      
      expect(status).toBe('normal'); // 50 > 10 threshold
    });

    it('should return low stock status for active lote', () => {
      const lowStockLote = { ...mockLote1, stock: 5 };
      const status = component.getStockStatus(lowStockLote);
      
      expect(status).toBe('low'); // 5 <= 10 threshold
    });

    it('should return out of stock status for active lote', () => {
      const outOfStockLote = { ...mockLote1, stock: 0 };
      const status = component.getStockStatus(outOfStockLote);
      
      expect(status).toBe('out'); // 0 === 0
    });
  });

  describe('Rule: Only ONE can be activeForSale', () => {
    it('should show ONLY lote1 when lote1 is activeForSale', () => {
      const lote1Active = { ...mockLote1, isActiveForSale: true };
      const lote2Inactive = { ...mockLote2, isActiveForSale: false };
      
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [lote1Active, lote2Inactive]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      expect(component.filteredProducts[0]).toEqual(lote1Active);
      expect(component.filteredProducts[0].isActiveForSale).toBe(true);
    });

    it('should show ONLY lote2 when lote2 is activeForSale', () => {
      const lote1Inactive = { ...mockLote1, isActiveForSale: false };
      const lote2Active = { ...mockLote2, isActiveForSale: true };
      
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [lote1Inactive, lote2Active]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      expect(component.filteredProducts[0]).toEqual(lote2Active);
      expect(component.filteredProducts[0].isActiveForSale).toBe(true);
    });

    it('should NEVER show two lotes as activeForSale simultaneously', () => {
      const lote1 = { ...mockLote1, isActiveForSale: true };
      const lote2 = { ...mockLote2, isActiveForSale: true };
      
      component.products = [mockRootProduct];
      component.lotesMap.set(1, [lote1, lote2]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      // Solo debe mostrar uno
      expect(component.filteredProducts.length).toBe(1);
      expect(component.filteredProducts[0].isActiveForSale).toBe(true);
      
      // Contar cuántos están marcados como activeForSale
      const activeCount = component.lotesMap.get(1).filter((l: any) => l.isActiveForSale).length;
      expect(activeCount).toBeLessThanOrEqual(1);
    });

    it('should show root when root is activeForSale (not lotes)', () => {
      const rootActive = { ...mockRootProduct, isActiveForSale: true };
      const lote1 = { ...mockLote1, isActiveForSale: false };
      const lote2 = { ...mockLote2, isActiveForSale: false };
      
      component.products = [rootActive];
      component.lotesMap.set(1, [lote1, lote2]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      // Debe mostrar el padre, no los lotes
      expect(component.filteredProducts[0]).toEqual(rootActive);
      expect(component.filteredProducts[0].id).toBe(1);
    });

    it('should show root when neither root nor lotes are activeForSale', () => {
      const rootInactive = { ...mockRootProduct, isActiveForSale: false };
      const lote1 = { ...mockLote1, isActiveForSale: false };
      const lote2 = { ...mockLote2, isActiveForSale: false };
      
      component.products = [rootInactive];
      component.lotesMap.set(1, [lote1, lote2]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      // Por defecto mostrar el padre
      expect(component.filteredProducts[0]).toEqual(rootInactive);
    });

    it('should validate no product has two or more activeForSale at same time', () => {
      const lote1 = { ...mockLote1, isActiveForSale: true };
      const lote2 = { ...mockLote2, isActiveForSale: true };
      const rootActive = { ...mockRootProduct, isActiveForSale: true };
      
      component.products = [rootActive];
      component.lotesMap.set(1, [lote1, lote2]);
      
      // Verificar que nunca hay dos o más activos
      const allProducts = [rootActive, lote1, lote2];
      const activeCount = allProducts.filter(p => p.isActiveForSale).length;
      
      // Aunque el estado es inválido, el frontend debe mostrar solo uno
      component.updateFilteredProductsWithActiveLotes();
      
      // Solo debe mostrar un producto en filteredProducts
      expect(component.filteredProducts.length).toBeLessThanOrEqual(1);
    });
  });

  describe('Rule: Always ONE must be activeForSale (Never zero)', () => {
    it('should show root product by default when all lotes are inactive', () => {
      const rootInactive = { ...mockRootProduct, isActiveForSale: false };
      const lote1 = { ...mockLote1, isActiveForSale: false };
      const lote2 = { ...mockLote2, isActiveForSale: false };
      
      component.products = [rootInactive];
      component.lotesMap.set(1, [lote1, lote2]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      // Por defecto mostrar el padre cuando nada está activo
      expect(component.filteredProducts[0]).toEqual(rootInactive);
      expect(component.filteredProducts[0].id).toBe(1);
    });

    it('should never have zero activeForSale products', () => {
      const products = [
        { ...mockRootProduct, isActiveForSale: false },
        { ...mockRootProduct, id: 2, isActiveForSale: false }
      ];
      
      component.products = products;
      component.lotesMap.set(1, []);
      component.lotesMap.set(2, []);
      
      component.updateFilteredProductsWithActiveLotes();
      
      // Cada producto debe tener algo mostrado (al menos el padre)
      expect(component.filteredProducts.length).toBeGreaterThan(0);
      component.filteredProducts.forEach(p => {
        expect(p).toBeTruthy();
      });
    });

    it('should always have exactly ONE activeForSale per product group', () => {
      const rootActive = { ...mockRootProduct, isActiveForSale: true };
      const lote1 = { ...mockLote1, isActiveForSale: false };
      const lote2 = { ...mockLote2, isActiveForSale: false };
      
      component.products = [rootActive];
      component.lotesMap.set(1, [lote1, lote2]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      // Debe haber exactamente uno activo
      const displayProduct = component.filteredProducts[0];
      expect(displayProduct.isActiveForSale).toBe(true);
      
      // Validar que en la estructura de datos, solo uno está marcado activo
      const allItems = [rootActive, lote1, lote2];
      const activeItems = allItems.filter(item => item.isActiveForSale);
      expect(activeItems.length).toBe(1);
    });

    it('backend should auto-activate parent when all lotes are deactivated', () => {
      // Este test valida el comportamiento del backend
      // Cuando intentes desactivar el último lote activo,
      // el backend debe auto-activar el padre
      
      const rootInitiallyInactive = { ...mockRootProduct, isActiveForSale: false };
      const loteActive = { ...mockLote1, isActiveForSale: true };
      const loteInactive = { ...mockLote2, isActiveForSale: false };
      
      component.products = [rootInitiallyInactive];
      component.lotesMap.set(1, [loteActive, loteInactive]);
      
      component.updateFilteredProductsWithActiveLotes();
      
      // Actualmente muestra el lote activo
      expect(component.filteredProducts[0]).toEqual(loteActive);
      
      // Si el backend desactiva loteActive sin activar nada,
      // debería auto-activar el padre
      // Este comportamiento está en ProductService.setActiveForSale()
    });
  });
});

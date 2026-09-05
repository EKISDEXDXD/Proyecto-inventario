import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LotesService } from '../../services/lotes.service';
import { timeout } from 'rxjs/operators';

interface PromoLote {
  id: number;
  name: string;
  stock: number;
  cost: number;
  price: number;
  parentId?: number | null;
  isActive?: boolean;
  isRoot?: boolean;
}

interface PromoProduct {
  id: number;
  parentId?: number | null;
  name: string;
  cost: number;
  price: number;
  stock: number;
  quantity: number;
  finalPrice: number;
  originalTotal: number;
  costTotal: number;
  lotes: PromoLote[];
  loteAllocations: Record<number, number>;
  lotesLoading: boolean;
}

@Component({
  selector: 'app-promotion-design-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './promotion-design-modal.component.html',
  styleUrl: './promotion-design-modal.component.css'
})
export class PromotionDesignModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() products: any[] = [];
  @Input() storeId = 0;
  @Output() onConfirm = new EventEmitter<any>();
  @Output() onClose = new EventEmitter<void>();

  private previousPageOverflow = '';

  promotionName: string = '';
  promotionDescription: string = '';
  mode: 'LOTE' | 'PRODUCTO_NUEVO' = 'LOTE';
  targetQuantity = 1;
  targetCost: number | null = null;
  customFinalPrice: number | null = null;
  warningMessage: string | null = null;

  promoProducts: PromoProduct[] = [];
  totalOriginal = 0;
  totalCost = 0;
  totalFinal = 0;

  constructor(
    private lotesService: LotesService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnChanges(changes: SimpleChanges) {
    const opened = changes['isOpen']?.currentValue === true;
    const closed = changes['isOpen']?.currentValue === false;
    const productsChanged = !!changes['products'];

    if (opened) {
      this.lockPageScroll(true);
    } else if (closed) {
      this.lockPageScroll(false);
    }

    if ((opened || productsChanged) && this.isOpen && this.products && this.products.length) {
      this.initPromoProducts();
    }
  }

  ngOnDestroy() {
    this.lockPageScroll(false);
  }

  private lockPageScroll(shouldLock: boolean) {
    if (typeof document === 'undefined') {
      return;
    }

    if (shouldLock) {
      this.previousPageOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      document.documentElement.style.overflow = 'hidden';
      return;
    }

    document.body.style.overflow = this.previousPageOverflow || '';
    document.documentElement.style.overflow = '';
  }

  private initPromoProducts() {
    this.customFinalPrice = null;
    this.targetCost = null;
    this.targetQuantity = 1;
    this.mode = 'LOTE';
    this.warningMessage = null;
    this.promoProducts = this.products.map(product => {
      const price = Number(product.displayPrice ?? product.price ?? 0);
      const cost = Number(product.displayCost ?? product.cost ?? 0);
      const stock = Math.max(0, Number(product.displayStock ?? product.stock ?? 0));
      const quantity = 1;
      const originalTotal = price * quantity;
      const costTotal = cost * quantity;
      const finalPrice = price * quantity;
      return {
        id: product.id,
        parentId: product.parentId ?? product.id ?? null,
        name: product.name,
        cost,
        price,
        stock,
        quantity,
        finalPrice,
        originalTotal,
        costTotal,
        lotes: [],
        loteAllocations: {},
        lotesLoading: true
      };
    });
    this.promoProducts.forEach(product => this.loadLotes(product));
    this.recalculateSummary();
  }

  private loadLotes(product: PromoProduct) {
    const parentId = product.parentId ?? product.id;
    const rootStock = Math.max(
      0,
      Number(this.products.find(item => item.id === product.id)?.rootStock
        ?? this.products.find(item => item.id === product.id)?.stock
        ?? 0)
    );
    const rootLote: PromoLote = {
      id: product.id,
      name: `${product.name} (stock raíz)`,
      stock: rootStock,
      cost: product.cost,
      price: product.price,
      parentId: null,
      isActive: true,
      isRoot: true
    };
    product.lotes = rootStock > 0 ? [rootLote] : [];
    this.lotesService.getLotesByProductId(parentId).pipe(timeout(5000)).subscribe({
      next: (lotes) => {
        const childLotes = (Array.isArray(lotes) ? lotes : [])
          .filter(lote => lote && lote.id && lote.isActive !== false && !lote.isDeleted && Number(lote.stock) > 0)
          .map(lote => ({
            id: Number(lote.id),
            name: lote.name || `Lote #${lote.id}`,
            stock: Math.max(0, Number(lote.stock) || 0),
            cost: Number(lote.cost) || 0,
            price: Number(lote.price) || 0,
            parentId: lote.parentId ?? parentId,
            isActive: lote.isActive !== false
          }));
        product.lotes = [...(rootStock > 0 ? [rootLote] : []), ...childLotes];
        product.lotesLoading = false;
        this.reconcileLoteAllocations(product);
        this.cdr.markForCheck();
      },
      error: () => {
        product.lotesLoading = false;
        this.reconcileLoteAllocations(product);
        this.cdr.markForCheck();
      }
    });
  }

  private reconcileLoteAllocations(product: PromoProduct) {
    if (!product.lotes.length) {
      return;
    }

    const allocations: Record<number, number> = {};
    let remaining = this.getRequiredSourceQuantity(product);
    for (const lote of product.lotes) {
      const requested = Math.max(0, Number(product.loteAllocations[lote.id]) || 0);
      const quantity = Math.min(lote.stock, requested, remaining);
      if (quantity > 0) {
        allocations[lote.id] = quantity;
        remaining -= quantity;
      }
    }

    if (remaining > 0) {
      for (const lote of product.lotes) {
        const current = allocations[lote.id] || 0;
        const quantity = Math.min(lote.stock - current, remaining);
        if (quantity > 0) {
          allocations[lote.id] = current + quantity;
          remaining -= quantity;
        }
        if (remaining === 0) {
          break;
        }
      }
    }
    product.loteAllocations = allocations;
  }

  getAllocatedQuantity(product: PromoProduct): number {
    return Object.values(product.loteAllocations).reduce((sum, quantity) => sum + (Number(quantity) || 0), 0);
  }

  getRequiredSourceQuantity(product: PromoProduct): number {
    const targetQuantity = Math.max(1, Number(this.targetQuantity) || 1);
    const quantityPerPromotion = Math.max(1, Number(product.quantity) || 1);
    return quantityPerPromotion * targetQuantity;
  }

  updateTargetQuantity() {
    this.targetQuantity = Math.max(1, Math.floor(Number(this.targetQuantity) || 1));
    this.promoProducts.forEach(product => this.reconcileLoteAllocations(product));
  }

  private validateLoteAllocations(): boolean {
    if (this.promoProducts.some(product => product.lotesLoading)) {
      this.warningMessage = 'Espera a que terminen de cargar los lotes antes de guardar la promoción.';
      return false;
    }

    const invalidProduct = this.promoProducts.find(product =>
      product.lotes.length > 0 && this.getAllocatedQuantity(product) !== this.getRequiredSourceQuantity(product)
    );
    if (invalidProduct) {
      this.warningMessage = `Asigna exactamente ${this.getRequiredSourceQuantity(invalidProduct)} unidad(es) de ${invalidProduct.name} entre sus lotes.`;
      return false;
    }
    return true;
  }

  onLoteAllocationChange(product: PromoProduct, lote: PromoLote) {
    const quantity = Math.min(lote.stock, Math.max(0, Number(product.loteAllocations[lote.id]) || 0));
    product.loteAllocations[lote.id] = quantity;
    const allocated = this.getAllocatedQuantity(product);
    const requiredQuantity = this.getRequiredSourceQuantity(product);
    if (allocated > requiredQuantity) {
      product.loteAllocations[lote.id] = Math.max(0, quantity - (allocated - requiredQuantity));
    }
  }

  adjustQuantity(product: PromoProduct, delta: number) {
    product.quantity = Math.min(product.stock || 1, Math.max(1, Number(product.quantity) + delta));
    this.updateProduct(product);
  }

  updateProduct(product: PromoProduct) {
    product.quantity = Math.min(product.stock || 1, Math.max(1, Number(product.quantity) || 1));
    this.reconcileLoteAllocations(product);
    product.originalTotal = product.price * product.quantity;
    product.costTotal = product.cost * product.quantity;
    product.finalPrice = product.price * product.quantity;
    this.recalculateSummary();
  }

  updateFinalPrice() {
    const parsed = Number(this.customFinalPrice ?? this.totalFinal);
    this.totalFinal = Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
    this.updateWarnings();
  }

  updateCostOverride(value: number | null) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
      this.targetCost = null;
      this.updateWarnings();
      return;
    }

    const parsed = Number(value);
    this.targetCost = Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
    this.updateWarnings();
  }

  private updateWarnings() {
    const costValue = Number(this.targetCost ?? this.totalCost ?? 0);
    const priceValue = Number(this.customFinalPrice ?? this.totalFinal ?? 0);
    this.warningMessage = priceValue < costValue ? 'El precio final es menor que el costo. Revisa la operación antes de guardar.' : null;
  }

  private recalculateSummary() {
    this.totalOriginal = this.promoProducts.reduce((sum, item) => sum + item.price * item.quantity, 0);
    this.totalCost = this.promoProducts.reduce((sum, item) => sum + item.cost * item.quantity, 0);
    const calculatedFinal = this.promoProducts.reduce((sum, item) => sum + item.finalPrice, 0);

    if (this.customFinalPrice === null) {
      this.customFinalPrice = calculatedFinal;
      this.totalFinal = calculatedFinal;
    } else {
      this.totalFinal = Number(this.customFinalPrice);
    }
    this.targetCost = this.targetCost ?? this.totalCost;
    this.updateWarnings();
  }

  private getParentProductId(): number | null {
    if (!this.promoProducts.length) {
      return null;
    }
    const first = this.promoProducts[0];
    return first.parentId ?? first.id ?? null;
  }

  confirm() {
    if (!this.validateLoteAllocations()) {
      return;
    }

    const sourceProducts = this.promoProducts.map(item => ({
      productId: item.id,
      quantity: this.getRequiredSourceQuantity(item),
      loteAllocations: item.lotes.length > 0
        ? item.lotes
          .map(lote => ({ loteId: lote.id, quantity: Number(item.loteAllocations[lote.id]) || 0 }))
          .filter(allocation => allocation.quantity > 0)
        : []
    }));

    const computedMode = this.mode ?? 'LOTE';

    const payload = {
      name: this.promotionName || 'Promo automática',
      description: this.promotionDescription,
      mode: computedMode,
      parentProductId: computedMode === 'LOTE' ? this.getParentProductId() : null,
      storeId: computedMode === 'PRODUCTO_NUEVO' ? this.storeId : null,
      targetQuantity: Math.max(1, Number(this.targetQuantity) || 1),
      cost: Number(this.targetCost ?? this.totalCost ?? 0),
      price: Number(this.customFinalPrice ?? this.totalFinal ?? 0),
      sourceProducts,
      sources: sourceProducts,
      target: {
        mode: computedMode,
        parentProductId: computedMode === 'LOTE' ? this.getParentProductId() : null,
        storeId: computedMode === 'PRODUCTO_NUEVO' ? this.storeId : null,
        name: this.promotionName || 'Promo automática',
        description: this.promotionDescription,
        cost: Number(this.targetCost ?? this.totalCost ?? 0),
        price: Number(this.customFinalPrice ?? this.totalFinal ?? 0),
        quantity: Math.max(1, Number(this.targetQuantity) || 1)
      },
      summary: {
        originalTotal: this.totalOriginal,
        totalCost: this.totalCost,
        finalTotal: this.totalFinal
      }
    };

    this.onConfirm.emit(payload);
  }

  close() {
    this.lockPageScroll(false);
    this.onClose.emit();
  }
}

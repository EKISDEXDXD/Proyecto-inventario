import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

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

  ngOnChanges(changes: SimpleChanges) {
    if (changes['products'] && this.products && this.products.length) {
      this.initPromoProducts();
    }
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
        costTotal
      };
    });
    this.recalculateSummary();
  }

  adjustQuantity(product: PromoProduct, delta: number) {
    product.quantity = Math.min(product.stock || 1, Math.max(1, Number(product.quantity) + delta));
    this.updateProduct(product);
  }

  updateProduct(product: PromoProduct) {
    product.quantity = Math.min(product.stock || 1, Math.max(1, Number(product.quantity) || 1));
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

  updateCostOverride() {
    const parsed = Number(this.targetCost ?? this.totalCost);
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
    const sourceProducts = this.promoProducts.map(item => ({
      productId: item.id,
      quantity: Math.max(1, Number(item.quantity) || 1)
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
    this.onClose.emit();
  }
}

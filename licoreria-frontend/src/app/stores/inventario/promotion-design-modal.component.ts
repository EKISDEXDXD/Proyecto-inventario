import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface PromoProduct {
  id: number;
  name: string;
  cost: number;
  price: number;
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
  @Output() onConfirm = new EventEmitter<any>();
  @Output() onClose = new EventEmitter<void>();

  promotionName: string = '';
  promotionDescription: string = '';

  promoProducts: PromoProduct[] = [];
  totalOriginal = 0;
  totalCost = 0;
  totalFinal = 0;
  customFinalPrice: number | null = null;

  ngOnChanges(changes: SimpleChanges) {
    if (changes['products'] && this.products && this.products.length) {
      this.initPromoProducts();
    }
  }

  private initPromoProducts() {
    this.customFinalPrice = null;
    this.promoProducts = this.products.map(product => {
      const price = Number(product.displayPrice ?? product.price ?? 0);
      const cost = Number(product.displayCost ?? product.cost ?? 0);
      const quantity = 1;
      const originalTotal = price * quantity;
      const costTotal = cost * quantity;
      const finalPrice = price * quantity;
      return {
        id: product.id,
        name: product.name,
        cost,
        price,
        quantity,
        finalPrice,
        originalTotal,
        costTotal
      };
    });
    this.recalculateSummary();
  }

  adjustQuantity(product: PromoProduct, delta: number) {
    product.quantity = Math.max(1, Number(product.quantity) + delta);
    this.updateProduct(product);
  }

  updateProduct(product: PromoProduct) {
    product.quantity = Math.max(1, Number(product.quantity) || 1);
    product.originalTotal = product.price * product.quantity;
    product.costTotal = product.cost * product.quantity;
    product.finalPrice = product.price * product.quantity;
    this.recalculateSummary();
  }

  updateFinalPrice() {
    const parsed = Number(this.customFinalPrice ?? this.totalFinal);
    this.totalFinal = Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
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
  }

  confirm() {
    this.onConfirm.emit({
      name: this.promotionName,
      description: this.promotionDescription,
      products: this.promoProducts,
      summary: {
        originalTotal: this.totalOriginal,
        totalCost: this.totalCost,
        finalTotal: this.totalFinal
      }
    });
  }

  close() {
    this.onClose.emit();
  }
}

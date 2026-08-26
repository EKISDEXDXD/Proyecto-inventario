import { CommonModule } from '@angular/common';
import { DOCUMENT } from '@angular/common';
import { Component, EventEmitter, Inject, Input, OnDestroy, OnInit, Output, Renderer2 } from '@angular/core';
import { CurrencyFormatPipe } from '../../pipes/currency-format.pipe';
import { LotesModalComponent } from './lotes-modal.component';

@Component({
  selector: 'app-description-modal',
  standalone: true,
  imports: [CommonModule, CurrencyFormatPipe, LotesModalComponent],
  template: `
    <div *ngIf="product" class="description-modal-overlay" (click)="closed.emit()">
      <div class="description-modal" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3 class="modal-title"><i class="bx bx-info-circle"></i> {{ product.name }}</h3>
          <button class="icon-btn small" type="button" (click)="closed.emit()" aria-label="Cerrar"><i class="bx bx-x"></i></button>
        </div>
        <div class="modal-body">
          <div class="lotes-header" *ngIf="!product.parentId">
            <div class="lotes-heading"><i class="bx bx-layer"></i><span>Presentaciones y lotes</span></div>
            <button class="btn-create-lote" type="button" (click)="showLotes = true"><i class="bx bx-cog"></i> Gestionar Lotes</button>
          </div>
          <div class="modal-section">
            <h4>Descripción</h4>
            <p class="modal-description">{{ product.description || 'Este producto no tiene una descripción registrada.' }}</p>
          </div>
          <div class="modal-info-grid">
            <div class="modal-info-item"><span class="modal-label">Stock Actual</span><span class="modal-value">{{ displayStock }}</span></div>
            <div class="modal-info-item"><span class="modal-label">Costo</span><span class="modal-value">{{ product.cost | currencyFormat }}</span></div>
            <div class="modal-info-item"><span class="modal-label">Precio de Venta</span><span class="modal-value">{{ product.price | currencyFormat }}</span></div>
            <div class="modal-info-item"><span class="modal-label">Estado</span><span class="modal-value"><span class="stock-badge" [ngClass]="'stock-' + stockStatus">{{ stockLabel }}</span></span></div>
          </div>
          <div class="margin-section" *ngIf="product.price > product.cost">
            <div class="form-group info-box"><label>Margen de Ganancia</label><div class="margin-display"><span class="margin-value">{{ margin }}%</span></div></div>
          </div>
        </div>
        <div class="modal-footer"><button class="btn btn-secondary" type="button" (click)="closed.emit()">Cerrar</button></div>
      </div>
    </div>
    <app-lotes-modal *ngIf="showLotes" [isOpen]="showLotes" [mainProduct]="product" [storeId]="storeId" (close)="closeLotes()" (lotesUpdated)="handleLotesUpdated()" (editLote)="editLote.emit($event)"></app-lotes-modal>
  `,
  styles: [`
    :host { position: fixed; inset: 0; z-index: 2100; display: block; pointer-events: none; }
    :host-context(body.description-modal-open) { overflow: hidden; }
    .description-modal-overlay { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; overflow-y: auto; z-index: 2100; padding: 1rem; background: rgba(15, 25, 40, .25); isolation: isolate; animation: descriptionFadeIn .2s ease-out; pointer-events: auto; }
    :host app-lotes-modal { position: fixed; inset: 0; z-index: 2200; display: block; pointer-events: auto; }
    .description-modal { width: 100%; max-width: 450px; overflow: hidden; border-radius: 16px; background: #f8fafc; box-shadow: 0 20px 60px rgba(0, 0, 0, .3); animation: descriptionSlideUp .3s ease-out; }
    .modal-header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .75rem 1rem; border-bottom: 1px solid #e2e8f0; background: linear-gradient(135deg, rgba(99, 102, 241, .05) 0%, rgba(139, 92, 246, .05) 100%); }
    .modal-title { display: flex; align-items: center; gap: .75rem; margin: 0; color: #0f172a; font-size: 1.1rem; font-weight: 700; }
    .icon-btn { border: 0; background: transparent; color: #0f172a; cursor: pointer; font-size: 1.4rem; }
    .modal-body { max-height: 70vh; overflow-y: auto; padding: 1rem 1rem 1.5rem; color: #0f172a; }.lotes-header { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin: 0 0 1.25rem; padding: .65rem .75rem; border: 1px solid #e0e7ff; border-radius: 10px; background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%); }.lotes-heading { display: inline-flex; align-items: center; gap: .45rem; min-width: 0; color: #64748b; font-size: .75rem; font-weight: 700; letter-spacing: .02em; text-transform: uppercase; }.lotes-heading i { color: #6366f1; font-size: 1.05rem; }.btn-create-lote { display: inline-flex; align-items: center; justify-content: center; gap: .4rem; flex: 0 0 auto; padding: .55rem .85rem; border: 1px solid #c7d2fe; border-radius: 8px; background: #fff; color: #4338ca; font-size: .8rem; font-weight: 700; cursor: pointer; box-shadow: 0 3px 8px rgba(79, 70, 229, .08); transition: transform .2s ease, box-shadow .2s ease, background .2s ease; }.btn-create-lote:hover { background: #eef2ff; transform: translateY(-1px); box-shadow: 0 7px 14px rgba(79, 70, 229, .15); }.btn-create-lote:active { transform: translateY(0) scale(.98); }
    .modal-section { margin-bottom: 1rem; }.modal-section h4 { margin: 0 0 .75rem; color: #64748b; font-size: .95rem; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; }
    .modal-description { margin: 0 0 1.25rem; color: #0f172a; font-size: 1rem; line-height: 1.6; word-break: break-word; }
    .modal-info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; }
    .modal-info-item { display: grid; gap: .35rem; padding: .85rem; border: 1px solid #e8ecf1; border-radius: 10px; background: #f8fafc; }
    .modal-label { color: #64748b; font-size: .75rem; font-weight: 700; }
    .modal-value { color: #0f172a; font-size: .95rem; font-weight: 700; }
    .stock-badge { display: inline-block; padding: .3rem .6rem; border-radius: 999px; font-size: .75rem; }
    .stock-normal { background: #d1fae5; color: #047857; }.stock-low { background: #ffedd5; color: #c2410c; }.stock-out { background: #fecaca; color: #b91c1c; }
    .margin-section { margin-top: 1rem; }.info-box { padding: .85rem; border-radius: 10px; background: #eef2ff; }.info-box label { color: #64748b; font-size: .75rem; font-weight: 700; }.margin-display { margin-top: .25rem; }.margin-value { color: #4f46e5; font-size: 1.2rem; font-weight: 800; }
    .modal-footer { display: flex; justify-content: flex-end; padding: 1rem; border-top: 1px solid #e2e8f0; }.btn { padding: .65rem 1.1rem; border: 0; border-radius: 8px; cursor: pointer; font-weight: 700; }.btn-secondary { background: #475569; color: #fff; }
    @keyframes descriptionFadeIn { from { opacity: 0; } to { opacity: 1; } } @keyframes descriptionSlideUp { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
    @media (max-width: 480px) { .modal-header, .modal-body { padding: 1rem; }.modal-info-grid { grid-template-columns: 1fr; }.modal-footer { padding: .85rem 1rem 1rem; }.lotes-header { align-items: stretch; flex-direction: column; gap: .65rem; }.btn-create-lote { width: 100%; } }
  `]
})
export class DescriptionModalComponent implements OnInit, OnDestroy {
  @Input() product: any = null;
  @Input() products: any[] = [];
  @Input() storeId: number | null = null;
  @Output() closed = new EventEmitter<void>();
  @Output() lotesUpdated = new EventEmitter<void>();
  @Output() editLote = new EventEmitter<any>();
  showLotes = false;
  private previousBodyOverflow = '';
  private previousBodyPaddingRight = '';

  constructor(@Inject(DOCUMENT) private document: Document, private renderer: Renderer2) {}

  ngOnInit(): void {
    this.previousBodyOverflow = this.document.body.style.overflow;
    this.previousBodyPaddingRight = this.document.body.style.paddingRight;
    const scrollbarWidth = (this.document.defaultView?.innerWidth ?? 0) - this.document.documentElement.clientWidth;
    this.renderer.setStyle(this.document.body, 'overflow', 'hidden');
    if (scrollbarWidth > 0) this.renderer.setStyle(this.document.body, 'padding-right', `${scrollbarWidth}px`);
    this.renderer.addClass(this.document.body, 'description-modal-open');
  }

  ngOnDestroy(): void {
    this.renderer.setStyle(this.document.body, 'overflow', this.previousBodyOverflow);
    this.renderer.setStyle(this.document.body, 'padding-right', this.previousBodyPaddingRight);
    this.renderer.removeClass(this.document.body, 'description-modal-open');
  }

  get displayStock(): number {
    if (!this.product) return 0;
    if (this.product.displayStock !== undefined) return Number(this.product.displayStock);
    if (this.product.parentId) return Number(this.product.stock ?? 0);

    const rootStock = Number(this.product.stock ?? 0);
    const lotes = this.products.filter(item => item.parentId === this.product.id && (item.isActive ?? true) && !(item.isDeleted ?? false));
    return lotes.reduce((total, lote) => total + Number(lote.stock ?? 0), rootStock);
  }

  closeLotes(): void { this.showLotes = false; }
  handleLotesUpdated(): void { this.lotesUpdated.emit(); }

  get stockStatus(): 'normal' | 'low' | 'out' {
    if (this.displayStock === 0) return 'out';
    return this.displayStock <= 10 ? 'low' : 'normal';
  }

  get stockLabel(): string { return this.stockStatus === 'normal' ? 'Normal' : this.stockStatus === 'low' ? 'Bajo' : 'Sin Stock'; }
  get margin(): string { return this.product?.cost ? (((this.product.price - this.product.cost) / this.product.cost) * 100).toFixed(2) : '0.00'; }
}

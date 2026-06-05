import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CurrencyFormatPipe } from '../../pipes/currency-format.pipe';

@Component({
  selector: 'app-lote-detail-modal',
  standalone: true,
  imports: [CommonModule, CurrencyFormatPipe],
  template: `
    <div *ngIf="isOpen" class="lote-detail-modal-overlay" (click)="onClose()">
      <div class="lote-detail-modal" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3 class="modal-title">
            <i class="bx bx-detail"></i> Detalles del Lote
          </h3>
          <div class="modal-header-actions">
            <button class="icon-btn small" type="button" (click)="onClose()" aria-label="Cerrar">
              <i class="bx bx-x"></i>
            </button>
          </div>
        </div>

        <div class="modal-body" *ngIf="lote">
          <!-- Información Principal -->
          <div class="modal-section">
            <h4>Información General</h4>
            <div class="modal-info-grid">
              <div class="modal-info-item">
                <span class="modal-label">Nombre</span>
                <span class="modal-value">{{ lote.name }}</span>
              </div>
              <div class="modal-info-item">
                <span class="modal-label">Estado</span>
                <span class="modal-value" [ngClass]="{'status-active': lote.isActive, 'status-inactive': !lote.isActive}">
                  {{ lote.isActive ? '✓ Activo' : 'Inactivo' }}
                </span>
              </div>
            </div>
          </div>

          <!-- Detalles Económicos -->
          <div class="modal-section">
            <h4>Datos Económicos</h4>
            <div class="modal-info-grid">
              <div class="modal-info-item">
                <span class="modal-label">Costo Unitario</span>
                <span class="modal-value">{{ lote.cost | currencyFormat }}</span>
              </div>
              <div class="modal-info-item">
                <span class="modal-label">Precio Unitario</span>
                <span class="modal-value">{{ lote.price | currencyFormat }}</span>
              </div>
              <div class="modal-info-item">
                <span class="modal-label">Margen (%)</span>
                <span class="modal-value margin-badge">{{ calculateMargin(lote.cost, lote.price) }}%</span>
              </div>
              <div class="modal-info-item">
                <span class="modal-label">Stock Actual</span>
                <span class="modal-value" [ngClass]="{'stock-low': lote.stock < 10, 'stock-out': lote.stock === 0, 'stock-normal': lote.stock >= 10}">
                  {{ lote.stock }} unidades
                </span>
              </div>
            </div>
          </div>

          <!-- Descripción -->
          <div class="modal-section" *ngIf="lote.description">
            <h4>Descripción</h4>
            <p class="modal-description">{{ lote.description }}</p>
          </div>

          <!-- Información Adicional -->
          <div class="modal-section">
            <h4>Información Técnica</h4>
            <div class="modal-info-grid">
              <div class="modal-info-item">
                <span class="modal-label">ID del Lote</span>
                <span class="modal-value small-text">#{{ lote.id }}</span>
              </div>
              <div class="modal-info-item">
                <span class="modal-label">Orden del Lote</span>
                <span class="modal-value">{{ lote.orderIndex }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-secondary" (click)="onClose()">Cerrar</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      --color-primary: #6366f1;
      --bg-surface: #ffffff;
      --bg-input: #ffffff;
      --text-main: #0f172a;
      --text-muted: #64748b;
      --border-color: #e2e8f0;
      --color-success: #10b981;
      --color-warning: #f59e0b;
      --color-danger: #ef4444;
      --radius-lg: 16px;
      --radius-md: 8px;
      --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
    }

    :host-context(.dark) {
      --bg-surface: #0f0f1a;
      --bg-input: #0f172a;
      --text-main: #f8fafc;
      --text-muted: #94a3b8;
      --border-color: #334155;
    }

    .lote-detail-modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(15, 25, 40, 0.35);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1004;
      padding: 1rem;
      animation: fadeIn 0.2s ease-out;
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    .lote-detail-modal {
      background-color: var(--bg-surface);
      border-radius: var(--radius-lg);
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      max-width: 500px;
      width: 100%;
      overflow: hidden;
      animation: slideUp 0.3s ease-out;
    }

    @keyframes slideUp {
      from {
        transform: translateY(30px);
        opacity: 0;
      }
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      padding: 1rem;
      border-bottom: 1px solid var(--border-color);
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.05) 0%, rgba(139, 92, 246, 0.05) 100%);
    }

    .modal-title {
      margin: 0;
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--text-main);
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .modal-header-actions {
      display: flex;
      gap: 0.5rem;
    }

    .icon-btn {
      background: none;
      border: none;
      color: var(--text-main);
      cursor: pointer;
      padding: 0.5rem;
      border-radius: 50%;
      transition: all 0.2s;
      font-size: 1.25rem;
    }

    .icon-btn:hover {
      background-color: rgba(99, 102, 241, 0.1);
      color: var(--color-primary);
    }

    .modal-body {
      padding: 1.5rem;
      max-height: 70vh;
      overflow-y: auto;
    }

    .modal-section {
      margin-bottom: 2rem;
    }

    .modal-section:last-child {
      margin-bottom: 0;
    }

    .modal-section h4 {
      margin: 0 0 1rem 0;
      font-size: 0.95rem;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .modal-info-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 1rem;
    }

    .modal-info-item {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      padding: 1rem;
      background-color: var(--bg-input);
      border-radius: var(--radius-md);
      border: 1px solid var(--border-color);
    }

    .modal-label {
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.02em;
    }

    .modal-value {
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--text-main);
    }

    .modal-value.small-text {
      font-size: 0.9rem;
      font-family: 'Monaco', 'Courier New', monospace;
    }

    .status-active {
      color: var(--color-success);
    }

    .status-inactive {
      color: var(--color-warning);
    }

    .stock-low {
      color: var(--color-warning);
    }

    .stock-out {
      color: var(--color-danger);
    }

    .stock-normal {
      color: var(--color-success);
    }

    .margin-badge {
      display: inline-block;
      background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-hover) 100%);
      color: white;
      padding: 0.25rem 0.75rem;
      border-radius: 20px;
      font-size: 0.95rem;
    }

    .modal-description {
      margin: 0;
      font-size: 1rem;
      line-height: 1.6;
      color: var(--text-main);
      word-break: break-word;
    }

    .modal-footer {
      display: flex;
      gap: 0.75rem;
      padding: 1rem 1.5rem;
      border-top: 1px solid var(--border-color);
      justify-content: flex-end;
    }

    .btn {
      padding: 0.75rem 1.5rem;
      border: none;
      border-radius: var(--radius-md);
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s ease;
      font-size: 0.95rem;
    }

    .btn-secondary {
      background-color: var(--border-color);
      color: var(--text-main);
    }

    .btn-secondary:hover {
      background-color: #cbd5e1;
    }
  `]
})
export class LoteDetailModalComponent {
  @Input() isOpen: boolean = false;
  @Input() lote: any = null;
  @Output() close = new EventEmitter<void>();

  calculateMargin(cost: number, price: number): number {
    if (cost === 0) return 0;
    return Math.round(((price - cost) / cost) * 100);
  }

  onClose() {
    this.close.emit();
  }
}

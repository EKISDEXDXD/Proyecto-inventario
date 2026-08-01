import { Component, EventEmitter, Input, Output, OnInit, NgZone, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PaymentMethodConfigService, PaymentMethodConfig } from '../../settings/payment-method-config.service';

@Component({
  selector: 'app-payment-method-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="modal-overlay" *ngIf="isOpen" (click)="close()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h2><i class="bx bx-credit-card"></i> Método de Pago</h2>
          <button class="close-btn" (click)="close()" title="Cerrar">
            <i class="bx bx-x"></i>
          </button>
        </div>
        
        <div class="modal-body">
          <p class="modal-description">Selecciona la forma de pago utilizada:</p>
          
          <!-- Cargando -->
          <div *ngIf="loading" class="loading-state">
            <i class="bx bx-loader-alt bx-spin"></i>
            <p>Cargando métodos de pago...</p>
          </div>

          <!-- Error -->
          <div *ngIf="error && !loading" class="error-state">
            <i class="bx bx-error-circle"></i>
            <p>{{ error }}</p>
            <button class="btn-retry" (click)="loadPaymentMethods()">
              <i class="bx bx-refresh"></i> Reintentar
            </button>
          </div>

          <!-- Métodos de pago -->
          <div *ngIf="!loading && !error && paymentMethods.length > 0" class="payment-options">
            <button 
              *ngFor="let method of paymentMethods"
              class="payment-btn"
              [ngClass]="'type-' + method.type.toLowerCase()"
              (click)="selectPaymentMethod(method)"
              [class.selected]="selectedMethod?.id === method.id">
              <div class="payment-icon">
                <i *ngIf="method.type === 'EFECTIVO'" class="bx bx-money"></i>
                <i *ngIf="method.type === 'QR'" class="bx bx-qr"></i>
              </div>
              <span class="payment-label">{{ method.type === 'QR' ? 'Efectivo' : method.name }}</span>
            </button>
          </div>

          <!-- Sin métodos -->
          <div *ngIf="!loading && paymentMethods.length === 0 && !error" class="empty-state">
            <i class="bx bx-inbox"></i>
            <p>No hay métodos de pago configurados</p>
          </div>
        </div>
        
        <div class="modal-footer">
          <button class="btn-cancel" (click)="close()">Cancelar</button>
          <button 
            class="btn-confirm" 
            (click)="confirm()" 
            [disabled]="!selectedMethod || loading">
            Confirmar
          </button>
        </div>
      </div>
    </div>

    <!-- Modal de imagen QR -->
    <div class="image-modal-overlay" *ngIf="showImageModal" (click)="closeImageModal()">
      <div class="image-modal-content" (click)="$event.stopPropagation()">
        <button class="image-close-btn" (click)="closeImageModal()" title="Cerrar">
          <i class="bx bx-x"></i>
        </button>
        <div class="image-container">
          <img *ngIf="selectedMethod?.imageUrl" [src]="selectedMethod?.imageUrl" [alt]="selectedMethod?.name" class="qr-image">
        </div>
        <div class="image-modal-footer">
          <button class="btn-cancel" (click)="closeImageModal()">Cancelar</button>
          <button class="btn-confirm" (click)="confirmFromImageModal()">
            <i class="bx bx-check"></i> Confirmar
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      --color-primary: #4f46e5;
      --color-success: #10b981;
      --bg-body: #ffffff;
      --bg-surface: #f8fafc;
      --text-main: #0f172a;
      --text-muted: #64748b;
      --border-color: #e2e8f0;
      --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
    }

    :host-context(.dark) {
      --bg-body: #0f0f1a;
      --bg-surface: #1e293b;
      --text-main: #f8fafc;
      --text-muted: #94a3b8;
      --border-color: #334155;
      --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.4);
    }

    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
      animation: fadeIn 0.2s ease-in-out;
    }

    @keyframes fadeIn {
      from {
        opacity: 0;
      }
      to {
        opacity: 1;
      }
    }

    .modal-content {
      background-color: var(--bg-body);
      border-radius: 12px;
      box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
      width: 90%;
      max-width: 500px;
      max-height: 80vh;
      overflow-y: auto;
      animation: slideUp 0.3s ease-out;
    }

    @keyframes slideUp {
      from {
        opacity: 0;
        transform: translateY(20px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem;
      border-bottom: 1px solid var(--border-color);
    }

    .modal-header h2 {
      margin: 0;
      font-size: 1.25rem;
      color: var(--text-main);
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .close-btn {
      background: none;
      border: none;
      font-size: 1.5rem;
      color: var(--text-muted);
      cursor: pointer;
      padding: 0.25rem;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px;
      transition: all 0.2s;
    }

    .close-btn:hover {
      color: var(--text-main);
      transform: scale(1.1);
    }

    .modal-body {
      padding: 1rem 1rem;
    }

    .modal-description {
      margin: 0 0 1.5rem 0;
      color: var(--text-muted);
      font-weight: 500;
      font-size: 0.95rem;
    }

    .payment-options {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 1rem;
      margin-bottom: 1rem;
    }

    .payment-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      padding: 1.5rem;
      background-color: var(--bg-body);
      border: 2px solid var(--border-color);
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.3s ease;
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--text-main);
    }

    .payment-btn:hover:not(:disabled) {
      border-color: var(--color-primary);
      background-color: var(--bg-surface);
      transform: translateY(-2px);
    }

    .payment-btn.selected {
      border-color: var(--color-primary);
      background: linear-gradient(135deg, rgba(79, 70, 229, 0.1) 0%, rgba(79, 70, 229, 0.05) 100%);
      box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
    }

    .payment-btn.type-efectivo.selected {
      border-color: #10b981;
      background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(16, 185, 129, 0.05) 100%);
      box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
    }

    .payment-btn.type-qr.selected {
      border-color: #4f46e5;
      background: linear-gradient(135deg, rgba(79, 70, 229, 0.1) 0%, rgba(79, 70, 229, 0.05) 100%);
      box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
    }

    .payment-icon {
      font-size: 2.5rem;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--color-primary);
    }

    .payment-btn.type-efectivo .payment-icon {
      color: #10b981;
    }

    .payment-btn.type-qr .payment-icon {
      color: #4f46e5;
    }

    .payment-btn.selected .payment-icon {
      animation: bounce 0.4s ease;
    }

    @keyframes bounce {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.1); }
    }

    .payment-label {
      display: block;
      margin-top: 0.5rem;
      word-break: break-word;
    }

    .payment-type-badge {
      display: flex;
      align-items: center;
      justify-content: center;
      margin-top: 0.5rem;
      width: 50px;
      height: 50px;
      background: var(--bg-surface);
      border-radius: 6px;
      padding: 0.25rem;
    }

    .qr-thumb {
      width: 100%;
      height: 100%;
      object-fit: contain;
      border-radius: 4px;
    }

    .loading-state,
    .error-state,
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      padding: 2rem;
      color: var(--text-muted);
      text-align: center;
    }

    .loading-state i,
    .error-state i,
    .empty-state i {
      font-size: 2.5rem;
      opacity: 0.5;
    }

    .loading-state i {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .btn-retry {
      padding: 0.75rem 1rem;
      background: var(--color-primary);
      color: white;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .btn-retry:hover {
      background: #4338ca;
    }

    .modal-footer {
      display: flex;
      gap: 1rem;
      padding: 1rem;
      border-top: 1px solid var(--border-color);
      justify-content: flex-end;
    }

    .btn-cancel,
    .btn-confirm {
      padding: 0.75rem 1.5rem;
      border-radius: 6px;
      border: none;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      font-size: 0.95rem;
    }

    .btn-cancel {
      background-color: var(--bg-body);
      color: var(--text-main);
      border: 1px solid var(--border-color);
    }

    .btn-cancel:hover {
      background-color: var(--bg-surface);
    }

    .btn-confirm {
      background-color: var(--color-primary);
      color: white;
    }

    .btn-confirm:hover:not(:disabled) {
      background-color: #4338ca;
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(79, 70, 229, 0.4);
    }

    .btn-confirm:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    /* Modal de Imagen QR */
    .image-modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(0, 0, 0, 0.8);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10000;
      animation: fadeIn 0.2s ease-in-out;
    }

    .image-modal-content {
      background-color: var(--bg-body);
      border-radius: 12px;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
      width: 90%;
      max-width: 600px;
      max-height: 85vh;
      overflow: auto;
      animation: slideUp 0.3s ease-out;
      display: flex;
      flex-direction: column;
      position: relative;
    }

    .image-close-btn {
      position: absolute;
      top: 1rem;
      right: 1rem;
      background-color: rgba(0, 0, 0, 0.5);
      color: white;
      border: none;
      border-radius: 50%;
      width: 40px;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-size: 1.5rem;
      transition: all 0.2s;
      z-index: 10001;
    }

    .image-close-btn:hover {
      background-color: rgba(0, 0, 0, 0.8);
    }

    .image-container {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2rem;
      background-color: var(--bg-surface);
    }

    .qr-image {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
    }

    .image-modal-footer {
      display: flex;
      gap: 1rem;
      padding: 1.5rem;
      border-top: 1px solid var(--border-color);
      justify-content: flex-end;
    }
  `]
})
export class PaymentMethodModalComponent implements OnInit {
  @Input() storeId: number | null = null;
  @Output() paymentMethodConfigIdSelected = new EventEmitter<number>();
  @Output() closed = new EventEmitter<void>();

  isOpen = false;
  selectedMethod: PaymentMethodConfig | null = null;
  paymentMethods: PaymentMethodConfig[] = [];
  loading = false;
  error: string | null = null;
  showImageModal = false;

  constructor(
    private paymentMethodConfigService: PaymentMethodConfigService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    // Initialization happens in open()
  }

  open() {
    this.isOpen = true;
    this.selectedMethod = null;
    this.error = null;
    this.loadPaymentMethods();
  }

  loadPaymentMethods() {
    this.loading = true;
    this.cdr.markForCheck();
    this.paymentMethodConfigService.getAllActive().subscribe({
      next: (methods) => {
        this.ngZone.run(() => {
          this.paymentMethods = methods;
          this.loading = false;
          this.error = null;
          this.cdr.markForCheck();
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.loading = false;
          this.error = 'Error al cargar los métodos de pago';
          this.cdr.markForCheck();
          console.error('Error loading payment methods:', err);
        });
      }
    });
  }

  close() {
    this.isOpen = false;
    this.showImageModal = false;
    this.selectedMethod = null;
    this.closed.emit();
  }

  selectPaymentMethod(method: PaymentMethodConfig) {
    this.selectedMethod = method;

    // Tratar QR igual que EFECTIVO: confirmar automáticamente
    if (method.type === 'EFECTIVO' || method.type === 'QR') {
      // Usar runOutsideAngular para evitar ExpressionChangedAfterItHasBeenCheckedError
      this.ngZone.runOutsideAngular(() => {
        setTimeout(() => {
          this.ngZone.run(() => {
            this.confirm();
          });
        }, 200);
      });
    }
  }

  closeImageModal() {
    this.showImageModal = false;
    this.cdr.markForCheck();
  }

  confirmFromImageModal() {
    this.showImageModal = false;
    this.confirm();
  }

  confirm() {
    if (this.selectedMethod) {
      this.paymentMethodConfigIdSelected.emit(this.selectedMethod.id);
      this.close();
    }
  }
}

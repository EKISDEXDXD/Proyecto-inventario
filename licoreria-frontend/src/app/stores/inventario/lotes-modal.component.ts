import { Component, Input, Output, EventEmitter, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CurrencyFormatPipe } from '../../pipes/currency-format.pipe';
import { LotesService } from '../../services/lotes.service';
import { LoteDetailModalComponent } from './lote-detail-modal.component';
import { ModalStackService } from './modal-stack.service';

@Component({
  selector: 'app-lotes-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, CurrencyFormatPipe, LoteDetailModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lotes-modal.component.html',
  styleUrl: './lotes-modal.component.css'
})
export class LotesModalComponent implements OnInit, OnChanges {
  @Input() isOpen: boolean = false;
  @Input() mainProduct: any = null;
  @Input() storeId: number | null = null;
  @Output() close = new EventEmitter<void>();
  @Output() lotesUpdated = new EventEmitter<void>();
  @Output() editLote = new EventEmitter<any>();

  lotes: any[] = [];
  loading: boolean = false;
  showCreateForm: boolean = false;
  showLoteDetailModal: boolean = false;
  selectedLoteForDetail: any = null;
  overlayZIndex = 2000;
  private previousMainProductId: any = null;

  newLoteForm = {
    name: '',
    description: '',
    cost: 0,
    price: 0,
    stock: 0
  };

  constructor(
    private lotesService: LotesService,
    private cdr: ChangeDetectorRef,
    private modalStackService: ModalStackService
  ) {}

  ngOnInit() {}

  ngOnChanges(changes: SimpleChanges) {
    // Detectar cambios en isOpen o mainProduct
    if (changes['isOpen'] && this.isOpen && this.mainProduct) {
      this.bringToFront();
      this.loadLotes();
    } else if (changes['mainProduct'] && this.mainProduct && this.mainProduct.id !== this.previousMainProductId) {
      this.previousMainProductId = this.mainProduct.id;
      if (this.isOpen) {
        this.loadLotes();
      }
    }
  }

  bringToFront() {
    this.overlayZIndex = this.modalStackService.bringToFront();
  }

  loadLotes() {
    if (!this.mainProduct || !this.mainProduct.id) {
      console.warn('⚠️ No mainProduct or mainProduct.id');
      return;
    }
    
    this.loading = true;
    console.log('📥 Cargando lotes para producto ID:', this.mainProduct.id);
    
    this.lotesService.getLotesByProductId(this.mainProduct.id).subscribe({
      next: (lotes) => {
        console.log('✅ Lotes cargados del servidor:', lotes?.length || 0);
        console.log('   Contenido:', lotes);
        
        const serverLotes = Array.isArray(lotes) ? lotes : [];
        const allLotes = [
          ...(this.mainProduct && this.mainProduct.id ? [{ ...this.mainProduct, parentId: null }] : []),
          ...serverLotes.map(lote => ({ ...lote, parentId: lote.parentId ?? this.mainProduct?.id }))
        ];
        console.log('📦 Lista de lotes preparada. Total:', allLotes.length);

        this.lotes = allLotes.filter(lote => lote && lote.isActive === true);
        console.log('✅ Lotes activos mostrados:', this.lotes.length);
        
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('❌ Error cargando lotes:', err);
        this.loading = false;
        this.lotes = [];
        this.cdr.markForCheck();
      }
    });
  }

  viewLoteDetails(lote: any) {
    this.selectedLoteForDetail = lote;
    this.showLoteDetailModal = true;
    this.cdr.markForCheck();
  }

  handleEditLote(lote: any) {
    if (!lote) {
      return;
    }
    this.showLoteDetailModal = false;
    this.editLote.emit(lote);
    this.cdr.markForCheck();
  }

  activateLote(loteId: number) {
    this.loading = true;
    console.log('✨ Activando lote con ID:', loteId);
    
    // Encontrar el lote a activar
    const loteToActivate = this.lotes.find(l => l.id === loteId);
    if (!loteToActivate) {
      this.loading = false;
      return;
    }
    
    this.lotesService.activateLote(loteId).subscribe({
      next: (updatedLote) => {
        console.log('✅ Lote activado:', updatedLote);

        const index = this.lotes.findIndex(l => l.id === loteId);
        if (index !== -1) {
          this.lotes[index] = { ...this.lotes[index], ...updatedLote };
        }

        this.loading = false;
        this.lotesUpdated.emit();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('❌ Error activando lote:', err);
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  toggleActiveForSale(lote: any) {
    if (!lote || !lote.id) {
      console.warn('Lote inválido:', lote);
      return;
    }

    try {
      const newState = !lote.isActiveForSale;
      const action = newState ? 'activar para venta' : 'desactivar de venta';
      
      // GUARDAR ESTADOS ANTERIORES COMPLETOS para reversión exacta
      const previousStates = new Map<number, boolean>();
      this.lotes.forEach(l => {
        previousStates.set(l.id, l.isActiveForSale);
      });
      
      this.loading = true;
      console.log(`🔄 Cambiando isActiveForSale a ${newState} para lote ID:`, lote.id);
      
      // Validación: Si intenta desactivar el único activo, impedir
      if (!newState) {
        const activeCount = this.lotes.filter(l => l.isActiveForSale).length;
        if (activeCount === 1 && lote.isActiveForSale) {
          console.warn('⚠️ No puede desactivar el único lote activo. El backend lo reemplazará automáticamente.');
          // Permitir que el backend maneje la lógica de auto-activación
        }
      }
      
      // Actualizar inmediatamente en la UI
      lote.isActiveForSale = newState;
      
      // Si se activa, desactivar los demás lotes (el backend también lo hará)
      if (newState) {
        this.lotes.forEach(l => {
          if (l.id !== lote.id && l.isActiveForSale) {
            l.isActiveForSale = false;
            console.log(`  → Desactivando lote hermano ID: ${l.id}`);
          }
        });
      }
      
      this.lotesService.setActiveForSale(lote.id, newState).subscribe({
        next: (updatedLote) => {
          console.log(`✅ isActiveForSale actualizado a ${newState}:`, updatedLote);
          
          // Sincronizar cambios del servidor
          const index = this.lotes.findIndex(l => l.id === lote.id);
          if (index !== -1) {
            this.lotes[index] = { ...this.lotes[index], ...updatedLote };
          }
          
          // Si se DESACTIVÓ y era el ÚNICO activo,
          // el backend auto-activó el padre. Sincronizar localmente.
          if (!newState) {
            const otherActiveCount = this.lotes.filter(l => l.id !== lote.id && l.isActiveForSale).length;
            if (otherActiveCount === 0 && this.mainProduct) {
              // El padre debería estar activo automáticamente
              this.mainProduct.isActiveForSale = true;
              console.log('✨ Padre auto-activado por backend. Sincronizando UI...');
              
              // Actualizar en la lista si ya está ahí
              const mainProductIndex = this.lotes.findIndex(l => l.id === this.mainProduct.id);
              if (mainProductIndex !== -1) {
                this.lotes[mainProductIndex].isActiveForSale = true;
              }
            }
          }
          
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error(`❌ Error al ${action}:`, err);
          
          // REVERSIÓN EXACTA: Restaurar todos los estados anteriores
          console.log('🔙 Revirtiendo a estados anteriores...');
          this.lotes.forEach(l => {
            const previousState = previousStates.get(l.id);
            if (previousState !== undefined) {
              l.isActiveForSale = previousState;
              console.log(`  → Lote ID ${l.id} restaurado a isActiveForSale=${previousState}`);
            }
          });
          
          this.loading = false;
          this.cdr.markForCheck();
        }
      });
    } catch (error) {
      console.error('Error inesperado en toggleActiveForSale:', error);
      this.loading = false;
    }
  }

  deleteLote(loteId: number) {
    this.loading = true;
    console.log('🗑️ Desactivando lote con ID:', loteId);
    
    // Actualizar inmediatamente en la UI - marcar como inactivo
    const indexToDelete = this.lotes.findIndex(l => l.id === loteId);
    const loteToDelete = this.lotes[indexToDelete];
    
    if (indexToDelete !== -1) {
      loteToDelete.isActive = false;
    }
    
    this.lotesService.deleteLote(loteId).subscribe({
      next: (response) => {
        console.log('✅ Lote desactivado:', response);
        
        // Remover del array UI (ya está marcado como inactivo)
        if (indexToDelete !== -1) {
          this.lotes.splice(indexToDelete, 1);
        }
        
        this.loading = false;
        this.lotesUpdated.emit();
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('❌ Error:', error);
        
        // Revertir cambios si hay error
        if (indexToDelete !== -1) {
          loteToDelete.isActive = true;
        }
        
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  toggleCreateForm() {
    this.showCreateForm = !this.showCreateForm;
    this.cdr.markForCheck();
  }

  isFormValid(): boolean {
    return this.newLoteForm.name.trim().length > 0 &&
           this.newLoteForm.cost > 0 &&
           this.newLoteForm.price > 0 &&
           this.newLoteForm.stock >= 0;
  }

  saveNewLote() {
    if (!this.isFormValid()) {
      alert('Por favor completa todos los campos correctamente');
      return;
    }

    this.loading = true;
    const loteName = this.newLoteForm.name.trim();
    const loteData = {
      name: loteName,
      description: this.newLoteForm.description,
      cost: this.newLoteForm.cost,
      price: this.newLoteForm.price,
      stock: 0
    };

    console.log('📤 Enviando lote con datos:', loteData);
    console.log('   Product ID:', this.mainProduct.id);
    console.log('   Store ID:', this.storeId || this.mainProduct?.storeId);

    this.lotesService.createLote(this.mainProduct.id, loteData).subscribe({
      next: (newLote) => {
        console.log('✅ Lote creado:', newLote);
        const loteParaMostrar = {
          ...newLote,
          name: newLote?.name?.trim() || loteName,
          displayName: newLote?.name?.trim() || loteName
        };
        this.lotes.push(loteParaMostrar);
        this.resetCreateForm();
        this.loading = false;
        this.lotesUpdated.emit();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.loading = false;
        console.error('❌ Error creando lote:', err);
        console.error('   Status:', err.status);
        console.error('   Error details:', err.error);
        
        let message = 'Error al crear el lote: ' + (err.error?.message || 'Error desconocido');
        alert('❌ ' + message);
        this.cdr.markForCheck();
      }
    });
  }

  cancelCreateForm() {
    this.resetCreateForm();
    this.cdr.markForCheck();
  }

  resetCreateForm() {
    this.newLoteForm = {
      name: '',
      description: '',
      cost: 0,
      price: 0,
      stock: 0
    };
    this.showCreateForm = false;
  }

  calculateMargin(cost: number, price: number): number {
    if (cost === 0) return 0;
    return Math.round(((price - cost) / cost) * 100);
  }

  getLoteDisplayName(lote: any): string {
    if (!lote) {
      return 'Sin nombre';
    }

    const candidateName = lote.name || lote.displayName || '';
    if (candidateName && candidateName.trim()) {
      return candidateName.trim();
    }

    if (lote.parentId) {
      return 'Lote sin nombre';
    }

    return this.mainProduct?.name || 'Producto sin nombre';
  }

  onClose() {
    this.close.emit();
  }
}

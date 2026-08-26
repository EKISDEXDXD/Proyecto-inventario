import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, ChangeDetectorRef, ChangeDetectionStrategy, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TagService } from '../../core/tag.service';
import { ApiConfigService } from '../../auth/api-config.service';
import { CurrencyService } from '../../services/currency.service';
import { CurrencyFormatPipe } from '../../pipes/currency-format.pipe';
import { LotesService } from '../../services/lotes.service';
import { PromotionDesignModalComponent } from './promotion-design-modal.component';
import { Subject, of, firstValueFrom } from 'rxjs';
import { takeUntil, debounceTime, distinctUntilChanged, catchError } from 'rxjs/operators';

@Component({
  selector: 'app-product-gallery-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, CurrencyFormatPipe, PromotionDesignModalComponent],
  templateUrl: './product-gallery-modal.component.html',
  styleUrls: ['./product-gallery-modal.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProductGalleryModalComponent implements OnInit, OnChanges, OnDestroy {
  @Input() isOpen: boolean = false;
  @Input() storeId: number = 0;
  @Output() onClose = new EventEmitter<void>();
  @Output() onProductSelect = new EventEmitter<any>();

  // Products
  products: any[] = [];
  displayedProducts: any[] = [];
  loading: boolean = true;
  productsError: string | null = null;
  currentPage: number = 0;
  pageSize: number = 20;
  totalElements: number = 0;
  hasMorePages: boolean = false;

  // Search & Filter
  searchQuery: string = '';
  selectedTagIds: number[] = [];
  private searchSubject = new Subject<string>();
  private destroy$ = new Subject<void>();

  // Tags
  allTags: any[] = [];
  loadingTags: boolean = true;
  tagsError: string | null = null;

  // New Tag Form
  showNewTagForm: boolean = false;
  newTagName: string = '';
  creatingTag: boolean = false;

  // UI
  isScrolling: boolean = false;
  overlayZIndex = 900;

  // Drag & Drop
  draggedTag: any = null;
  dragOverProductId: number | null = null;
  assigningTag: boolean = false;
  draggedProduct: any = null;
  promoMode: boolean = false;
  promoProducts: any[] = [];
  promoConfigOpen: boolean = false;
  promoDropActive: boolean = false;

  // Touch support for mobile
  touchDraggedTag: any = null;
  isMobile: boolean = false;

  constructor(
    private tagService: TagService,
    private cdr: ChangeDetectorRef,
    private apiConfig: ApiConfigService,
    private currencyService: CurrencyService,
    private lotesService: LotesService
  ) {}

  ngOnInit() {
    // Setup search debounce
    this.searchSubject.pipe(
      debounceTime(500),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.currentPage = 0;
      this.displayedProducts = [];
      this.loadProducts();
    });

    // Close modal on ESC key
    document.addEventListener('keydown', (event: KeyboardEvent) => {
      if (event.key === 'Escape' && this.isOpen) {
        this.close();
      }
    });

    // Detect if mobile device
    this.isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
  }

  ngOnChanges(changes: SimpleChanges) {
    console.log('[Gallery Modal] ngOnChanges llamado - cambios:', changes);
    
    // Cuando el modal se abre, cargar datos
    if (changes['isOpen']) {
      console.log('[Gallery Modal] isOpen cambió. Actual:', this.isOpen, 'storeId:', this.storeId);
      if (this.isOpen) {
        if (this.storeId > 0) {
          this.loadTags();
          this.loadProducts();
        }
      }
    }
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load all tags for this store
   */
  loadTags() {
    this.loadingTags = true;
    this.tagsError = null;
    console.log('[Gallery Modal] Cargando etiquetas para storeId:', this.storeId);
    this.tagService.getTagsByStore(this.storeId).subscribe({
      next: (tags) => {
        console.log('[Gallery Modal] Etiquetas cargadas:', tags);
        this.allTags = tags;
        this.loadingTags = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('[Gallery Modal] Error cargando etiquetas:', err);
        console.error('[Gallery Modal] Error status:', err.status);
        console.error('[Gallery Modal] Error message:', err.message);
        console.error('[Gallery Modal] Error response:', err.error);
        this.tagsError = `Error (${err.status}): ${err.message}`;
        this.loadingTags = false;
        this.cdr.markForCheck();
      }
    });
  }

  /**
   * Load products with pagination and tag filtering
   */
  loadProducts() {
    this.loading = true;
    this.productsError = null;
    console.log('[Gallery Modal] Cargando productos - storeId:', this.storeId, 'page:', this.currentPage, 'tags:', this.selectedTagIds);
    this.tagService.searchProducts(
      this.storeId,
      this.searchQuery,
      this.selectedTagIds,
      this.currentPage,
      this.pageSize
    ).subscribe({
      next: async (response: any) => {
        console.log('[Gallery Modal] Respuesta recibida - estructura completa:', response);
        console.log('[Gallery Modal] response.content:', response.content);
        console.log('[Gallery Modal] response.totalElements:', response.totalElements);
        console.log('[Gallery Modal] response.last:', response.last);

        const products = response.content || response;
        const rawProducts = Array.isArray(products) ? products : [];
        const displayProducts = await this.buildGalleryDisplayProducts(rawProducts);

        if (this.currentPage === 0) {
          this.displayedProducts = displayProducts;
        } else {
          this.displayedProducts = [...this.displayedProducts, ...displayProducts];
        }

        this.totalElements = response.totalElements || displayProducts.length;
        this.hasMorePages = response.last !== undefined ? !response.last : false;
        this.loading = false;

        console.log('[Gallery Modal] Productos mostrados:', this.displayedProducts.length, 'Total:', this.totalElements);
        this.cdr.markForCheck();
      },
      error: (err: any) => {
        console.error('[Gallery Modal] Error cargando productos:', err);
        console.error('[Gallery Modal] Error status:', err.status);
        console.error('[Gallery Modal] Error message:', err.message);
        console.error('[Gallery Modal] Error response:', err.error);
        this.productsError = `Error (${err.status}): ${err.message}`;
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  async buildGalleryDisplayProducts(rawProducts: any[]): Promise<any[]> {
    const visibleRoots = (rawProducts || []).filter(product => this.isVisibleRootProduct(product));

    const displayProducts: any[] = [];
    for (const product of visibleRoots) {
      const lotes = await firstValueFrom(
        this.lotesService.getLotesByProductId(product.id).pipe(
          catchError(() => of([]))
        )
      );

      const displayLote = this.getGalleryDisplayLote(product, lotes);
      const displayStock = this.getGalleryDisplayStock(product, lotes);
      const displayCost = displayLote ? (displayLote.cost ?? product.cost) : product.cost;
      const displayPrice = displayLote ? (displayLote.price ?? product.price) : product.price;
      const displayProduct = {
        ...product,
        cost: displayCost,
        price: displayPrice,
        stock: displayStock,
        displayCost,
        displayPrice,
        displayStock,
        activeLote: displayLote
      };

      displayProducts.push(displayProduct);
    }

    return displayProducts;
  }

  private isVisibleRootProduct(product: any): boolean {
    if (!product || !product.id || product.parentId) {
      return false;
    }

    const isActive = product.isActive ?? true;
    const isDeleted = product.isDeleted ?? false;
    return isActive && !isDeleted;
  }

  private isVisibleLote(lote: any): boolean {
    if (!lote || !lote.id) {
      return false;
    }

    const isActive = lote.isActive ?? true;
    const isDeleted = lote.isDeleted ?? false;
    return isActive && !isDeleted;
  }

  private getGalleryDisplayLote(product: any, lotes: any[]): any {
    const visibleLotes = (lotes || []).filter(lote => this.isVisibleLote(lote));

    if (product?.isActiveForSale) {
      return null;
    }

    const activeForSaleLote = visibleLotes.find(lote => Boolean(lote.isActiveForSale));
    if (activeForSaleLote) {
      return activeForSaleLote;
    }

    return null;
  }

  private getGalleryDisplayStock(product: any, lotes: any[]): number {
    const rootStock = Number(product.stock ?? 0);
    const lotesStock = (lotes || []).reduce((total, lote) => {
      if (!this.isVisibleLote(lote)) {
        return total;
      }
      return total + Number(lote.stock ?? 0);
    }, 0);
    return rootStock + lotesStock;
  }

  /**
   * Handle search input
   */
  onSearchChange(value: string) {
    this.searchQuery = value;
    this.searchSubject.next(value);
  }

  /**
   * Toggle tag selection for filtering
   */
  toggleTag(tag: any) {
    const index = this.selectedTagIds.indexOf(tag.id);
    if (index > -1) {
      this.selectedTagIds.splice(index, 1);
    } else {
      this.selectedTagIds.push(tag.id);
    }
    this.currentPage = 0;
    this.displayedProducts = [];
    this.loadProducts();
  }

  /**
   * Check if a tag is selected
   */
  isTagSelected(tagId: number): boolean {
    return this.selectedTagIds.includes(tagId);
  }

  /**
   * Clear all tag filters
   */
  clearFilters() {
    this.selectedTagIds = [];
    this.searchQuery = '';
    this.currentPage = 0;
    this.displayedProducts = [];
    this.loadProducts();
  }

  /**
   * Show new tag form
   */
  toggleNewTagForm() {
    this.showNewTagForm = !this.showNewTagForm;
    this.newTagName = '';
  }

  /**
   * Create a new tag
   */
  createNewTag() {
    if (!this.newTagName.trim()) {
      alert('El nombre de la etiqueta no puede estar vacío');
      return;
    }

    this.creatingTag = true;
    this.tagService.createTag(this.storeId, this.newTagName).subscribe({
      next: (tag) => {
        this.allTags.push(tag);
        this.newTagName = '';
        this.showNewTagForm = false;
        this.creatingTag = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Error creating tag:', err);
        alert('Error al crear la etiqueta');
        this.creatingTag = false;
        this.cdr.markForCheck();
      }
    });
  }

  /**
   * Delete a tag
   */
  deleteTag(tag: any) {
    if (!confirm(`¿Eliminar la etiqueta "${tag.name}"?`)) {
      return;
    }

    this.tagService.deleteTag(tag.id).subscribe({
      next: () => {
        this.allTags = this.allTags.filter(t => t.id !== tag.id);
        if (this.selectedTagIds.includes(tag.id)) {
          this.selectedTagIds.splice(this.selectedTagIds.indexOf(tag.id), 1);
          this.currentPage = 0;
          this.displayedProducts = [];
          this.loadProducts();
        }
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Error deleting tag:', err);
        alert('Error al eliminar la etiqueta');
        this.cdr.markForCheck();
      }
    });
  }

  /**
   * Handle scroll for infinite scroll pagination
   */
  onGalleryScroll(event: any) {
    const element = event.target;
    // Check if scrolled to bottom
    if (element.scrollHeight - element.scrollTop - 200 < element.clientHeight) {
      if (this.hasMorePages && !this.loading && !this.isScrolling) {
        this.isScrolling = true;
        this.currentPage++;
        this.loadProducts();
        this.isScrolling = false;
      }
    }
  }

  /**
   * Close modal
   */
  close() {
    this.onClose.emit();
  }

  /**
   * Select a product to open description modal
   */
  selectProduct(product: any) {
    this.onProductSelect.emit(product);
  }

  onProductClick(product: any) {
    // If touch assigning tags, delegate
    if (this.touchDraggedTag) {
      this.onProductTouchAssign(product);
      return;
    }

    if (this.promoMode) {
      if (!this.promoProducts.some((p: any) => p.id === product.id)) {
        this.promoProducts.push(product);
        this.cdr.markForCheck();
      }
      return;
    }

    // Default behavior: open/select product
    this.selectProduct(product);
  }

  togglePromoMode() {
    if (this.promoMode) {
      this.cancelPromo();
      return;
    }

    this.startPromoMode();
  }

  startPromoMode() {
    this.promoMode = true;
    this.promoProducts = [];
    this.promoConfigOpen = false;
    this.promoDropActive = false;
    this.cdr.markForCheck();
  }

  cancelPromo() {
    this.promoMode = false;
    this.promoProducts = [];
    this.promoConfigOpen = false;
    this.promoDropActive = false;
    this.cdr.markForCheck();
  }

  removePromoProduct(product: any) {
    this.promoProducts = this.promoProducts.filter((p: any) => p.id !== product.id);
    this.cdr.markForCheck();
  }

  openPromotionConfig() {
    if (this.promoProducts.length === 0) {
      return;
    }
    this.promoConfigOpen = true;
    this.cdr.markForCheck();
  }

  onPromotionConfirmed(result: any) {
    console.log('[Gallery Modal] Promotion configured', result);

    const rawSources = Array.isArray(result?.sources)
      ? result.sources
      : Array.isArray(result?.sourceProducts)
        ? result.sourceProducts
        : [];

    const target = result?.target ?? {};
    const mode = target?.mode ?? result?.mode ?? 'LOTE';

    const payload = {
      sources: rawSources.map((item: any) => ({
        productId: item.productId,
        quantity: Number(item.quantity) || 1
      })),
      target: {
        mode,
        parentProductId: mode === 'LOTE' ? (target?.parentProductId ?? result?.parentProductId ?? null) : null,
        storeId: mode === 'PRODUCTO_NUEVO' ? this.storeId : null,
        name: target?.name ?? result?.name ?? 'Promo automática',
        description: target?.description ?? result?.description ?? '',
        cost: Number(target?.cost ?? result?.cost ?? 0),
        price: Number(target?.price ?? result?.price ?? 0),
        quantity: Number(target?.quantity ?? result?.targetQuantity ?? 1)
      }
    };

    this.lotesService.applyStockTransformation(payload).subscribe({
      next: () => {
        this.promoConfigOpen = false;
        this.promoMode = false;
        this.promoProducts = [];
        this.loadProducts();
        this.cdr.markForCheck();
      },
      error: (err: any) => {
        console.error('[Gallery Modal] Error aplicando transformación:', err);
        const message = err?.error?.message || err?.message || 'No se pudo guardar la promoción';
        alert(message);
      }
    });
  }

  onPromotionClosed() {
    this.promoConfigOpen = false;
    this.cdr.markForCheck();
  }

  /**
   * Get image path or placeholder
   */
  getImagePath(product: any): string {
    // Si tiene imagen asociada (producto.image es ProductImage)
    if (product.image && product.image.imagePath) {
      // Construir URL para obtener la imagen del servidor
      // El endpoint /api/product-images/file/{productId} retorna la imagen física
      const imageUrl = `${this.apiConfig.getApiUrl('/api/product-images')}/file/${product.id}`;
      console.log('[Gallery Modal] Construyendo URL de imagen para producto', product.id, ':', imageUrl);
      return imageUrl;
    }
    // Placeholder SVG en línea si no hay imagen
    return this.getPlaceholderImage();
  }

  /**
   * Handle image loading error
   */
  onImageError(event: any) {
    console.log('[Gallery Modal] Error cargando imagen, usando placeholder');
    event.target.src = this.getPlaceholderImage();
  }

  /**
   * Get placeholder SVG image (always works)
   */
  private getPlaceholderImage(): string {
    return 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzAwIiBoZWlnaHQ9IjMwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMzAwIiBoZWlnaHQ9IjMwMCIgZmlsbD0iI2VlZSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LXNpemU9IjE4IiBmaWxsPSIjYWFhIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBkeT0iLjNlbSI+U2luIGltYWdlbjwvdGV4dD48L3N2Zz4=';
  }

  /**
   * Get tag name by ID
   */
  getTagName(tagId: number): string {
    const tag = this.allTags.find(t => t.id === tagId);
    return tag?.name || 'Etiqueta';
  }

  /**
   * ============ DRAG & DROP METHODS ============
   */

  /**
   * Detect if user prefers dark mode
   * Forzar light mode (false) para que la galería sea blanca y limpia
   */
  isDarkMode(): boolean {
    return false;
  }

  /**
   * Handle drag start on tag
   */
  onTagDragStart(event: DragEvent, tag: any) {
    this.draggedTag = tag;
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'link';
      event.dataTransfer.setData('text/plain', JSON.stringify({ tagId: tag.id, tagName: tag.name }));
    }
  }

  /**
   * Handle drag end
   */
  onTagDragEnd(event: DragEvent) {
    this.draggedTag = null;
    this.dragOverProductId = null;
  }

  /**
   * Handle drag over product (allow drop)
   */
  onProductDragOver(event: DragEvent, productId: number) {
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'link';
    }
    this.dragOverProductId = productId;
  }

  /**
   * Handle drag leave product
   */
  onProductDragLeave(event: DragEvent, productId: number) {
    // Only clear if we're actually leaving the element
    const rect = (event.target as HTMLElement).getBoundingClientRect();
    const x = event.clientX;
    const y = event.clientY;
    
    if (x < rect.left || x >= rect.right || y < rect.top || y >= rect.bottom) {
      if (this.dragOverProductId === productId) {
        this.dragOverProductId = null;
      }
    }
  }

  /**
   * Remove a tag from a product
   */
  removeTagFromProduct(product: any, tag: any) {
    if (!confirm(`¿Remover la etiqueta "${tag.name}" de este producto?`)) {
      return;
    }

    this.tagService.removeTagFromProduct(product.id, tag.id).subscribe({
      next: () => {
        console.log('[Gallery Modal] Etiqueta removida del producto');
        // Remove tag from product's tags array
        if (product.tags) {
          product.tags = product.tags.filter((t: any) => t.id !== tag.id);
        }
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('[Gallery Modal] Error removiendo etiqueta:', err);
        alert('Error al remover la etiqueta del producto');
        this.cdr.markForCheck();
      }
    });
  }

  /**
   * Handle drop on product - assign tag to product
   */
  onProductDrop(event: DragEvent, product: any) {
    event.preventDefault();
    event.stopPropagation();
    
    if (!event.dataTransfer) {
      return;
    }

    const data = event.dataTransfer.getData('text/plain');
    
    try {
      const tagData = JSON.parse(data);
      const tagId = tagData.tagId;

      this.dragOverProductId = null;

      // Prevent assigning same tag twice
      if (product.tags && product.tags.some((t: any) => t.id === tagId)) {
        console.log('[Gallery Modal] Etiqueta ya asignada a este producto');
        return;
      }

      this.assigningTag = true;
      console.log('[Gallery Modal] Asignando etiqueta', tagId, 'a producto', product.id);

      this.tagService.addTagToProduct(product.id, tagId).subscribe({
        next: () => {
          console.log('[Gallery Modal] Etiqueta asignada exitosamente');
          // Add tag to product's tags array
          const tagObj = this.allTags.find(t => t.id === tagId);
          if (tagObj) {
            if (!product.tags) {
              product.tags = [];
            }
            product.tags.push(tagObj);
          }
          this.assigningTag = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          // Handle 409 Conflict - tag already assigned
          if (err.status === 409) {
            console.log('[Gallery Modal] Etiqueta ya asignada a este producto');
            // Ensure tag is in the product's tags array
            const tagObj = this.allTags.find(t => t.id === tagId);
            if (tagObj && product.tags && !product.tags.some((t: any) => t.id === tagId)) {
              product.tags.push(tagObj);
            }
            this.assigningTag = false;
            this.cdr.markForCheck();
            return;
          }
          
          console.error('[Gallery Modal] Error asignando etiqueta:', err);
          // Show user-friendly error message
          let errorMessage = 'Error al asignar la etiqueta';
          if (err.error && err.error.message) {
            errorMessage = err.error.message;
          }
          alert(errorMessage);
          this.assigningTag = false;
          this.cdr.markForCheck();
        }
      });
    } catch (err) {
      console.error('[Gallery Modal] Error al procesar datos del drag:', err);
      this.dragOverProductId = null;
    }
  }

  /**
   * ============ TOUCH SUPPORT FOR MOBILE ============
   */

  /**
   * Handle touch start on tag (mobile)
   */
  onTagTouchStart(event: TouchEvent, tag: any) {
    this.touchDraggedTag = tag;
  }

  /**
   * Handle touch end on tag (mobile)
   */
  onTagTouchEnd(event: TouchEvent) {
    this.touchDraggedTag = null;
  }

  /**
   * Handle touch on product to assign tag (mobile)
   */
  onProductTouchAssign(product: any) {
    if (!this.touchDraggedTag) {
      return;
    }

    const tagId = this.touchDraggedTag.id;
    const tagName = this.touchDraggedTag.name;

    // Prevent assigning same tag twice
    if (product.tags && product.tags.some((t: any) => t.id === tagId)) {
      alert(`La etiqueta "${tagName}" ya está asignada a este producto`);
      this.touchDraggedTag = null;
      return;
    }

    // Show confirmation
    if (!confirm(`¿Asignar la etiqueta "${tagName}" a "${product.name}"?`)) {
      return;
    }

    this.touchDraggedTag = null;
    this.assigningTag = true;
    console.log('[Gallery Modal] Asignando etiqueta', tagId, 'a producto', product.id);

    this.tagService.addTagToProduct(product.id, tagId).subscribe({
      next: () => {
        console.log('[Gallery Modal] Etiqueta asignada exitosamente');
        // Add tag to product's tags array
        const tagObj = this.allTags.find(t => t.id === tagId);
        if (tagObj) {
          if (!product.tags) {
            product.tags = [];
          }
          product.tags.push(tagObj);
        }
        alert(`¡Etiqueta "${tagName}" asignada a "${product.name}"!`);
        this.assigningTag = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        // Handle 409 Conflict - tag already assigned
        if (err.status === 409) {
          console.log('[Gallery Modal] Etiqueta ya asignada a este producto');
          const tagObj = this.allTags.find(t => t.id === tagId);
          if (tagObj && product.tags && !product.tags.some((t: any) => t.id === tagId)) {
            product.tags.push(tagObj);
          }
          this.assigningTag = false;
          this.cdr.markForCheck();
          return;
        }

        console.error('[Gallery Modal] Error asignando etiqueta:', err);
        let errorMessage = 'Error al asignar la etiqueta';
        if (err.error && err.error.message) {
          errorMessage = err.error.message;
        }
        alert(errorMessage);
        this.assigningTag = false;
        this.cdr.markForCheck();
      }
    });

  }

  onProductDragStart(event: DragEvent, product: any) {
    // If user is dragging a tag (text/json set elsewhere) we don't override
    if (this.draggedTag || !this.promoMode) return;

    this.draggedProduct = product;
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('application/json', JSON.stringify({ productId: product.id, name: product.name }));
    }
  }

  onPromoDragOver(event: DragEvent) {
    event.preventDefault();
    if (this.promoMode && event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
      this.promoDropActive = true;
      this.cdr.markForCheck();
    }
  }

  onPromoDragLeave(event: DragEvent) {
    event.preventDefault();
    this.promoDropActive = false;
    this.cdr.markForCheck();
  }

  onPromoDrop(event: DragEvent) {
    event.preventDefault();
    this.promoDropActive = false;
    if (!this.draggedProduct) {
      return;
    }

    if (!this.promoProducts.some((p: any) => p.id === this.draggedProduct.id)) {
      this.promoProducts.push(this.draggedProduct);
      this.cdr.markForCheck();
    }

    this.draggedProduct = null;
  }

}

import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient, HttpClientModule, HttpHeaders } from '@angular/common/http';
import { MenuService } from '../core/menu.service';
import { ApiConfigService } from '../auth/api-config.service';

@Component({
  selector: 'app-edit-store',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, HttpClientModule],
  templateUrl: './edit-store.component.html',
  styleUrls: ['./edit-store.component.css']
})
export class EditStoreComponent implements OnInit {
  storeForm!: FormGroup;
  isLoading = false;
  errorMessage = '';
  successMessage = '';
  storeId!: number;
  showPasswordField = false;
  selectedColor = '#007AFF';

  // Paleta HD profesional con predominio de verdes, rojos y terracota.
  colorPalette = [
    { name: 'Azul Sistema', hex: '#007AFF' },
    { name: 'Azul Zafiro', hex: '#2563EB' },
    { name: 'Turquesa Sistema', hex: '#00A7A0' },
    { name: 'Verde Eucalipto', hex: '#149B76' },
    { name: 'Verde Menta', hex: '#2FBF8F' },
    { name: 'Verde Esmeralda', hex: '#24A66A' },
    { name: 'Verde Bosque', hex: '#21865B' },
    { name: 'Verde Lima', hex: '#65B741' },
    { name: 'Coral Vivo', hex: '#F05A50' },
    { name: 'Rojo Sistema', hex: '#FF3B30' },
    { name: 'Rojo Cereza', hex: '#D92D55' },
    { name: 'Rojo Carmesí', hex: '#C9364B' },
    { name: 'Borgoña', hex: '#8F3040' },
    { name: 'Terracota', hex: '#D46A32' },
    { name: 'Naranja Sistema', hex: '#FF9500' },
    { name: 'Dorado Cálido', hex: '#E0A52B' }
  ];

  get isMenuOpen$() {
    return this.menuService.isMenuOpen$;
  }

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private http: HttpClient,
    private fb: FormBuilder,
    private menuService: MenuService,
    private apiConfig: ApiConfigService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.storeId = params['id'];
      this.initializeForm();
      this.loadStoreData();
    });
  }

  toggleMenu() {
    this.menuService.toggleMenu();
  }

  initializeForm() {
    this.storeForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
      accessPassword: ['', [Validators.minLength(6), Validators.maxLength(50)]],
      address: ['', [Validators.maxLength(255)]],
      description: ['', [Validators.maxLength(500)]],
      color: ['#007AFF']
    });
  }

  loadStoreData() {
    const token = localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : undefined;
    
    const apiUrl = this.apiConfig.getApiUrl(`/api/stores/${this.storeId}`);
    this.http.get(apiUrl, { headers }).subscribe({
      next: (store: any) => {
        this.storeForm.patchValue({
          name: store.name,
          address: store.address || '',
          description: store.description || '',
          color: store.color || '#007AFF'
        });
        this.selectedColor = store.color || '#007AFF';
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error cargando datos de tienda:', error);
        this.errorMessage = 'No fue posible cargar los datos de la tienda';
        this.cdr.markForCheck();
      }
    });
  }

  get name() {
    return this.storeForm.get('name');
  }

  get accessPassword() {
    return this.storeForm.get('accessPassword');
  }

  get address() {
    return this.storeForm.get('address');
  }

  get description() {
    return this.storeForm.get('description');
  }

  togglePasswordField() {
    this.showPasswordField = !this.showPasswordField;
    if (!this.showPasswordField) {
      this.storeForm.get('accessPassword')?.reset();
    }
    this.cdr.markForCheck();
  }

  selectColor(color: string) {
    this.selectedColor = color;
    this.storeForm.get('color')?.setValue(color);
    this.cdr.markForCheck();
  }

  updateStore() {
    if (!this.storeForm.get('name')?.valid) {
      this.errorMessage = 'Por favor completa el nombre correctamente.';
      this.cdr.markForCheck();
      return;
    }

    // Si se está cambiando la contraseña, validar que sea válida
    if (this.showPasswordField) {
      const passwordValue = this.storeForm.get('accessPassword')?.value;
      if (!passwordValue || passwordValue.trim().length === 0) {
        this.errorMessage = 'Por favor ingresa una contraseña.';
        this.cdr.markForCheck();
        return;
      }
      if (passwordValue.trim().length < 6 || passwordValue.trim().length > 50) {
        this.errorMessage = 'La contraseña debe tener entre 6 y 50 caracteres.';
        this.cdr.markForCheck();
        return;
      }
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.cdr.markForCheck();

    const token = localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : undefined;

    // Preparar datos a actualizar
    const updateData = {
      name: this.storeForm.get('name')?.value?.trim(),
      address: this.storeForm.get('address')?.value?.trim() || '',
      description: this.storeForm.get('description')?.value?.trim() || '',
      color: this.storeForm.get('color')?.value || '#E8E8E8'
    } as any;

    // Solo incluir contraseña si el usuario está intentando cambiarla y proporciona un valor válido
    if (this.showPasswordField) {
      const passwordValue = this.storeForm.get('accessPassword')?.value?.trim();
      if (passwordValue && passwordValue.length > 0) {
        updateData.accessPassword = passwordValue;
      }
    }

    const apiUrl = this.apiConfig.getApiUrl(`/api/stores/${this.storeId}`);
    this.http.put(apiUrl, updateData, { headers }).subscribe({
      next: () => {
        this.successMessage = '¡Tienda actualizada exitosamente!';
        this.cdr.markForCheck();
        setTimeout(() => {
          this.router.navigate(['/my-stores']);
        }, 2000);
      },
      error: (error) => {
        console.error('Error al actualizar tienda:', error);
        this.errorMessage = error?.error?.message || 'No fue posible actualizar la tienda. Revisa los datos e intenta de nuevo.';
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      complete: () => {
        this.isLoading = false;
      }
    });
  }

  goBack() {
    this.router.navigate(['/my-stores']);
  }

  getColorGradient(hexColor: string): string {
    // Convierte hex a RGB
    const hex = hexColor.replace('#', '');
    const r = parseInt(hex.substring(0, 2), 16);
    const g = parseInt(hex.substring(2, 4), 16);
    const b = parseInt(hex.substring(4, 6), 16);

    // Crea una versión más clara del color (para el gradient)
    const lighten = (val: number) => Math.min(255, Math.round(val + (255 - val) * 0.2));
    const darken = (val: number) => Math.max(0, Math.round(val * 0.85));

    const lightColor = `rgb(${lighten(r)}, ${lighten(g)}, ${lighten(b)})`;
    const darkColor = `rgb(${darken(r)}, ${darken(g)}, ${darken(b)})`;

    // Retorna un gradient diagonal
    return `linear-gradient(135deg, ${lightColor} 0%, ${hexColor} 50%, ${darkColor} 100%)`;
  }
}

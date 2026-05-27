import { Component, OnInit, NgZone, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { SettingsService } from './settings.service';
import { PaymentMethodConfigService, PaymentMethodConfig } from './payment-method-config.service';
import { AuthService } from '../auth/auth.service';
import { UserService } from '../core/user.service';
import { ApiConfigService } from '../auth/api-config.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {
  usernameForm!: FormGroup;
  passwordForm!: FormGroup;
  paymentMethodForm!: FormGroup;
  
  currentUsername = '';
  successMessage = '';
  errorMessage = '';
  
  showUsernameForm = false;
  showPasswordForm = false;
  showPaymentMethodForm = false;
  
  loadingUsername = false;
  loadingPassword = false;
  loadingPaymentMethods = false;
  loadingPaymentMethodCreation = false;

  paymentMethods: PaymentMethodConfig[] = [];
  currentStoreId: number | null = null;
  selectedQRImage: File | null = null;
  selectedQRImagePreview: string | null = null;

  constructor(
    private fb: FormBuilder,
    private settingsService: SettingsService,
    private paymentMethodConfigService: PaymentMethodConfigService,
    private authService: AuthService,
    private userService: UserService,
    private apiConfig: ApiConfigService,
    private router: Router,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {
    this.initializeForms();
  }

  ngOnInit() {
    this.loadCurrentUsername();
    this.loadUserStore();
  }

  loadUserStore() {
    // Obtener la tienda del usuario desde el backend
    const token = localStorage.getItem('token');
    if (!token) {
      this.errorMessage = 'No se encontró token de autenticación';
      return;
    }

    const headers = {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    };

    // Llamar al endpoint /api/stores/my-store
    fetch(this.apiConfig.getApiUrl('/api/stores/my-store'), { headers })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        // Verificar que sea JSON válido
        const contentType = response.headers.get('content-type');
        if (!contentType || !contentType.includes('application/json')) {
          throw new Error('La respuesta del servidor no es válida. El backend podría no estar disponible.');
        }
        
        return response.json();
      })
      .then((store: any) => {
        this.ngZone.run(() => {
          if (store && store.id) {
            this.currentStoreId = store.id;
            this.loadPaymentMethods();
          } else {
            throw new Error('Respuesta del servidor inválida');
          }
        });
      })
      .catch(error => {
        this.ngZone.run(() => {
          console.error('Error loading store:', error);
          
          // Mensaje más específico según el tipo de error
          let errorMsg = 'Error al cargar la tienda del usuario.';
          if (error.message.includes('<!doctype') || error.message.includes('HTML')) {
            errorMsg = 'El servidor de API no está disponible. Por favor, intenta más tarde.';
          } else if (error.message.includes('401') || error.message.includes('403')) {
            errorMsg = 'No tienes permiso para acceder a esta información.';
          } else if (error.message.includes('404')) {
            errorMsg = 'Tu tienda no fue encontrada en el sistema.';
          }
          
          this.errorMessage = errorMsg;
          this.paymentMethods = [];
          this.ngZone.run(() => {
            setTimeout(() => this.errorMessage = '', 7000);
          });
        });
      });
  }

  initializeForms() {
    this.usernameForm = this.fb.group({
      newUsername: ['', [Validators.required, Validators.minLength(3)]]
    });

    this.passwordForm = this.fb.group({
      oldPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });

    this.paymentMethodForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3)]],
      type: ['EFECTIVO', [Validators.required]],
      imageUrl: ['']
    });
  }

  loadCurrentUsername() {
    const token = localStorage.getItem('token');
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        this.currentUsername = payload.sub || 'Usuario';
      } catch (error) {
        console.error('Error decodificando JWT:', error);
      }
    }
  }

  loadPaymentMethods() {
    this.loadingPaymentMethods = true;
    this.cdr.markForCheck();
    this.paymentMethodConfigService.getAllActive().subscribe({
      next: (methods) => {
        this.ngZone.run(() => {
          this.paymentMethods = methods;
          this.loadingPaymentMethods = false;
          this.cdr.markForCheck();
        });
      },
      error: (error) => {
        this.ngZone.run(() => {
          this.loadingPaymentMethods = false;
          // Si hay error, mostrar lista vacía, no error
          this.paymentMethods = [];
          this.cdr.markForCheck();
          console.error('Error al cargar métodos de pago:', error);
        });
      }
    });
  }

  onQRImageSelected(event: any) {
    const file: File = event.target.files[0];
    if (!file) {
      this.selectedQRImage = null;
      this.selectedQRImagePreview = null;
      return;
    }

    // Validaciones de seguridad
    const validImageTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    if (!validImageTypes.includes(file.type)) {
      this.errorMessage = `❌ Tipo de archivo no permitido. Usa: JPG, PNG, GIF o WebP`;
      event.target.value = ''; // Limpiar input
      this.ngZone.run(() => {
        setTimeout(() => this.errorMessage = '', 5000);
      });
      return;
    }

    const maxSizeMB = 5;
    const maxSizeBytes = maxSizeMB * 1024 * 1024;
    if (file.size > maxSizeBytes) {
      this.errorMessage = `❌ La imagen no puede superar ${maxSizeMB}MB (tu archivo: ${(file.size / 1024 / 1024).toFixed(2)}MB)`;
      event.target.value = ''; // Limpiar input
      this.ngZone.run(() => {
        setTimeout(() => this.errorMessage = '', 5000);
      });
      return;
    }

    // Validar dimensiones mínimas
    const reader = new FileReader();
    reader.onload = (e: any) => {
      const img = new Image();
      img.onload = () => {
        this.ngZone.run(() => {
          // Validación de dimensiones (mínimo 100x100)
          if (img.width < 100 || img.height < 100) {
            this.errorMessage = `❌ La imagen debe tener al menos 100x100 píxeles (tu imagen: ${img.width}x${img.height})`;
            this.selectedQRImage = null;
            this.selectedQRImagePreview = null;
            event.target.value = '';
            this.cdr.markForCheck();
            setTimeout(() => this.errorMessage = '', 5000);
            return;
          }

          // Si todas las validaciones pasan, guardar la imagen
          this.selectedQRImage = file;
          this.selectedQRImagePreview = e.target.result;
          this.paymentMethodForm.patchValue({
            imageUrl: e.target.result
          });
          this.errorMessage = ''; // Limpiar errores si los había
          this.cdr.markForCheck();
        });
      };
      img.onerror = () => {
        this.ngZone.run(() => {
          this.errorMessage = `❌ No se pudo procesar la imagen. Asegúrate de que sea un archivo de imagen válido`;
          this.selectedQRImage = null;
          this.selectedQRImagePreview = null;
          event.target.value = '';
          this.cdr.markForCheck();
          setTimeout(() => this.errorMessage = '', 5000);
        });
      };
      img.src = e.target.result;
    };
    reader.onerror = () => {
      this.ngZone.run(() => {
        this.errorMessage = `❌ Error al leer el archivo. Por favor, intenta nuevamente`;
        event.target.value = '';
        this.cdr.markForCheck();
        setTimeout(() => this.errorMessage = '', 5000);
      });
    };
    reader.readAsDataURL(file);
  }

  clearQRImage() {
    this.selectedQRImage = null;
    this.selectedQRImagePreview = null;
    this.paymentMethodForm.patchValue({
      imageUrl: ''
    });
  }

  createPaymentMethod() {
    if (!this.currentStoreId) {
      this.errorMessage = 'No se encontró la tienda del usuario';
      return;
    }

    if (this.paymentMethodForm.invalid) {
      this.errorMessage = 'Por favor completa los campos requeridos';
      return;
    }

    const type = this.paymentMethodForm.get('type')?.value;
    if (type === 'QR' && !this.selectedQRImagePreview) {
      this.errorMessage = 'Por favor sube una imagen QR';
      return;
    }

    this.loadingPaymentMethodCreation = true;
    this.successMessage = '';
    this.errorMessage = '';

    const name = this.paymentMethodForm.get('name')?.value;
    const imageUrl = this.selectedQRImagePreview || undefined;

    this.paymentMethodConfigService.create(name, type, imageUrl).subscribe({
      next: (newMethod) => {
        this.loadingPaymentMethodCreation = false;
        this.paymentMethods.push(newMethod);
        this.successMessage = 'Método de pago creado exitosamente';
        this.paymentMethodForm.reset({ type: 'EFECTIVO' });
        this.selectedQRImage = null;
        this.selectedQRImagePreview = null;
        this.showPaymentMethodForm = false;
        this.cdr.markForCheck();

        requestAnimationFrame(() => {
          setTimeout(() => {
            this.successMessage = '';
            this.cdr.markForCheck();
          }, 4000);
        });
      },
      error: (error) => {
        this.loadingPaymentMethodCreation = false;
        this.errorMessage = error.error?.message || 'Error al crear el método de pago';
        this.cdr.markForCheck();
        
        requestAnimationFrame(() => {
          setTimeout(() => {
            this.errorMessage = '';
            this.cdr.markForCheck();
          }, 4000);
        });
      }
    });
  }

  deletePaymentMethod(id: number) {
    if (!confirm('¿Estás seguro de que deseas eliminar este método de pago?')) {
      return;
    }

    this.paymentMethodConfigService.delete(id).subscribe({
      next: () => {
        this.paymentMethods = this.paymentMethods.filter(m => m.id !== id);
        this.successMessage = 'Método de pago eliminado exitosamente';
        this.cdr.markForCheck();
        
        requestAnimationFrame(() => {
          setTimeout(() => {
            this.successMessage = '';
            this.cdr.markForCheck();
          }, 4000);
        });
      },
      error: (error) => {
        this.errorMessage = error.error?.message || 'Error al eliminar el método de pago';
        this.cdr.markForCheck();
        
        requestAnimationFrame(() => {
          setTimeout(() => {
            this.errorMessage = '';
            this.cdr.markForCheck();
          }, 4000);
        });
      }
    });
  }

  passwordMatchValidator(group: FormGroup) {
    const password = group.get('newPassword')?.value;
    const confirmPassword = group.get('confirmPassword')?.value;
    
    if (password && confirmPassword && password !== confirmPassword) {
      group.get('confirmPassword')?.setErrors({ passwordMismatch: true });
      return { passwordMismatch: true };
    }
    return null;
  }

  onUpdateUsername() {
    if (this.usernameForm.valid) {
      this.loadingUsername = true;
      this.successMessage = '';
      this.errorMessage = '';

      const newUsername = this.usernameForm.get('newUsername')?.value;

      this.settingsService.updateUsername(newUsername).subscribe({
        next: (response) => {
          this.ngZone.run(() => {
            this.loadingUsername = false;
            this.successMessage = 'Nombre de usuario actualizado correctamente.';
            this.currentUsername = newUsername;
            // Notificar al UserService para actualizar el nombre en el sidebar
            this.userService.updateUsername(newUsername);
            this.usernameForm.reset();
            this.showUsernameForm = false;
            this.cdr.markForCheck();
            
            setTimeout(() => {
              this.successMessage = '';
              this.cdr.markForCheck();
            }, 5000);
          });
        },
        error: (error) => {
          this.ngZone.run(() => {
            this.loadingUsername = false;
            this.errorMessage = error.error?.message || 'Error al actualizar el nombre de usuario.';
            this.cdr.markForCheck();
            setTimeout(() => {
              this.errorMessage = '';
              this.cdr.markForCheck();
            }, 5000);
          });
        }
      });
    }
  }

  onUpdatePassword() {
    if (this.passwordForm.valid) {
      this.loadingPassword = true;
      this.successMessage = '';
      this.errorMessage = '';

      const oldPassword = this.passwordForm.get('oldPassword')?.value;
      const newPassword = this.passwordForm.get('newPassword')?.value;

      this.settingsService.updatePassword(oldPassword, newPassword).subscribe({
        next: (response) => {
          this.ngZone.run(() => {
            this.loadingPassword = false;
            this.successMessage = 'Contraseña actualizada correctamente.';
            this.passwordForm.reset();
            this.showPasswordForm = false;
            this.cdr.markForCheck();
            
            setTimeout(() => {
              this.successMessage = '';
              this.cdr.markForCheck();
            }, 5000);
          });
        },
        error: (error) => {
          this.ngZone.run(() => {
            this.loadingPassword = false;
            this.errorMessage = error.error?.message || 'Error al actualizar la contraseña. Verifica tu contraseña actual.';
            this.cdr.markForCheck();
            setTimeout(() => {
              this.errorMessage = '';
              this.cdr.markForCheck();
            }, 5000);
          });
        }
      });
    }
  }

  toggleUsernameForm() {
    this.showUsernameForm = !this.showUsernameForm;
    if (!this.showUsernameForm) {
      this.usernameForm.reset();
    }
  }

  togglePasswordForm() {
    this.showPasswordForm = !this.showPasswordForm;
    if (!this.showPasswordForm) {
      this.passwordForm.reset();
    }
  }

  togglePaymentMethodForm() {
    this.showPaymentMethodForm = !this.showPaymentMethodForm;
    if (!this.showPaymentMethodForm) {
      this.paymentMethodForm.reset({ type: 'EFECTIVO' });
      this.selectedQRImage = null;
      this.selectedQRImagePreview = null;
    }
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}

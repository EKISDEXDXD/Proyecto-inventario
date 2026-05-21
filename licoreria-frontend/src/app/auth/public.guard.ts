import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class PublicGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(): boolean {
    const isAuthenticated = this.authService.isAuthenticated();
    
    if (isAuthenticated) {
      // Si ya está autenticado, redirigir a home
      this.router.navigate(['/home']);
      return false;
    }
    
    // Si NO está autenticado, permitir acceso a login/register
    return true;
  }
}

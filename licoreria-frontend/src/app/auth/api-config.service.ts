import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ApiConfigService {
  private apiBaseUrl: string;

  constructor() {
    this.apiBaseUrl = this.getApiBaseUrl();
  }

  private getApiBaseUrl(): string {
    const currentUrl = window.location.hostname;
    const isLocalhost = currentUrl === 'localhost' || currentUrl === '127.0.0.1';

    if (isLocalhost) {
      // Si es localhost en desarrollo, usar el puerto 8081 para el backend
      return `http://localhost:8081`;
    } else {
      // Para producción (VPS, dominios o ngrok), usar rutas relativas pasándolas por el proxy Nginx en puerto 80
      return '';
    }
  }

  getApiUrl(endpoint: string): string {
    return `${this.apiBaseUrl}${endpoint}`;
  }

  getBaseUrl(): string {
    return this.apiBaseUrl;
  }
}

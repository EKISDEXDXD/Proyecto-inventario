import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfigService } from '../auth/api-config.service';

export interface PaymentMethodConfig {
  id: number;
  name: string;
  type: string; // EFECTIVO, QR
  imageUrl: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class PaymentMethodConfigService {
  private apiUrl: string = '';

  constructor(
    private http: HttpClient,
    private apiConfig: ApiConfigService
  ) {
    this.apiUrl = this.apiConfig.getApiUrl('/api/payment-method-configs');
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  // Métodos globales (sin filtro de tienda)
  getAll(): Observable<PaymentMethodConfig[]> {
    const headers = this.getHeaders();
    return this.http.get<PaymentMethodConfig[]>(`${this.apiUrl}`, { headers });
  }

  getAllActive(): Observable<PaymentMethodConfig[]> {
    const headers = this.getHeaders();
    return this.http.get<PaymentMethodConfig[]>(`${this.apiUrl}/active`, { headers });
  }

  getById(id: number): Observable<PaymentMethodConfig> {
    const headers = this.getHeaders();
    return this.http.get<PaymentMethodConfig>(`${this.apiUrl}/${id}`, { headers });
  }

  create(name: string, type: string, imageUrl?: string): Observable<PaymentMethodConfig> {
    const headers = this.getHeaders();
    const body = {
      name,
      type,
      imageUrl: imageUrl || null,
      isActive: true
    };
    return this.http.post<PaymentMethodConfig>(this.apiUrl, body, { headers });
  }

  update(id: number, name: string, type: string, imageUrl?: string, isActive?: boolean): Observable<PaymentMethodConfig> {
    const headers = this.getHeaders();
    const body = {
      name,
      type,
      imageUrl: imageUrl || null,
      isActive: isActive !== undefined ? isActive : true
    };
    return this.http.put<PaymentMethodConfig>(`${this.apiUrl}/${id}`, body, { headers });
  }

  delete(id: number): Observable<void> {
    const headers = this.getHeaders();
    return this.http.delete<void>(`${this.apiUrl}/${id}`, { headers });
  }
}

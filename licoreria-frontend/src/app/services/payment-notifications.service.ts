import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfigService } from '../auth/api-config.service';

@Injectable({ providedIn: 'root' })
export class PaymentNotificationsService {
  private readonly apiUrl: string;

  constructor(private http: HttpClient, private apiConfig: ApiConfigService) {
    this.apiUrl = this.apiConfig.getApiUrl('/api/payment-notifications');
  }

  private headers(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      Authorization: `Bearer ${token ?? ''}`,
      'Content-Type': 'application/json'
    });
  }

  getByStore(storeId: number, status?: string): Observable<any[]> {
    const query = status ? `?status=${status}` : '';
    return this.http.get<any[]>(`${this.apiUrl}/store/${storeId}${query}`, { headers: this.headers() });
  }

  reconcile(id: number, transactionId: number): Observable<any> {
    return this.http.patch<any>(`${this.apiUrl}/${id}/reconcile`, { transactionId }, { headers: this.headers() });
  }

  discard(id: number): Observable<any> {
    return this.http.patch<any>(`${this.apiUrl}/${id}/discard`, {}, { headers: this.headers() });
  }
}

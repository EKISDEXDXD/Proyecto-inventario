import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfigService } from '../auth/api-config.service';

@Injectable({ providedIn: 'root' })
export class CashControlService {
  private readonly apiUrl: string;

  constructor(private http: HttpClient, apiConfig: ApiConfigService) {
    this.apiUrl = apiConfig.getApiUrl('/api/cash-control');
  }

  private headers(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      Authorization: `Bearer ${token ?? ''}`,
      'Content-Type': 'application/json'
    });
  }

  getEntries(storeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/store/${storeId}`, { headers: this.headers() });
  }

  getSummary(storeId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/summary/${storeId}`, { headers: this.headers() });
  }

  updateSummary(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/summary`, payload, { headers: this.headers() });
  }

  createEntry(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/entries`, payload, { headers: this.headers() });
  }

  deleteEntry(entryId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/entries/${entryId}`, { headers: this.headers() });
  }
}

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfigService } from '../auth/api-config.service';

@Injectable({
  providedIn: 'root'
})
export class SettingsService {
  private apiUrl: string;

  constructor(private http: HttpClient, private apiConfigService: ApiConfigService) {
    this.apiUrl = this.apiConfigService.getApiUrl('/api/users');
  }

  updateUsername(newUsername: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/username`, { username: newUsername });
  }

  updatePassword(oldPassword: string, newPassword: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/password`, { 
      oldPassword, 
      newPassword 
    });
  }

  getCurrentUser(): Observable<any> {
    return this.http.get(`${this.apiUrl}/profile`);
  }
}

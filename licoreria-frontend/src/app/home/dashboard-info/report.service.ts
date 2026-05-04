import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfigService } from '../../auth/api-config.service';

export interface Report {
  id: number;
  title: string;
  description: string;
  reportDate: string;
  storeId: number;
  storeName: string;
  userId: number;
  userName: string;
  createdAt: string;
  updatedAt: string;
  active: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ReportService {
  private apiUrl = '';

  constructor(private http: HttpClient, private apiConfig: ApiConfigService) {
    this.apiUrl = this.apiConfig.getBaseUrl() + '/api/reports';
  }

  /**
   * Crear un nuevo reporte
   */
  createReport(storeId: number, title: string, description: string, reportDate: string): Observable<Report> {
    const payload = {
      title,
      description,
      reportDate
    };

    return this.http.post<Report>(`${this.apiUrl}/${storeId}`, payload);
  }

  /**
   * Obtener un reporte por ID
   */
  getReport(reportId: number): Observable<Report> {
    return this.http.get<Report>(`${this.apiUrl}/${reportId}`);
  }



  /**
   * Obtener reportes de una tienda con paginación
   */
  getReportsByStore(storeId: number, page: number = 0, size: number = 10): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<any>(`${this.apiUrl}/store/${storeId}`, { params });
  }

  /**
   * Obtener reportes en un rango de fechas
   */
  getReportsByDateRange(storeId: number, startDate: string, endDate: string): Observable<Report[]> {
    const params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate);

    return this.http.get<Report[]>(`${this.apiUrl}/store/${storeId}/range`, { params });
  }

  /**
   * Obtener todos los reportes de una tienda
   */
  getAllReportsByStore(storeId: number): Observable<Report[]> {
    return this.http.get<Report[]>(`${this.apiUrl}/store/${storeId}/all`);
  }

  /**
   * Actualizar un reporte
   */
  updateReport(reportId: number, title?: string, description?: string, reportDate?: string): Observable<Report> {
    const payload: any = {};
    if (title) payload.title = title;
    if (description) payload.description = description;
    if (reportDate) payload.reportDate = reportDate;

    return this.http.put<Report>(`${this.apiUrl}/${reportId}`, payload);
  }

  /**
   * Eliminar un reporte
   */
  deleteReport(reportId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${reportId}`);
  }


}

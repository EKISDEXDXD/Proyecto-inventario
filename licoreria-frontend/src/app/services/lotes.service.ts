import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, map, catchError, throwError } from 'rxjs';
import { ApiConfigService } from '../auth/api-config.service';

@Injectable({
  providedIn: 'root'
})
export class LotesService {

  private apiUrl: string = '';

  constructor(
    private http: HttpClient,
    private apiConfigService: ApiConfigService
  ) {
    this.apiUrl = this.apiConfigService.getApiUrl('/api/products');
  }

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': 'Bearer ' + localStorage.getItem('token'),
      'Content-Type': 'application/json'
    });
  }

  /**
   * Obtener todos los lotes de un producto principal
   */
  getLotesByProductId(parentId: number): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.apiUrl}/${parentId}/lotes`,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Obtener el lote activo de un producto
   */
  getActiveLote(parentId: number): Observable<any> {
    return this.http.get<any>(
      `${this.apiUrl}/${parentId}/active-lote`,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Crear un nuevo lote
   */
  createLote(parentId: number, loteData: any): Observable<any> {
    return this.http.post<any>(
      `${this.apiUrl}/${parentId}/create-lote`,
      loteData,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Activar un lote (desactiva los demás)
   */
  activateLote(loteId: number): Observable<any> {
    return this.http.put<any>(
      `${this.apiUrl}/${loteId}/activate`,
      {},
      { headers: this.getHeaders() }
    );
  }

  /**
   * Calcular el stock total de un producto (suma de todos lotes)
   */
  calculateTotalStock(lotes: any[]): number {
    return lotes.reduce((total, lote) => total + (lote.stock || 0), 0);
  }

  /**
   * Obtener la información de un producto específico
   */
  getProductById(productId: number): Observable<any> {
    return this.http.get<any>(
      `${this.apiUrl}/${productId}`,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Eliminar un lote (se desactiva si tiene transacciones)
   */
  deleteLote(loteId: number): Observable<any> {
    return this.http.delete<any>(
      `${this.apiUrl}/${loteId}`,
      { headers: this.getHeaders() }
    ).pipe(
      catchError(error => {
        console.error('Error al eliminar lote:', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Cambiar isActiveForSale de un lote
   * Si se activa, desactiva automáticamente los hermanos (solo uno puede estar activo para venta)
   */
  setActiveForSale(loteId: number, isActive: boolean): Observable<any> {
    return this.http.put<any>(
      `${this.apiUrl}/${loteId}/active-for-sale?active=${isActive}`,
      {},
      { headers: this.getHeaders() }
    ).pipe(
      catchError(error => {
        console.error('Error al cambiar isActiveForSale:', error);
        return throwError(() => error);
      })
    );
  }

  applyStockTransformation(payload: any): Observable<any> {
    return this.http.post<any>(
      `${this.apiUrl}/transformations`,
      payload,
      { headers: this.getHeaders() }
    ).pipe(
      catchError(error => {
        console.error('Error al aplicar la transformación de stock:', error);
        return throwError(() => error);
      })
    );
  }
}

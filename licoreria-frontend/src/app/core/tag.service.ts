import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ApiConfigService } from '../auth/api-config.service';

@Injectable({
  providedIn: 'root'
})
export class TagService {
  private apiTagsUrl: string = '';

  constructor(
    private http: HttpClient,
    private apiConfig: ApiConfigService
  ) {
    this.apiTagsUrl = this.apiConfig.getApiUrl('/api/tags');
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    if (!token) {
      console.warn('[TagService] No token found in localStorage');
    }
    console.debug('[TagService] Using token:', token ? `${token.substring(0, 20)}...` : 'EMPTY');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  /**
   * Obtener todas las etiquetas de una tienda
   */
  getTagsByStore(storeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiTagsUrl}/store/${storeId}`, {
      headers: this.getHeaders()
    });
  }

  /**
   * Crear una nueva etiqueta
   */
  createTag(storeId: number, name: string): Observable<any> {
    return this.http.post(`${this.apiTagsUrl}/store/${storeId}`, 
      { name }, 
      { headers: this.getHeaders() }
    );
  }

  /**
   * Actualizar una etiqueta
   */
  updateTag(tagId: number, newName: string): Observable<any> {
    return this.http.put(`${this.apiTagsUrl}/${tagId}`, 
      { name: newName },
      { headers: this.getHeaders() }
    );
  }

  /**
   * Eliminar una etiqueta
   */
  deleteTag(tagId: number): Observable<any> {
    return this.http.delete(`${this.apiTagsUrl}/${tagId}`, {
      headers: this.getHeaders()
    });
  }

  /**
   * Agregar una etiqueta a un producto
   * Maneja cualquier status 2xx como éxito
   */
  addTagToProduct(productId: number, tagId: number): Observable<any> {
    return this.http.post(
      `${this.apiTagsUrl}/product/${productId}/tag/${tagId}`,
      {},
      { 
        headers: this.getHeaders(),
        observe: 'response',
        responseType: 'text'
      }
    ).pipe(
      map(response => {
        // Cualquier status 2xx es éxito
        return { success: true, status: response.status, body: response.body };
      }),
      catchError((error: any) => {
        // Solo true errors (4xx, 5xx) deben pasar como error
        throw error;
      })
    );
  }

  /**
   * Remover una etiqueta de un producto
   */
  removeTagFromProduct(productId: number, tagId: number): Observable<any> {
    return this.http.delete(
      `${this.apiTagsUrl}/product/${productId}/tag/${tagId}`,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Obtener etiquetas de un producto
   */
  getTagsByProduct(productId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiTagsUrl}/product/${productId}`);
  }

  /**
   * Búsqueda de productos con paginación y filtro de etiquetas
   */
  searchProducts(storeId: number, searchQuery: string = '', tagIds: number[] = [], page: number = 0, size: number = 20): Observable<any> {
    let url = `${this.apiConfig.getApiUrl('/api/products')}/gallery/search?storeId=${storeId}&search=${encodeURIComponent(searchQuery)}&page=${page}&size=${size}`;
    
    if (tagIds && tagIds.length > 0) {
      url += `&tags=${tagIds.join(',')}`;
    }

    return this.http.get<any>(url, { headers: this.getHeaders() });
  }
}

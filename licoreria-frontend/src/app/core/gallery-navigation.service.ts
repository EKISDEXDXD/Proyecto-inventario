import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class GalleryNavigationService {
  private openGallerySubject = new Subject<void>();
  openGallery$ = this.openGallerySubject.asObservable();

  triggerOpenGallery(): void {
    queueMicrotask(() => this.openGallerySubject.next());
  }
}

import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ModalStackService {
  private readonly baseZIndex = 1100;
  private currentZIndex = this.baseZIndex;

  bringToFront(): number {
    this.currentZIndex += 10;
    return this.currentZIndex;
  }
}

import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface Currency {
  code: string;
  symbol: string;
  name: string;
}

@Injectable({
  providedIn: 'root'
})
export class CurrencyService {
  private currencies: Currency[] = [
    { code: 'USD', symbol: '$', name: 'Dólar Estadounidense' },
    { code: 'BS', symbol: 'Bs', name: 'Bolívares' },
    { code: 'EUR', symbol: '€', name: 'Euro' },
    { code: 'MXN', symbol: '$', name: 'Peso Mexicano' },
    { code: 'COP', symbol: '$', name: 'Peso Colombiano' },
    { code: 'ARS', symbol: '$', name: 'Peso Argentino' }
  ];

  private selectedCurrency = new BehaviorSubject<Currency>(this.getDefaultCurrency());
  public currency$: Observable<Currency> = this.selectedCurrency.asObservable();

  constructor() {
    this.loadCurrencyFromLocalStorage();
  }

  private getDefaultCurrency(): Currency {
    return this.currencies[0]; // USD por defecto
  }

  private loadCurrencyFromLocalStorage(): void {
    const savedCurrencyCode = localStorage.getItem('selectedCurrency');
    if (savedCurrencyCode) {
      const currency = this.currencies.find(c => c.code === savedCurrencyCode);
      if (currency) {
        this.selectedCurrency.next(currency);
      }
    }
  }

  getCurrentCurrency(): Currency {
    return this.selectedCurrency.value;
  }

  setCurrency(code: string): void {
    const currency = this.currencies.find(c => c.code === code);
    if (currency) {
      this.selectedCurrency.next(currency);
      localStorage.setItem('selectedCurrency', code);
    }
  }

  getCurrencies(): Currency[] {
    return [...this.currencies];
  }

  formatCurrency(value: number): string {
    const currency = this.selectedCurrency.value;
    return `${currency.symbol} ${value.toFixed(2)}`;
  }
}

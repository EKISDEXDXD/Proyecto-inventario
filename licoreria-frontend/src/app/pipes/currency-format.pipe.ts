import { Pipe, PipeTransform, ChangeDetectorRef, OnDestroy, NgZone } from '@angular/core';
import { CurrencyService } from '../services/currency.service';
import { Subscription } from 'rxjs';

@Pipe({
  name: 'currencyFormat',
  pure: false,
  standalone: true
})
export class CurrencyFormatPipe implements PipeTransform, OnDestroy {
  private subscription?: Subscription;
  private lastValue: number = 0;
  private lastCurrency: string = '';
  private lastFormattedValue: string = '';

  constructor(
    private currencyService: CurrencyService,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {
    this.ngZone.runOutsideAngular(() => {
      this.subscription = this.currencyService.currency$.subscribe(() => {
        this.lastCurrency = '';
        this.cdr.markForCheck();
      });
    });
  }

  transform(value: number): string {
    if (value == null || isNaN(value)) {
      return '';
    }

    const currency = this.currencyService.getCurrentCurrency();
    const currencyKey = `${currency.code}_${value}`;

    if (this.lastValue === value && this.lastCurrency === currency.code) {
      return this.lastFormattedValue;
    }

    this.lastValue = value;
    this.lastCurrency = currency.code;
    
    // Format number without unnecessary zeros
    const numValue = parseFloat(String(value)).toFixed(2);
    const formattedNumber = parseFloat(numValue).toString();
    
    this.lastFormattedValue = `${currency.symbol} ${formattedNumber}`;

    return this.lastFormattedValue;
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }
}

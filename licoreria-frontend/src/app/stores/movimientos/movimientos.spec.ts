import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Movimientos } from './movimientos';

describe('Movimientos', () => {
  let component: Movimientos;
  let fixture: ComponentFixture<Movimientos>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Movimientos],
    }).compileComponents();

    fixture = TestBed.createComponent(Movimientos);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should count only purchase and sale reasons in the stats grid', () => {
    component.transactions = [
      { id: 1, type: 'ENTRADA', reason: 'COMPRA', dateTime: new Date().toISOString() },
      { id: 2, type: 'ENTRADA', reason: 'AJUSTE', dateTime: new Date().toISOString() },
      { id: 3, type: 'ENTRADA', reason: 'DEVOLUCIÓN', dateTime: new Date().toISOString() },
      { id: 4, type: 'SALIDA', reason: 'VENTA', dateTime: new Date().toISOString() },
      { id: 5, type: 'SALIDA', reason: 'PERDIDA', dateTime: new Date().toISOString() },
      { id: 6, type: 'SALIDA', reason: 'DEVOLUCION', dateTime: new Date().toISOString() },
      { id: 7, type: 'SALIDA', reason: 'AJUSTE', dateTime: new Date().toISOString() }
    ];

    expect(component.entradasCount).toBe(1);
    expect(component.salidasCount).toBe(1);
  });
});

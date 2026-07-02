import { Component, Inject, OnInit, OnDestroy, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { formatDate } from '@angular/common';
import { ApiConfigService } from '../auth/api-config.service';

interface ExportOption {
  type: 'COMPLETE' | 'SIMPLE' | 'DAILY';
  label: string;
  description: string;
  icon: string;
}

interface ExportHistory {
  id: string;
  fileName: string;
  dateGenerated: string;
  period: string;
  reportType: string;
  downloadUrl: string;
}

@Component({
  selector: 'app-export-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  template: `
    <div class="export-modal-overlay" [class.dark]="isDarkMode">
      <div class="export-modal" [class.dark]="isDarkMode">
        <!-- Header -->
        <div class="modal-header">
          <div class="header-content">
            <div class="header-icon">
              <i class="bx bx-bar-chart"></i>
            </div>
            <div class="header-text">
              <h2>Reportes de Ventas</h2>
              <p>Exporta análisis detallados de tu inventario</p>
            </div>
          </div>
          <button class="close-btn" (click)="onCancel()">
            <i class="bx bx-x"></i>
          </button>
        </div>

        <!-- Content -->
        <div class="modal-content">

          <!-- Info Section -->
          <div class="info-section">
            <div class="info-card">
              <div class="info-icon">
                <i class="bx bx-info-circle"></i>
              </div>
              <div class="info-content">
                <h4>Información de Reportes</h4>
                <div class="info-items">
                  <div class="info-item">
                    <span class="info-label">Completo:</span>
                    <span class="info-text">4 hojas + Costos Administrativos</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">Resumido:</span>
                    <span class="info-text">2 hojas + Costos Administrativos</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">Diario:</span>
                    <span class="info-text">Reporte del día + Costos (atajo rápido)</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Export Options -->
          <div class="export-section">
            <h3 class="section-title">
              <i class="bx bx-file"></i>
              Generar Reporte
            </h3>

            <div class="export-cards">

              <!-- Complete Report -->
              <div class="export-card">
                <div class="card-header">
                  <div class="card-icon complete">
                    <i class="bx bx-spreadsheet"></i>
                  </div>
                  <div class="card-info">
                    <h4>Reporte Completo</h4>
                    <p>4 hojas con análisis detallado</p>
                  </div>
                </div>

                <div class="card-content">
                  <div class="period-selector">
                    <label class="radio-option">
                      <input type="radio" name="complete-period" value="30days" [(ngModel)]="completeReportPeriod" (change)="onPeriodChange('complete')">
                      <span class="radio-label">Últimos 30 días</span>
                    </label>
                    <label class="radio-option">
                      <input type="radio" name="complete-period" value="custom" [(ngModel)]="completeReportPeriod" (change)="onPeriodChange('complete')">
                      <span class="radio-label">Personalizar período</span>
                    </label>
                  </div>

                  <div *ngIf="completeReportPeriod === 'custom'" class="date-picker">
                    <div class="date-input-group">
                      <div class="date-input">
                        <label>Desde</label>
                        <input type="date" [(ngModel)]="completeFromDate">
                      </div>
                      <div class="date-input">
                        <label>Hasta</label>
                        <input type="date" [(ngModel)]="completeToDate">
                      </div>
                    </div>
                  </div>

                  <button
                    class="export-btn primary"
                    (click)="downloadReport('COMPLETE')"
                    [disabled]="isLoading">
                    <i class="bx bx-download" *ngIf="!isLoading"></i>
                    <i class="bx bx-loader-alt bx-spin" *ngIf="isLoading"></i>
                    <span>{{ isLoading ? 'Generando...' : 'Descargar Completo' }}</span>
                  </button>
                </div>
              </div>

              <!-- Simple Report -->
              <div class="export-card">
                <div class="card-header">
                  <div class="card-icon simple">
                    <i class="bx bx-file"></i>
                  </div>
                  <div class="card-info">
                    <h4>Reporte Resumido</h4>
                    <p>2 hojas rápidas</p>
                  </div>
                </div>

                <div class="card-content">
                  <div class="period-selector">
                    <label class="radio-option">
                      <input type="radio" name="simple-period" value="30days" [(ngModel)]="simpleReportPeriod" (change)="onPeriodChange('simple')">
                      <span class="radio-label">Últimos 30 días</span>
                    </label>
                    <label class="radio-option">
                      <input type="radio" name="simple-period" value="custom" [(ngModel)]="simpleReportPeriod" (change)="onPeriodChange('simple')">
                      <span class="radio-label">Personalizar período</span>
                    </label>
                  </div>

                  <div *ngIf="simpleReportPeriod === 'custom'" class="date-picker">
                    <div class="date-input-group">
                      <div class="date-input">
                        <label>Desde</label>
                        <input type="date" [(ngModel)]="simpleFromDate">
                      </div>
                      <div class="date-input">
                        <label>Hasta</label>
                        <input type="date" [(ngModel)]="simpleToDate">
                      </div>
                    </div>
                  </div>

                  <button
                    class="export-btn primary"
                    (click)="downloadReport('SIMPLE')"
                    [disabled]="isLoading">
                    <i class="bx bx-download" *ngIf="!isLoading"></i>
                    <i class="bx bx-loader-alt bx-spin" *ngIf="isLoading"></i>
                    <span>{{ isLoading ? 'Generando...' : 'Descargar Resumido' }}</span>
                  </button>
                </div>
              </div>

              <!-- Daily Report -->
              <div class="export-card">
                <div class="card-header">
                  <div class="card-icon daily">
                    <i class="bx bx-calendar-event"></i>
                  </div>
                  <div class="card-info">
                    <h4>Reporte Diario</h4>
                    <p>Ventas del día actual (0:00 - 23:59 horas)</p>
                  </div>
                </div>

                <div class="card-content">
                  <p class="daily-info">Este reporte incluye todas las transacciones de hoy. Se genera automáticamente sin opciones de período.</p>
                  <button
                    class="export-btn primary"
                    (click)="downloadDailyReport()"
                    [disabled]="isLoading">
                    <i class="bx bx-download" *ngIf="!isLoading"></i>
                    <i class="bx bx-loader-alt bx-spin" *ngIf="isLoading"></i>
                    <span>{{ isLoading ? 'Generando...' : 'Descargar Hoy' }}</span>
                  </button>
                </div>
              </div>

              <!-- Info Reports -->
              <div class="export-card">
                <div class="card-header">
                  <div class="card-icon info">
                    <i class="bx bx-document"></i>
                  </div>
                  <div class="card-info">
                    <h4>Reportes de Información</h4>
                    <p>Reportes con fotos e información detallada</p>
                  </div>
                </div>

                <div class="card-content">
                  <div class="period-selector">
                    <label class="radio-option">
                      <input type="radio" name="info-period" value="30days" [(ngModel)]="infoReportPeriod" (change)="onPeriodChange('info')">
                      <span class="radio-label">Últimos 30 días</span>
                    </label>
                    <label class="radio-option">
                      <input type="radio" name="info-period" value="custom" [(ngModel)]="infoReportPeriod" (change)="onPeriodChange('info')">
                      <span class="radio-label">Personalizar período</span>
                    </label>
                    <label class="radio-option">
                      <input type="radio" name="info-period" value="all" [(ngModel)]="infoReportPeriod" (change)="onPeriodChange('info')">
                      <span class="radio-label">Todos los reportes</span>
                    </label>
                  </div>

                  <div *ngIf="infoReportPeriod === 'custom'" class="date-picker">
                    <div class="date-input-group">
                      <div class="date-input">
                        <label>Desde</label>
                        <input type="date" [(ngModel)]="infoFromDate">
                      </div>
                      <div class="date-input">
                        <label>Hasta</label>
                        <input type="date" [(ngModel)]="infoToDate">
                      </div>
                    </div>
                  </div>

                  <div class="button-group">
                    <button
                      class="export-btn primary"
                      *ngIf="infoReportPeriod !== 'all'"
                      (click)="downloadInfoReport('range')"
                      [disabled]="isLoading">
                      <i class="bx bx-download" *ngIf="!isLoading"></i>
                      <i class="bx bx-loader-alt bx-spin" *ngIf="isLoading"></i>
                      <span>{{ isLoading ? 'Generando...' : 'Descargar Período' }}</span>
                    </button>
                    <button
                      class="export-btn primary"
                      *ngIf="infoReportPeriod === 'all'"
                      (click)="downloadInfoReport('all')"
                      [disabled]="isLoading">
                      <i class="bx bx-download" *ngIf="!isLoading"></i>
                      <i class="bx bx-loader-alt bx-spin" *ngIf="isLoading"></i>
                      <span>{{ isLoading ? 'Generando...' : 'Descargar Todo' }}</span>
                    </button>
                  </div>
                </div>
              </div>

              <!-- History -->
              <div class="export-card">
                <div class="card-header">
                  <div class="card-icon history">
                    <i class="bx bx-history"></i>
                  </div>
                  <div class="card-info">
                    <h4>Historial</h4>
                    <p>Reportes anteriores</p>
                  </div>
                </div>

                <div class="card-content">
                  <div *ngIf="exportHistory.length === 0" class="empty-state">
                    <i class="bx bx-folder-open"></i>
                    <p>No hay reportes generados aún</p>
                  </div>

                  <div *ngIf="exportHistory.length > 0" class="history-list">
                    <div class="history-item" *ngFor="let item of exportHistory">
                      <div class="history-info">
                        <div class="history-period">{{ item.period }}</div>
                        <div class="history-date">{{ item.dateGenerated | date: 'dd/MM/yyyy HH:mm' }}</div>
                      </div>
                      <div class="history-type">
                        <span class="type-badge" [class]="item.reportType === 'COMPLETE' ? 'complete' : (item.reportType === 'SIMPLE' ? 'simple' : 'daily')">
                          {{ item.reportType === 'COMPLETE' ? 'Completo' : (item.reportType === 'SIMPLE' ? 'Resumido' : 'Diario') }}
                        </span>
                      </div>
                      <div class="history-actions">
                        <button class="action-btn download" (click)="redownloadReport(item)" title="Descargar">
                          <i class="bx bx-download"></i>
                        </button>
                        <button class="action-btn delete" (click)="deleteReport(item)" title="Eliminar">
                          <i class="bx bx-trash"></i>
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

            </div>
          </div>

        </div>

        <!-- Footer -->
        <div class="modal-footer">
          <button class="btn-secondary" (click)="onCancel()">Cerrar</button>
        </div>

        <!-- Messages -->
        <div *ngIf="errorMessage" class="message error">
          <i class="bx bx-error-circle"></i>
          <span>{{ errorMessage }}</span>
        </div>

        <div *ngIf="successMessage" class="message success">
          <i class="bx bx-check-circle"></i>
          <span>{{ successMessage }}</span>
        </div>
      </div>
    </div>`,
  styles: [`
    /* Overlay */
    .export-modal-overlay {
      position: fixed;
      inset: 0;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      
      
    }

    /* Modal Container */
    .export-modal {
      width: 100%;
      max-width: 900px;
      background: white;
      border-radius: 14px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
      overflow: hidden;
      max-height: 90vh;
      display: flex;
      flex-direction: column;
      font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, sans-serif;
      pointer-events: auto;
      position: relative;
      z-index: 10000;
    }

    /* Dark Mode Support */
    .export-modal-overlay.dark {
      background: transparent;
    }

    .export-modal.dark {
      background: #1a1a2e;
      color: #ffffff;
    }

    /* Header */
    .modal-header {
      background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
      color: white;
      padding: 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }

    .header-content {
      display: flex;
      gap: 1rem;
      align-items: flex-start;
    }

    .header-icon {
      width: 48px;
      height: 48px;
      background: rgba(255, 255, 255, 0.2);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      flex-shrink: 0;
    }

    .header-text h2 {
      margin: 0 0 0.25rem 0;
      font-size: 1.5rem;
      font-weight: 600;
    }
    

    .header-text p {
      margin: 0;
      opacity: 0.9;
      font-size: 0.95rem;
    }

    .close-btn {
      background: none;
      border: none;
      color: white;
      cursor: pointer;
      padding: 0.5rem;
      border-radius: 8px;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
    }

    .close-btn:hover {
      background: rgba(255, 255, 255, 0.2);
      transform: scale(1.05);
    }

    /* Content */
    .modal-content {
      flex: 1;
      overflow-y: auto;
      padding: 0;
    }

    /* Info Section */
    .info-section {
      padding: 1.5rem;
      background: #f8f9fa;
      border-bottom: 1px solid #e9ecef;
    }

    .export-modal.dark .info-section {
      background: #0f0f1a;
      border-bottom-color: #2a2a3e;
    }

    .info-card {
      display: flex;
      gap: 1rem;
      align-items: flex-start;
    }

    .info-icon {
      width: 40px;
      height: 40px;
      background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-size: 20px;
      flex-shrink: 0;
    }

    .info-content h4 {
      margin: 0 0 0.75rem 0;
      color: #333;
      font-size: 1.1rem;
      font-weight: 600;
    }

    .export-modal.dark .info-content h4 {
      color: #ffffff;
    }

    .info-items {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .info-item {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }

    .info-label {
      font-weight: 600;
      color: #6366f1;
      min-width: 80px;
      font-size: 0.9rem;
    }

    .info-text {
      color: #666;
      font-size: 0.9rem;
    }

    .export-modal.dark .info-text {
      color: #cccccc;
    }

    /* Export Section */
    .export-section {
      padding: 1.5rem;
    }

    .section-title {
      margin: 0 0 1.5rem 0;
      color: #333;
      font-size: 1.2rem;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .export-modal.dark .section-title {
      color: #ffffff;
    }

    .section-title i {
      color: #6366f1;
      font-size: 20px;
    }

    /* Export Cards */
    .export-cards {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .export-card {
      background: white;
      border: 1px solid #e9ecef;
      border-radius: 14px;
      overflow: hidden;
      transition: all 0.3s ease;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    }

    .export-modal.dark .export-card {
      background: #1e1e2e;
      border-color: #2a2a3e;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
    }

    .export-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 25px rgba(0, 0, 0, 0.08);
    }

    .export-modal.dark .export-card:hover {
      box-shadow: 0 8px 25px rgba(0, 0, 0, 0.3);
    }

    .card-header {
      display: flex;
      gap: 1rem;
      padding: 1.5rem;
      align-items: center;
    }

    .card-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      flex-shrink: 0;
    }

    .card-icon.complete {
      background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
      color: white;
    }

    .card-icon.simple {
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      color: white;
    }

    .card-icon.daily {
      background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
      color: white;
    }

    .card-icon.history {
      background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
      color: white;
    }

    .card-icon.info {
      background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
      color: white;
    }

    .card-info h4 {
      margin: 0 0 0.25rem 0;
      color: #333;
      font-size: 1.1rem;
      font-weight: 600;
    }

    .export-modal.dark .card-info h4 {
      color: #ffffff;
    }

    .card-info p {
      margin: 0;
      color: #666;
      font-size: 0.9rem;
    }

    .export-modal.dark .card-info p {
      color: #cccccc;
    }

    .card-content {
      padding: 0 1.5rem 1.5rem 1.5rem;
    }

    /* Period Selector */
    .period-selector {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin-bottom: 1rem;
    }

    .radio-option {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      cursor: pointer;
      padding: 0.5rem;
      border-radius: 8px;
      transition: background 0.2s;
    }

    .radio-option:hover {
      background: #f8f9fa;
    }

    .export-modal.dark .radio-option:hover {
      background: #2a2a3e;
    }

    .radio-option input[type="radio"] {
      cursor: pointer;
      accent-color: #6366f1;
    }

    .radio-label {
      color: #555;
      font-size: 0.95rem;
      font-weight: 500;
    }

    .export-modal.dark .radio-label {
      color: #cccccc;
    }

    /* Date Picker */
    .date-picker {
      margin: 1rem 0;
    }

    .daily-info {
      margin: 0 0 1rem 0;
      padding: 1rem;
      background: #fef3c7;
      border-left: 4px solid #f59e0b;
      border-radius: 6px;
      color: #92400e;
      font-size: 0.9rem;
      line-height: 1.5;
    }

    .export-modal.dark .daily-info {
      background: #4a3a1a;
      border-left-color: #f59e0b;
      color: #fcd34d;
    }

    .date-input-group {
      display: flex;
      gap: 1rem;
    }

    .date-input {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .date-input label {
      font-size: 0.85rem;
      color: #666;
      font-weight: 500;
    }

    .export-modal.dark .date-input label {
      color: #cccccc;
    }

    .date-input input {
      padding: 0.75rem;
      border: 1px solid #ddd;
      border-radius: 8px;
      font-size: 0.9rem;
      transition: border-color 0.2s;
    }

    .export-modal.dark .date-input input {
      background: #2a2a3e;
      border-color: #3a3a4e;
      color: #ffffff;
    }

    .date-input input:focus {
      outline: none;
      border-color: #6366f1;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
    }

    /* Export Button */
    .export-btn {
      width: 100%;
      padding: 0.875rem 1.5rem;
      border: none;
      border-radius: 10px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.95rem;
      transition: all 0.3s;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
      color: white;
    }

    .button-group {
      display: flex;
      gap: 0.75rem;
    }

    .button-group .export-btn {
      flex: 1;
    }

    .export-btn:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(99, 102, 241, 0.25);
    }

    .export-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      transform: none;
    }

    /* History */
    .empty-state {
      text-align: center;
      padding: 2rem;
      color: #999;
    }

    .export-modal.dark .empty-state {
      color: #666;
    }

    .empty-state i {
      font-size: 48px;
      margin-bottom: 1rem;
      opacity: 0.5;
    }

    .history-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .history-item {
      display: flex;
      align-items: center;
      padding: 1rem;
      background: #f8f9fa;
      border-radius: 10px;
      gap: 1rem;
    }

    .export-modal.dark .history-item {
      background: #2a2a3e;
    }

    .history-info {
      flex: 1;
    }

    .history-period {
      font-weight: 600;
      color: #333;
      margin-bottom: 0.25rem;
    }

    .export-modal.dark .history-period {
      color: #ffffff;
    }

    .history-date {
      font-size: 0.85rem;
      color: #666;
    }

    .export-modal.dark .history-date {
      color: #cccccc;
    }

    .history-type {
      flex-shrink: 0;
    }

    .type-badge {
      display: inline-block;
      padding: 0.4rem 0.8rem;
      border-radius: 6px;
      font-size: 0.8rem;
      font-weight: 500;
    }

    .type-badge.complete {
      background: #dbeafe;
      color: #0369a1;
    }

    .type-badge.simple {
      background: #dcfce7;
      color: #15803d;
    }

    .type-badge.daily {
      background: #fef3c7;
      color: #92400e;
    }

    .history-actions {
      display: flex;
      gap: 0.5rem;
      flex-shrink: 0;
    }

    .action-btn {
      padding: 0.5rem;
      border: 1px solid #ddd;
      background: white;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      color: #666;
    }

    .export-modal.dark .action-btn {
      background: #3a3a4e;
      border-color: #4a4a5e;
      color: #cccccc;
    }

    .action-btn.download:hover {
      background: #e8f4f8;
      border-color: #0369a1;
      color: #0369a1;
    }

    .export-modal.dark .action-btn.download:hover {
      background: #1a3a4a;
      border-color: #0369a1;
      color: #0369a1;
    }

    .action-btn.delete:hover {
      background: #fee2e2;
      border-color: #dc2626;
      color: #dc2626;
    }

    .export-modal.dark .action-btn.delete:hover {
      background: #4a1a1a;
      border-color: #dc2626;
      color: #dc2626;
    }

    /* Footer */
    .modal-footer {
      padding: 1rem;
      border-top: 1px solid #e9ecef;
      display: flex;
      justify-content: flex-end;
      gap: 1rem;
      background: #f8f9fa;
    }

    .export-modal.dark .modal-footer {
      background: #0f0f1a;
      border-top-color: #2a2a3e;
    }

    .btn-secondary {
      padding: 0.75rem 1.5rem;
      background: #f0f0f0;
      border: 1px solid #ddd;
      border-radius: 10px;
      cursor: pointer;
      font-weight: 600;
      transition: all 0.3s;
      color: #333;
    }

    .export-modal.dark .btn-secondary {
      background: #2a2a3e;
      border-color: #3a3a4e;
      color: #ffffff;
    }

    .btn-secondary:hover {
      background: #e0e0e0;
      transform: translateY(-1px);
    }

    .export-modal.dark .btn-secondary:hover {
      background: #3a3a4e;
    }

    /* Messages */
    .message {
      margin: 0 1.5rem 1.5rem 1.5rem;
      padding: 1rem;
      border-radius: 10px;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      font-weight: 500;
    }

    .message.error {
      background: #fee2e2;
      border-left: 4px solid #dc2626;
      color: #991b1b;
    }

    .message.success {
      background: #dcfce7;
      border-left: 4px solid #16a34a;
      color: #15803d;
    }

    .message i {
      flex-shrink: 0;
      font-size: 20px;
    }

    /* Animations */
    .bx-spin {
      animation: bx-spin 1s linear infinite;
    }

    @keyframes bx-spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    /* Responsive */
    @media (max-width: 768px) {
      .export-modal-overlay {
        padding: 0.5rem;
      }

      .export-modal {
        max-height: 95vh;
      }

      .modal-header {
        padding: 1rem;
        display: flex !important;
        flex-direction: row !important;
        align-items: center !important; /* Centra verticalmente icono, texto y X */
        justify-content: space-between !important;

      }

      .header-content {
        flex-direction: column;
        gap: 0.75rem;
        align-items: flex-start;
      }

      .header-text h2 {
        font-size: 1.3rem;
      }

      .card-header {
        padding: 1rem;
      }

      .card-content {
        padding: 0 1rem 1rem 1rem;
      }

      .date-input-group {
        flex-direction: column;
        gap: 0.75rem;
      }

      .history-item {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.75rem;
      }

      .history-actions {
        width: 100%;
        justify-content: flex-end;
      }
    }
  `]
})
export class ExportModalComponent implements OnInit, OnDestroy {
  storeId: number = 0;
  isLoading: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';
  isDarkMode: boolean = false;
  private darkModeObserver?: MutationObserver;

  // Complete Report
  completeReportPeriod: string = '30days';
  completeFromDate: string = '';
  completeToDate: string = '';

  // Simple Report
  simpleReportPeriod: string = '30days';
  simpleFromDate: string = '';
  simpleToDate: string = '';

  // Daily Report
  dailyFromDate: string = '';
  dailyToDate: string = '';

  // Dashboard Info Reports
  infoReportPeriod: string = '30days';
  infoFromDate: string = '';
  infoToDate: string = '';

  // History
  exportHistory: ExportHistory[] = [];

  constructor(
    public dialogRef: MatDialogRef<ExportModalComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { storeId: number },
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
    private apiConfig: ApiConfigService
  ) {
    this.storeId = data.storeId;
  }

  ngOnInit(): void {
    this.initializeDates();
    this.loadExportHistory();
    this.detectDarkMode();
  }

  detectDarkMode(): void {
    // Detectar si la clase .dark existe en el documento
    const homeContainer = document.querySelector('.home-container.dark');
    this.isDarkMode = !!homeContainer;

    // Si el usuario cambia el dark mode, detectarlo también
    // Observar cambios en la clase dark del contenedor principal
    this.darkModeObserver = new MutationObserver(() => {
      const homeContainer = document.querySelector('.home-container.dark');
      this.isDarkMode = !!homeContainer;
      this.cdr.detectChanges();
    });

    const mainContainer = document.querySelector('.home-container');
    if (mainContainer) {
      this.darkModeObserver.observe(mainContainer, { attributes: true, attributeFilter: ['class'] });
    }
  }

  ngOnDestroy(): void {
    if (this.darkModeObserver) {
      this.darkModeObserver.disconnect();
    }
  }

  initializeDates(): void {
    const today = new Date();
    const thirtyDaysAgo = new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000);

    this.completeToDate = this.formatDateForInput(today);
    this.completeFromDate = this.formatDateForInput(thirtyDaysAgo);

    this.simpleToDate = this.formatDateForInput(today);
    this.simpleFromDate = this.formatDateForInput(thirtyDaysAgo);

    this.infoToDate = this.formatDateForInput(today);
    this.infoFromDate = this.formatDateForInput(thirtyDaysAgo);

    // Daily report uses today's date
    this.dailyFromDate = this.formatDateForInput(today);
    this.dailyToDate = this.formatDateForInput(today);
  }

  formatDateForInput(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  onPeriodChange(reportType: string): void {
    if (reportType === 'complete') {
      if (this.completeReportPeriod === '30days') {
        this.initializeDates();
      } else if (!this.completeFromDate || !this.completeToDate) {
        this.initializeDates();
      }
    } else if (reportType === 'simple') {
      if (this.simpleReportPeriod === '30days') {
        this.initializeDates();
      } else if (!this.simpleFromDate || !this.simpleToDate) {
        this.initializeDates();
      }
    } else if (reportType === 'info') {
      if (this.infoReportPeriod === '30days') {
        this.initializeDates();
      } else if (!this.infoFromDate || !this.infoToDate) {
        this.initializeDates();
      }
    }
  }

  private getPeriodMode(reportType: 'COMPLETE' | 'SIMPLE'): 'custom' | 'default' {
    return reportType === 'COMPLETE'
      ? (this.completeReportPeriod === 'custom' ? 'custom' : 'default')
      : (this.simpleReportPeriod === 'custom' ? 'custom' : 'default');
  }

  downloadReport(reportType: 'COMPLETE' | 'SIMPLE'): void {
    this.errorMessage = '';
    this.successMessage = '';

    const fromDate = reportType === 'COMPLETE' ? this.completeFromDate : this.simpleFromDate;
    const toDate = reportType === 'COMPLETE' ? this.completeToDate : this.simpleToDate;

    if (!this.validateDates(fromDate, toDate)) {
      this.errorMessage = 'Por favor, selecciona un rango de fechas válido';
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) {
      this.errorMessage = 'Token no encontrado. Por favor, inicia sesión nuevamente.';
      console.error('Token no encontrado en localStorage');
      return;
    }

    this.isLoading = true;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    const params = {
      storeId: this.storeId.toString(),
      dateFrom: fromDate,
      dateTo: toDate,
      reportType: reportType,
      periodMode: this.getPeriodMode(reportType)
    };

    const baseUrl = this.apiConfig.getBaseUrl();
    console.log('Enviando solicitud de exportación:', {
      url: `${baseUrl}/api/export/sales-report`,
      params,
      hasToken: !!token
    });

    this.http.post(`${baseUrl}/api/export/sales-report`, {}, {
      headers,
      params,
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        this.ngZone.run(() => {
          this.handleDownload(blob, reportType, fromDate, toDate, this.getPeriodMode(reportType));
          this.isLoading = false;
          this.successMessage = '✅ Reporte descargado exitosamente';
          this.cdr.detectChanges();
          setTimeout(() => this.loadExportHistory(), 500);
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.isLoading = false;
          console.error('Error descargando reporte:', err);

          if (err.status === 403) {
            this.errorMessage = 'Acceso denegado (403). Verifica tus permisos.';
          } else if (err.status === 401) {
            this.errorMessage = 'No autenticado. Por favor, inicia sesión nuevamente.';
          } else if (err.status === 400) {
            this.errorMessage = 'Parámetros inválidos. Verifica las fechas.';
          } else {
            this.errorMessage = `Error al generar el reporte (${err.status}). Intenta nuevamente.`;
          }
          this.cdr.detectChanges();
        });
      }
    });
  }

  downloadDailyReport(): void {
    this.errorMessage = '';
    this.successMessage = '';

    const token = localStorage.getItem('token');
    if (!token) {
      this.errorMessage = 'Token no encontrado. Por favor, inicia sesión nuevamente.';
      console.error('Token no encontrado en localStorage');
      return;
    }

    this.isLoading = true;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    const params = {
      storeId: this.storeId.toString(),
      dateFrom: this.dailyFromDate,
      dateTo: this.dailyToDate,
      reportType: 'DAILY',
      periodMode: 'default'
    };

    const baseUrl = this.apiConfig.getBaseUrl();

    console.log('Enviando solicitud de reporte diario:', {
      url: `${baseUrl}/api/export/sales-report`,
      params,
      hasToken: !!token
    });

    this.http.post(`${baseUrl}/api/export/sales-report`, {}, {
      headers,
      params,
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        this.ngZone.run(() => {
          this.handleDownload(blob, 'DAILY', this.dailyFromDate, this.dailyToDate, 'default');
          this.isLoading = false;
          this.successMessage = '✅ Reporte diario descargado exitosamente';
          this.cdr.detectChanges();
          setTimeout(() => this.loadExportHistory(), 500);
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.isLoading = false;
          console.error('Error descargando reporte diario:', err);

          if (err.status === 403) {
            this.errorMessage = 'Acceso denegado (403). Verifica tus permisos.';
          } else if (err.status === 401) {
            this.errorMessage = 'No autenticado. Por favor, inicia sesión nuevamente.';
          } else if (err.status === 400) {
            this.errorMessage = 'Parámetros inválidos. Verifica las fechas.';
          } else {
            this.errorMessage = `Error al generar el reporte (${err.status}). Intenta nuevamente.`;
          }
          this.cdr.detectChanges();
        });
      }
    });
  }

  downloadInfoReport(periodType: 'range' | 'all'): void {
    this.errorMessage = '';
    this.successMessage = '';

    const token = localStorage.getItem('token');
    if (!token) {
      this.errorMessage = 'Token no encontrado. Por favor, inicia sesión nuevamente.';
      console.error('Token no encontrado en localStorage');
      return;
    }

    const fromDate = this.infoFromDate;
    const toDate = this.infoToDate;

    if (periodType === 'range' && !this.validateDates(fromDate, toDate)) {
      this.errorMessage = 'Por favor, selecciona un rango de fechas válido';
      return;
    }

    this.isLoading = true;

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    let endpoint: string;
    const baseUrl = this.apiConfig.getBaseUrl();
    const periodMode = periodType === 'range' && this.infoReportPeriod === 'custom' ? 'custom' : 'default';
    if (periodType === 'range') {
      endpoint = `${baseUrl}/api/reports/export/excel/${this.storeId}?startDate=${fromDate}&endDate=${toDate}&periodMode=${periodMode}`;
    } else {
      endpoint = `${baseUrl}/api/reports/export/excel/${this.storeId}/all`;
    }

    console.log('Exportando reportes de información:', {
      url: endpoint,
      periodType,
      hasToken: !!token
    });

    this.http.get<Blob>(endpoint, {
      headers,
      responseType: 'blob' as 'json'
    }).subscribe({
      next: (blob: any) => {
        this.ngZone.run(() => {
          const reportType = periodType === 'range' ? 'INFO_RANGE' : 'INFO_ALL';
          this.handleDownload(blob, reportType, fromDate, toDate, periodMode);
          this.isLoading = false;
          this.successMessage = '✅ Reportes de información descargados exitosamente';
          this.cdr.detectChanges();
          setTimeout(() => this.loadExportHistory(), 500);
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.isLoading = false;
          console.error('Error descargando reportes de información:', err);

          if (err.status === 403) {
            this.errorMessage = 'Acceso denegado (403). Verifica tus permisos.';
          } else if (err.status === 401) {
            this.errorMessage = 'No autenticado. Por favor, inicia sesión nuevamente.';
          } else if (err.status === 400) {
            this.errorMessage = 'Parámetros inválidos. Verifica las fechas.';
          } else {
            this.errorMessage = `Error al generar el reporte (${err.status}). Intenta nuevamente.`;
          }
          this.cdr.detectChanges();
        });
      }
    });
  }

  handleDownload(blob: Blob, reportType: string, dateFrom: string, dateTo: string, periodMode: 'custom' | 'default' = 'default'): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;

    const reportKey = reportType.toLowerCase().replace(/_/g, '-');
    const periodLabel = periodMode === 'custom' ? 'periodo-personalizado' : 'periodo';
    const dateSuffix = [dateFrom, dateTo].filter(Boolean).join('_a_');
    const fileName = dateSuffix
      ? `reporte-${reportKey}-${periodLabel}-${dateSuffix}.xlsx`
      : `reporte-${reportKey}-${periodLabel}.xlsx`;

    link.download = fileName;
    link.click();
    window.URL.revokeObjectURL(url);
  }

  validateDates(fromDate: string, toDate: string): boolean {
    if (!fromDate || !toDate) return false;
    return new Date(fromDate) <= new Date(toDate);
  }

  loadExportHistory(): void {
    const baseUrl = this.apiConfig.getBaseUrl();
    const token = localStorage.getItem('token');
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.get<ExportHistory[]>(`${baseUrl}/api/export/history?storeId=${this.storeId}`, { headers })
      .subscribe({
        next: (history) => {
          this.ngZone.run(() => {
            this.exportHistory = history;
            this.cdr.detectChanges();
          });
        },
        error: (err) => {
          console.error('Error cargando historial:', err);
        }
      });
  }

  redownloadReport(item: ExportHistory): void {
    const baseUrl = this.apiConfig.getBaseUrl();
    const token = localStorage.getItem('token');
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.get(`${baseUrl}/api/export/download/${item.id}`, {
      headers,
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        this.ngZone.run(() => {
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = item.fileName;
          link.click();
          window.URL.revokeObjectURL(url);
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.errorMessage = 'Error al descargar el archivo';
          console.error('Error:', err);
          this.cdr.detectChanges();
        });
      }
    });
  }

  deleteReport(item: ExportHistory): void {
    if (!confirm('¿Estás seguro de que deseas eliminar este reporte?')) return;

    const baseUrl = this.apiConfig.getBaseUrl();
    const token = localStorage.getItem('token');
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    this.http.delete(`${baseUrl}/api/export/${item.id}`, { headers })
      .subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.successMessage = 'Reporte eliminado';
            this.cdr.detectChanges();
            this.loadExportHistory();
          });
        },
        error: (err) => {
          this.ngZone.run(() => {
            this.errorMessage = 'Error al eliminar el reporte';
            console.error('Error:', err);
            this.cdr.detectChanges();
          });
        }
      });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}

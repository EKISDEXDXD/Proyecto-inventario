import { Injectable } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { ApiConfigService } from '../auth/api-config.service';

// Conecta al WebSocket del backend y permite suscribirse a las notificaciones de pago de una tienda
@Injectable({ providedIn: 'root' })
export class PaymentNotificationsSocketService {
  private client: Client | null = null;
  private subscription: StompSubscription | null = null;

  constructor(private apiConfig: ApiConfigService) {}

  // Convierte la URL http(s) del backend a ws(s):// para el WebSocket nativo (sin SockJS)
  private getSocketUrl(): string {
    const httpUrl = this.apiConfig.getApiUrl('/ws-payment-notifications');
    const absoluteUrl = httpUrl.startsWith('http') ? httpUrl : `${window.location.origin}${httpUrl}`;
    return absoluteUrl.replace(/^http/, 'ws');
  }

  private ensureConnected(): Promise<void> {
    if (this.client?.active) {
      return Promise.resolve();
    }

    return new Promise((resolve, reject) => {
      this.client = new Client({
        brokerURL: this.getSocketUrl(),
        reconnectDelay: 5000,
        onConnect: () => resolve(),
        onStompError: (frame) => {
          console.error('Error STOMP en notificaciones de pago:', frame.headers['message']);
          reject(frame);
        }
      });
      this.client.activate();
    });
  }

  // Se suscribe al canal de una tienda; onNotification se llama con cada notificación nueva
  async subscribeToStore(storeId: number, onNotification: (notification: any) => void): Promise<void> {
    await this.ensureConnected();
    this.unsubscribe();

    this.subscription = this.client!.subscribe(`/topic/payment-notifications/${storeId}`, (message: IMessage) => {
      try {
        onNotification(JSON.parse(message.body));
      } catch (e) {
        console.error('No se pudo parsear la notificación de pago recibida:', e);
      }
    });
  }

  unsubscribe(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
  }

  disconnect(): void {
    this.unsubscribe();
    this.client?.deactivate();
    this.client = null;
  }
}

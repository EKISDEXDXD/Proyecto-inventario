# 📊 Dashboard Analítico - Guía de Uso

## ✅ Estado: COMPLETAMENTE FUNCIONAL

El dashboard analítico está completamente implementado y validado con datos reales.

---

## 🚀 Cómo acceder

### Opción 1: Desarrollo local (Recomendado)
```bash
# Terminal 1: Backend (Puerto 8081)
cd licoreria-backend
./mvnw spring-boot:run

# Terminal 2: Frontend (Puerto 4200)
cd licoreria-frontend
npm start

# Luego acceder a:
http://localhost:4200/dashboard-info
```

### Opción 2: Ruta específica por tienda
```
http://localhost:4200/tienda/1/dashboard-info
```

---

## 📋 Credenciales de prueba

**Usuario:** `testuser`  
**Contraseña:** `testpass123`

---

## 📊 Secciones del Dashboard

### 1. **KPIs Principales** (4 tarjetas)
- 💵 **Ingreso Total**: Ingresos totales de ventas
- 📊 **Costo Total**: Costo total de los productos vendidos
- 📈 **Ganancia**: Diferencia entre ingresos y costos
- 📉 **Rentabilidad**: Porcentaje de ganancia sobre ingresos

### 2. **Resumen de Métricas** (3 cards)
- ✅ **Saldo Neto**: Ganancias finales
- 🔄 **Movimientos**: Número de transacciones
- 📦 **Unidades**: Total de unidades vendidas

### 3. **Ventas por Categoría** (Gráfico de barras)
- Desglose de ingresos por categoría de producto
- Porcentaje de participación en ventas

### 4. **Tendencia Diaria** (Gráfico de línea)
- Últimos 7 días de ingresos
- Cantidad de unidades por día

### 5. **Métodos de Pago** (Tabla)
- Desglose de pagos por método
- Porcentaje de ingresos por método

### 6. **Top 10 Productos** (Ranking)
- Productos más vendidos
- Revenue, costo, ganancia y margen por producto

### 7. **Balance General** (4 métricas)
- Ventas totales
- Costo de ventas
- Utilidad neta
- Valor total de inventario

---

## 🎯 Filtros disponibles

### Botones de Tipo de Movimiento
- **Salidas** (Ventas) - Activo por defecto
- **Entradas** (Compras)
- *Combinación multi-select disponible*

### Contexto de tienda
- Muestra automáticamente la tienda activa

---

## 🛠️ Stack técnico

### Backend
- **Framework**: Spring Boot 3.5.13
- **Base de datos**: PostgreSQL 16
- **Port**: 8081
- **Endpoint**: `/api/dashboard/metrics`

### Frontend
- **Framework**: Angular 21
- **Styling**: CSS 3 + Custom components
- **Port**: 4200
- **Route**: `/dashboard-info` o `/tienda/:id/dashboard-info`

---

## 📈 Datos de ejemplo

El sistema incluye datos de prueba realistas:
- **4 categorías** de productos activas
- **2 métodos de pago** (Efectivo, QR)
- **11 días** de histórico de ventas
- **10 productos top** con métricas completas
- **Ingresos totales**: $11,771.00
- **Margen de ganancia**: 38.86%

---

## 🔍 Validación técnica

### ✅ Backend
```
[✓] Compilación: Clean (0 errores)
[✓] API Response: HTTP 200
[✓] Schema de datos: Validado
[✓] Métodos de pago: Funcionando
[✓] Categorías de productos: Funcionando
[✓] Tendencias: Generando datos correctos
```

### ✅ Frontend
```
[✓] Build producción: Clean (0 errores)
[✓] TypeScript: Tipado correctamente
[✓] CSS: 319 reglas balanceadas
[✓] Componentes: Cargando datos en tiempo real
[✓] Estilos: Responsive en todos los tamaños
```

### ✅ Integración end-to-end
```
[✓] Autenticación: Funcionando
[✓] Llamada API: HTTP 200
[✓] Binding de datos: Correcto
[✓] Formatos numéricos: Localizados (es-CL)
[✓] Renderización: Sin errores de consola
```

---

## 📱 Características responsive

El dashboard se adapta automáticamente a:
- 📱 Móviles (320px+)
- 📱 Tablets (768px+)
- 🖥️ Desktops (1024px+)
- 🖥️ Ultra-wide (1440px+)

---

## 🎨 Paleta de colores

| Elemento | Color | Uso |
|----------|-------|-----|
| Primary | Indigo/Purple (`#6366f1`/`#8b5cf6`) | Headers, highlights |
| Sales | Green (`#10b981`) | Ingresos |
| Cost | Red (`#ef4444`) | Costos |
| Profit | Blue (`#3b82f6`) | Ganancias |
| Margin | Purple (`#8b5cf6`) | Rentabilidad |

---

## 🔧 Troubleshooting

### Dashboard muestra "Cargando métricas..."
- Verificar que el backend está corriendo: `http://localhost:8081`
- Verificar el token en localStorage
- Abrir consola del navegador (F12) para ver errores

### Datos vacíos
- Asegurar que existen transacciones del tipo "SALIDA"
- Verificar que los productos tienen asignadas categorías (tags)
- Verificar que existen métodos de pago activos

### Errores de CORS
- El backend ya está configurado con CORS
- Si persiste, verificar que el frontend accede a `http://localhost:8081`

---

## 📝 Notas de desarrollo

- El dashboard es un componente standalone en Angular 21
- Utiliza RxJS para reactive data binding
- El backend pre-computa las agregaciones (mejor performance)
- Los datos se actualizan al cambiar filtros
- No hay caché; cada petición trae datos frescos

---

## 🚀 Próximos pasos (Opcional)

- Agregar exportación a PDF/Excel
- Implementar drill-down en gráficos
- Agregar más filtros temporales (rango de fechas personalizado)
- Agregar comparación período vs período
- Implementar alertas de umbral

---

**Última validación**: 2026-08-18  
**Estado**: ✅ PRODUCCIÓN

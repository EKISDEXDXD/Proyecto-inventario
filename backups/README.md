# 📦 Backups - Licoreria Inventario

## 📋 Contenido del Backup

Cada archivo de backup (`backup_YYYY-MM-DD_HH-MM-SS.sql`) contiene:

### Base de Datos:
- ✅ Todos los **usuarios** (admin, managers)
- ✅ Todas las **tiendas/sucursales**
- ✅ Todos los **productos** e inventario
- ✅ Todas las **transacciones/movimientos**
- ✅ Todos los **métodos de pago**
- ✅ Todos los **costos administrativos**
- ✅ Todos los **reportes generados** (metadata)
- ✅ Todas las **imágenes de productos** (referencias)

### Archivos Separados (en carpetas raíz):
```
proyecto-inventario/
├── product-images/      ← Imágenes de productos físicas
├── exports/             ← Reportes Excel generados
└── backups/
    ├── backup_2026-05-28_22-22-41.sql  ← Base de datos
    └── README.md        ← Este archivo
```

---

## 🔄 Cómo Restaurar los Datos

### Opción 1: Restaurar en la misma máquina

**Paso 1:** Asegúrate que Docker está corriendo:
```bash
docker-compose up -d
```

**Paso 2:** Restaura el backup:
```bash
# Cambia el nombre del archivo al que quieras restaurar
docker exec licoreria-postgres psql -U licoreria_user -d licoreria_db < backups/backup_2026-05-28_22-22-41.sql
```

**Paso 3:** Verifica que funcionó:
```bash
docker exec licoreria-postgres psql -U licoreria_user -d licoreria_db -c "SELECT COUNT(*) as total_usuarios FROM users;"
```

---

### Opción 2: Restaurar en otra máquina

**Paso 1:** Copia TODO el proyecto a la nueva máquina:
```bash
# En la máquina vieja
scp -r /home/usuario/Proyecto-inventario usuario@nueva-maquina:/home/usuario/
```

**Paso 2:** Accede a la nueva máquina:
```bash
cd /home/usuario/Proyecto-inventario
```

**Paso 3:** Levanta Docker:
```bash
docker-compose up -d
```

**Paso 4:** Restaura el backup:
```bash
docker exec licoreria-postgres psql -U licoreria_user -d licoreria_db < backups/backup_2026-05-28_22-22-41.sql
```

---

## 📁 Estructura de Datos Respaldada

```
BACKUP CONTIENE:
├── Base de Datos PostgreSQL
│   ├── users (Usuarios del sistema)
│   ├── stores (Tiendas/sucursales)
│   ├── products (Productos)
│   ├── transaction (Movimientos de inventario)
│   ├── product_image (Metadata de imágenes)
│   ├── payment_methods (Métodos de pago)
│   ├── administrative_cost (Costos fijos)
│   └── exported_report (Reportes generados - metadata)
│
NO CONTIENE (están en carpetas):
├── product-images/        ← Archivos .jpg, .png físicos
└── exports/Reports/       ← Archivos .xlsx físicos
```

---

## ⚠️ Importante

- **Usa el backup más reciente** (la fecha más nueva)
- **Verifica que tienes espacio en disco** antes de restaurar
- **Haz backup regularmente** cuando tengas cambios importantes
- **Guarda este proyecto en Git** para máxima seguridad

---

## 🆘 Troubleshooting

### Error: "psql: command not found"
PostgreSQL client no está instalado. Instala:
```bash
sudo apt-get install postgresql-client  # Ubuntu/Debian
brew install postgresql                 # Mac
```

### Error: "FATAL: password authentication failed"
Verifica las credenciales en `docker-compose.yml`:
```yaml
POSTGRES_USER: licoreria_user
POSTGRES_PASSWORD: licoreria_password
```

### Error: "database does not exist"
La BD se crea automáticamente. Si falla, recrea los contenedores:
```bash
docker-compose down -v
docker-compose up -d
```

---

**Última actualización:** 2026-05-28

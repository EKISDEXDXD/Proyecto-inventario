package com.inventario.licoreria.modules.export.service;

import com.inventario.licoreria.modules.export.model.ExportedReport;
import com.inventario.licoreria.modules.export.repository.ExportedReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

@Service
public class ExportedReportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportedReportService.class);
    private final ExportedReportRepository exportedReportRepository;

    @Value("${export.file.path:}")
    private String exportFilePath;

    public ExportedReportService(ExportedReportRepository exportedReportRepository) {
        this.exportedReportRepository = exportedReportRepository;
    }

    /**
     * Guarda un reporte generado en el sistema de archivos y registra la metadata en BD
     */
    public ExportedReport saveReportFile(Long storeId, byte[] fileContent, String reportType, 
                                         String dateFrom, String dateTo) throws IOException {
        logger.info("Iniciando guardado de reporte: storeId={}, type={}, size={} bytes", storeId, reportType, fileContent.length);
        
        // Usar ruta absoluta en temp directory si no está configurada
        String basePath = exportFilePath;
        logger.debug("exportFilePath configurado: '{}'", basePath);
        
        // Si está vacío o es null, usar directorio temporal
        if (basePath == null || basePath.trim().isEmpty()) {
            // Usar el directorio de la aplicación/proyecto
            basePath = System.getProperty("user.dir") + File.separator + "exports";
            logger.info("exportFilePath vacío, usando ruta por defecto: {}", basePath);
        } else if (basePath.startsWith("./")) {
            // Convertir rutas relativas a absolutas
            basePath = System.getProperty("user.dir") + File.separator + basePath.substring(2);
            logger.info("Convirtiendo ruta relativa a absoluta: {}", basePath);
        }
        
        // Asegurar que la ruta sea absoluta
        File baseDirFile = new File(basePath);
        if (!baseDirFile.isAbsolute()) {
            String absolutePath = System.getProperty("user.dir") + File.separator + basePath;
            logger.info("Ruta relativa detectada, convirtiendo a absoluta: {} -> {}", basePath, absolutePath);
            basePath = absolutePath;
        }
        
        // Crear directorio base si no existe
        File baseDir = new File(basePath);
        logger.debug("Verificando directorio base: {}", basePath);
        
        if (!baseDir.exists()) {
            logger.info("Directorio base no existe, creando: {}", basePath);
            if (!baseDir.mkdirs()) {
                String error = "No se pudo crear el directorio base: " + basePath;
                logger.error(error);
                throw new IOException(error);
            }
            logger.info("Directorio base creado exitosamente");
        } else {
            logger.debug("Directorio base ya existe");
        }
        
        // Crear directorio para la tienda
        String storePath = basePath + File.separator + "store_" + storeId;
        File storeDir = new File(storePath);
        logger.debug("Verificando directorio de tienda: {}", storePath);
        
        if (!storeDir.exists()) {
            logger.info("Directorio de tienda no existe, creando: {}", storePath);
            if (!storeDir.mkdirs()) {
                String error = "No se pudo crear el directorio de tienda: " + storePath;
                logger.error(error);
                throw new IOException(error);
            }
            logger.info("Directorio de tienda creado exitosamente");
        } else {
            logger.debug("Directorio de tienda ya existe");
        }

        // Generar nombre único del archivo
        String fileName = generateFileName(reportType, dateFrom, dateTo);
        String filePath = storePath + File.separator + fileName;
        logger.info("Guardando archivo en: {}", filePath);

        // Guardar archivo en sistema de archivos
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(fileContent);
            fos.flush();
            logger.info("Archivo guardado exitosamente: {} bytes en {}", fileContent.length, filePath);
        } catch (IOException e) {
            String error = "Error al guardar archivo: " + fileName + " en " + filePath;
            logger.error(error + " - Causa: " + e.getMessage(), e);
            throw new IOException(error, e);
        }

        // Crear registro en BD
        ExportedReport report = new ExportedReport(
            UUID.randomUUID().toString(),
            storeId,
            fileName,
            filePath,
            LocalDateTime.now(),
            dateFrom,
            dateTo,
            reportType,
            (long) fileContent.length
        );

        logger.info("Registrando reporte en BD: id={}, fileName={}", report.getId(), report.getFileName());
        ExportedReport savedReport = exportedReportRepository.save(report);
        logger.info("Reporte guardado exitosamente en BD");
        
        return savedReport;
    }

    /**
     * Obtiene el contenido del archivo desde el sistema de archivos
     */
    public byte[] getReportFile(String reportId) throws IOException {
        logger.info("Obteniendo archivo de reporte: {}", reportId);
        ExportedReport report = exportedReportRepository.findById(reportId).orElse(null);
        
        if (report == null || report.isDeleted()) {
            logger.warn("Reporte no encontrado o eliminado: {}", reportId);
            throw new IOException("Reporte no encontrado");
        }

        try {
            logger.debug("Leyendo contenido del archivo: {}", report.getFilePath());
            byte[] content = Files.readAllBytes(Paths.get(report.getFilePath()));
            logger.info("Archivo leído exitosamente: {} bytes", content.length);
            return content;
        } catch (IOException e) {
            logger.error("Error al leer archivo: {}", report.getFilePath(), e);
            throw new IOException("Error al leer archivo: " + report.getFilePath(), e);
        }
    }

    /**
     * Elimina un reporte (borrado lógico en BD + eliminación física de archivo)
     */
    public void deleteReport(String reportId) throws IOException {
        logger.info("Eliminando reporte: {}", reportId);
        ExportedReport report = exportedReportRepository.findById(reportId).orElse(null);
        
        if (report == null) {
            logger.warn("Reporte no encontrado para eliminar: {}", reportId);
            throw new IOException("Reporte no encontrado");
        }

        // Borrado lógico en BD
        report.setDeleted(true);
        exportedReportRepository.save(report);
        logger.debug("Reporte marcado como eliminado en BD: {}", reportId);

        // Intentar eliminar archivo físicamente
        try {
            if (Files.deleteIfExists(Paths.get(report.getFilePath()))) {
                logger.info("Archivo eliminado exitosamente: {}", report.getFilePath());
            } else {
                logger.debug("Archivo no existía para eliminar: {}", report.getFilePath());
            }
        } catch (IOException e) {
            // Log pero no falla, el archivo puede no existir
            logger.warn("No se pudo eliminar archivo: {} - {}", report.getFilePath(), e.getMessage());
        }
    }

    /**
     * Genera nombre descriptivo del archivo
     */
    private String generateFileName(String reportType, String dateFrom, String dateTo) {
        String type;
        String period;
        
        if ("DAILY".equals(reportType)) {
            type = "diario";
            period = dateFrom; // Ej: 2026-04-16
        } else {
            type = "COMPLETE".equals(reportType) ? "completo" : "resumido";
            period = formatFilePeriod(dateFrom, dateTo);
        }
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("reporte-%s-%s-%s.xlsx", type, period, timestamp);
    }

    /**
     * Formatea el período para el nombre del archivo
     */
    private String formatFilePeriod(String dateFrom, String dateTo) {
        try {
            YearMonth from = YearMonth.parse(dateFrom.substring(0, 7));
            YearMonth to = YearMonth.parse(dateTo.substring(0, 7));
            
            if (from.equals(to)) {
                return from.toString(); // Ej: 2026-01
            } else {
                return from.toString() + "-a-" + to.toString(); // Ej: 2026-01-a-2026-03
            }
        } catch (Exception e) {
            return "periodo-desconocido";
        }
    }

    /**
     * Limpia archivos huérfanos y registros marcados como eliminados más antiguos de X días
     */
    public void cleanupOldReports(int daysOld) {
        // Esta función puede ejecutarse periódicamente (Scheduled Task)
        // Por ahora solo registra la lógica
        System.out.println("Limpieza de reportes más antiguos de " + daysOld + " días ejecutada");
    }
}

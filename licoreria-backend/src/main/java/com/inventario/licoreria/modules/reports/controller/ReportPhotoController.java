package com.inventario.licoreria.modules.reports.controller;

import com.inventario.licoreria.modules.reports.dto.ReportDTO;
import com.inventario.licoreria.modules.reports.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador público para servir fotos de reportes
 * No requiere autenticación para permitir que <img src> funcione
 */
@RestController
@RequestMapping("/api/photos")
public class ReportPhotoController {

    private final ReportService reportService;

    public ReportPhotoController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Obtener la foto de un reporte (endpoint PÚBLICO)
     */
    @GetMapping("/reports/{reportId}")
    public ResponseEntity<byte[]> getReportPhoto(@PathVariable Long reportId) {
        try {
            byte[] photoData = reportService.getReportPhoto(reportId);
            
            if (photoData == null || photoData.length == 0) {
                return ResponseEntity.notFound().build();
            }

            ReportDTO report = reportService.getReportById(reportId);
            if (report == null) {
                return ResponseEntity.notFound().build();
            }
            
            String mimeType = report.getPhotoMimeType() != null ? report.getPhotoMimeType() : MediaType.IMAGE_JPEG_VALUE;

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + report.getPhotoFileName() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(photoData);
        } catch (Exception e) {
            System.err.println("Error obteniendo foto del reporte " + reportId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }
}

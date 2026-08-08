package com.inventario.licoreria.modules.reports.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inventario.licoreria.modules.reports.dto.ReportDTO;
import com.inventario.licoreria.modules.reports.service.ReportService;
import com.inventario.licoreria.security.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final JwtUtil jwtUtil;

    public ReportController(ReportService reportService, JwtUtil jwtUtil) {
        this.reportService = reportService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Crear un nuevo reporte (sin foto)
     */
    @PostMapping(value = "/{storeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportDTO> createReport(
            @PathVariable Long storeId,
            @RequestBody ReportDTO reportDTO,
            HttpServletRequest request) throws IOException {

        Long userId = extractUserIdFromToken(request);
        
        ReportDTO report = reportService.createReport(storeId, userId, reportDTO.getTitle(), reportDTO.getDescription(), reportDTO.getReportDate(), reportDTO.getColor(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    /**
     * Obtener un reporte por ID
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<ReportDTO> getReport(@PathVariable Long reportId) {
        ReportDTO report = reportService.getReportById(reportId);
        return ResponseEntity.ok(report);
    }

    /**
     * Obtener la foto de un reporte (endpoint público para permitir cargas en <img src>)
     */
    @GetMapping("/{reportId}/photo")
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
            // Log del error para debugging
            System.err.println("Error obteniendo foto del reporte " + reportId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Obtener reportes de una tienda con paginación
     */
    @GetMapping("/store/{storeId}")
    public ResponseEntity<Page<ReportDTO>> getReportsByStore(
            @PathVariable Long storeId,
            Pageable pageable) {
        Page<ReportDTO> reports = reportService.getReportsByStore(storeId, pageable);
        return ResponseEntity.ok(reports);
    }

    /**
     * Obtener todos los reportes de una tienda en un rango de fechas
     */
    @GetMapping("/store/{storeId}/range")
    public ResponseEntity<List<ReportDTO>> getReportsByDateRange(
            @PathVariable Long storeId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        List<ReportDTO> reports = reportService.getReportsByStoreAndDateRange(storeId, start, end);
        return ResponseEntity.ok(reports);
    }

    /**
     * Obtener todos los reportes de una tienda
     */
    @GetMapping("/store/{storeId}/all")
    public ResponseEntity<List<ReportDTO>> getAllReportsByStore(@PathVariable Long storeId) {
        List<ReportDTO> reports = reportService.getAllReportsByStore(storeId);
        return ResponseEntity.ok(reports);
    }

    /**
     * Actualizar un reporte (sin foto)
     */
    @PutMapping(value = "/{reportId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportDTO> updateReport(
            @PathVariable Long reportId,
            @RequestBody ReportDTO reportDTO) throws IOException {

        ReportDTO currentReport = reportService.getReportById(reportId);
        String finalTitle = reportDTO.getTitle() != null ? reportDTO.getTitle() : currentReport.getTitle();
        String finalDescription = reportDTO.getDescription() != null ? reportDTO.getDescription() : currentReport.getDescription();
        LocalDate finalDate = reportDTO.getReportDate() != null ? reportDTO.getReportDate() : currentReport.getReportDate();
        String finalColor = reportDTO.getColor() != null ? reportDTO.getColor() : currentReport.getColor();

        ReportDTO updatedReport = reportService.updateReport(reportId, finalTitle, finalDescription, finalDate, finalColor, null);
        return ResponseEntity.ok(updatedReport);
    }

    /**
     * Eliminar un reporte
     */
    @DeleteMapping("/{reportId}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long reportId) {
        reportService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extraer el ID de usuario del token JWT
     */
    private Long extractUserIdFromToken(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null) {
            return jwtUtil.extractUserId(token);
        }
        return null;
    }

    /**
     * Extraer el token del header Authorization
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

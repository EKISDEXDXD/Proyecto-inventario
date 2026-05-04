package com.inventario.licoreria.modules.reports.controller;

import com.inventario.licoreria.modules.reports.service.ReportExportService;
import com.inventario.licoreria.modules.export.service.ExportedReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/reports/export")
public class ReportExportController {

    private final ReportExportService reportExportService;
    private final ExportedReportService exportedReportService;

    public ReportExportController(ReportExportService reportExportService,
                                  ExportedReportService exportedReportService) {
        this.reportExportService = reportExportService;
        this.exportedReportService = exportedReportService;
    }

    /**
     * Exportar reportes en un rango de fechas a Excel
     * Guarda en la tabla ExportedReport para el historial
     */
    @GetMapping("/excel/{storeId}")
    public ResponseEntity<byte[]> exportReportsToExcel(
            @PathVariable Long storeId,
            @RequestParam String startDate,
            @RequestParam String endDate) throws IOException {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        byte[] excelFile = reportExportService.exportReportsToExcel(storeId, start, end);

        // Guardar en ExportedReport (historial de exportaciones)
        var exportedReport = exportedReportService.saveReportFile(
                storeId, excelFile, "REPORTS_RANGE", start.toString(), end.toString()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", exportedReport.getFileName());
        headers.setContentLength(excelFile.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelFile);
    }

    /**
     * Exportar todos los reportes de una tienda a Excel
     * Guarda en la tabla ExportedReport para el historial
     */
    @GetMapping("/excel/{storeId}/all")
    public ResponseEntity<byte[]> exportAllReportsToExcel(@PathVariable Long storeId) throws IOException {
        
        LocalDate today = LocalDate.now();
        byte[] excelFile = reportExportService.exportAllReportsToExcel(storeId);

        // Guardar en ExportedReport (historial de exportaciones)
        var exportedReport = exportedReportService.saveReportFile(
                storeId, excelFile, "REPORTS_ALL", "2000-01-01", today.toString()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", exportedReport.getFileName());
        headers.setContentLength(excelFile.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelFile);
    }
}

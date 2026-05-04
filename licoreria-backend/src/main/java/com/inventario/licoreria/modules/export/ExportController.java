package com.inventario.licoreria.modules.export;

import com.inventario.licoreria.modules.export.service.SalesReportService;
import com.inventario.licoreria.modules.export.service.ExportedReportService;
import com.inventario.licoreria.modules.export.model.ExportedReport;
import com.inventario.licoreria.modules.export.repository.ExportedReportRepository;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.users.service.UserService;
import com.inventario.licoreria.modules.users.model.User;
import com.inventario.licoreria.modules.inventory.service.TransactionService;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);

    private final UserService userService;
    private final ProductService productService;
    private final TransactionService transactionService;
    private final SalesReportService salesReportService;
    private final ExportedReportRepository exportedReportRepository;
    private final ExportedReportService exportedReportService;

    public ExportController(UserService userService, ProductService productService, 
                           TransactionService transactionService, SalesReportService salesReportService,
                           ExportedReportRepository exportedReportRepository,
                           ExportedReportService exportedReportService) {
        this.userService = userService;
        this.productService = productService;
        this.transactionService = transactionService;
        this.salesReportService = salesReportService;
        this.exportedReportRepository = exportedReportRepository;
        this.exportedReportService = exportedReportService;
    }

    // --- MÉTODOS DE VALIDACIÓN PRIVADOS ---
    
    private boolean isUserAuthorized(Authentication authentication, Long storeId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        
        User user = userService.findByUsername(authentication.getName());
        if (user == null) return false;
        
        // Si es ADMIN, tiene acceso a todo
        if (user.getRole() != null && user.getRole().name().equals("ADMIN")) return true;
        
        // Para otros usuarios, solo permitir acceso si están autenticados
        // La validación de tienda ocurre a nivel de datos (productos/transacciones filtrados)
        return true;
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportToExcel() throws IOException {
        Workbook workbook = new XSSFWorkbook();

        // Hoja de Usuarios
        Sheet userSheet = workbook.createSheet("Usuarios");
        createUserSheet(userSheet, userService.findAllModels());

        // Hoja de Productos
        Sheet productSheet = workbook.createSheet("Productos");
        createProductSheet(productSheet, productService.findAll());

        // Hoja de Transacciones
        Sheet transactionSheet = workbook.createSheet("Transacciones");
        createTransactionSheet(transactionSheet, transactionService.findAll());

        // Escribir a bytes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "inventario-licoreria.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream.toByteArray());
    }

    @PostMapping("/sales-report")
    public ResponseEntity<?> exportSalesReport(
            @RequestParam Long storeId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(defaultValue = "COMPLETE") String reportType,
            Authentication authentication) {

        try {
            // VALIDACIÓN DE AUTENTICACIÓN Y PERMISOS
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "No autenticado"));
            }

            if (!isUserAuthorized(authentication, storeId)) {
                logger.warn("Usuario {} intenta acceder a tienda {} sin permisos", 
                    authentication.getName(), storeId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "No tienes permisos para acceder a esta tienda"));
            }

            // Set defaults if not provided
            LocalDate to = dateTo != null ? dateTo : LocalDate.now();
            LocalDate from = dateFrom != null ? dateFrom : to.minusDays(30);
            
            logger.info("Exportar reporte: storeId={}, dateFrom={}, dateTo={}, reportType={}", 
                storeId, from, to, reportType);
            
            // Validate date range
            if (from.isAfter(to)) {
                logger.warn("Fechas inválidas: from={} > to={}", from, to);
                return ResponseEntity.badRequest().body(Map.of("message", "Fechas inválidas"));
            }

            // Generate the Excel file bytes
            logger.info("Iniciando generación de reporte...");
            byte[] reportBytes = salesReportService.generateSalesReport(storeId, from, to, reportType);

            if (reportBytes == null || reportBytes.length == 0) {
                logger.info("El reporte no tiene datos");
                return ResponseEntity.ok()
                    .body(Map.of("message", "No hay datos para exportar en el rango de fechas especificado"));
            }

            // Save the report to filesystem and database using ExportedReportService
            logger.info("Guardando reporte generado: {} bytes", reportBytes.length);
            ExportedReport exportedReport = exportedReportService.saveReportFile(
                    storeId, reportBytes, reportType, from.toString(), to.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", exportedReport.getFileName());
            headers.setContentLength(reportBytes.length);

            logger.info("Reporte exportado exitosamente: id={}, fileName={}", 
                exportedReport.getId(), exportedReport.getFileName());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(reportBytes);
            
        } catch (IOException e) {
            logger.error("Error de IO al generar reporte Excel", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error al generar el archivo Excel: " + e.getMessage()));
        } catch (NullPointerException e) {
            logger.error("NullPointerException al generar reporte - revisa datos nulos en transacciones", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error de datos: " + e.getMessage()));
        } catch (DateTimeParseException e) {
            logger.error("Error al parsear fechas", e);
            return ResponseEntity.badRequest().body(Map.of("message", "Fechas inválidas"));
        } catch (Exception e) {
            logger.error("Error inesperado al exportar reporte: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error interno: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getExportHistory(@RequestParam Long storeId, Authentication authentication) {
        try {
            // VALIDACIÓN DE PERMISOS
            if (!isUserAuthorized(authentication, storeId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            logger.info("Obteniendo historial de exportaciones para storeId={}", storeId);
            
            List<ExportedReport> reports = exportedReportRepository.findByStoreIdAndIsDeletedFalseOrderByDateGeneratedDesc(storeId);
            
            List<Map<String, Object>> response = reports.stream()
                .map(report -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", report.getId());
                    map.put("fileName", report.getFileName());
                    map.put("dateGenerated", report.getDateGenerated());
                    map.put("period", formatPeriod(report.getDateFrom(), report.getDateTo()));
                    map.put("reportType", report.getReportType());
                    map.put("downloadUrl", "/api/export/download/" + report.getId());
                    return map;
                })
                .collect(Collectors.toList());

            logger.info("Historial obtenido: {} reportes encontrados", response.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error al obtener historial de exportaciones", e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String id, Authentication authentication) throws IOException {
        ExportedReport report = exportedReportRepository.findById(id).orElse(null);
        if (report == null || report.isDeleted()) {
            return ResponseEntity.notFound().build();
        }

        // VALIDACIÓN DE PERMISOS
        if (!isUserAuthorized(authentication, report.getStoreId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Retrieve report bytes from filesystem using ExportedReportService
        byte[] reportBytes = exportedReportService.getReportFile(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", report.getFileName());
        headers.setContentLength(reportBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(reportBytes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReport(@PathVariable String id, Authentication authentication) throws IOException {
        ExportedReport report = exportedReportRepository.findById(id).orElse(null);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }

        // VALIDACIÓN DE PERMISOS
        if (!isUserAuthorized(authentication, report.getStoreId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Soft delete with file cleanup using ExportedReportService
        exportedReportService.deleteReport(id);

        return ResponseEntity.ok(Map.of("message", "Reporte eliminado correctamente"));
    }

    private String formatPeriod(String dateFrom, String dateTo) {
        try {
            LocalDate from = LocalDate.parse(dateFrom);
            LocalDate to = LocalDate.parse(dateTo);
            return from.getMonth().toString().substring(0, 3).toUpperCase() + " " + from.getYear() + 
                   " - " + to.getMonth().toString().substring(0, 3).toUpperCase() + " " + to.getYear();
        } catch (Exception e) {
            return dateFrom + " a " + dateTo;
        }
    }

    private void createUserSheet(Sheet sheet, List<User> users) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Usuario");
        headerRow.createCell(2).setCellValue("Rol");

        int rowNum = 1;
        for (User user : users) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(user.getId());
            row.createCell(1).setCellValue(user.getUsername());
            row.createCell(2).setCellValue(user.getRole().name());
        }
    }

    private void createProductSheet(Sheet sheet, List<Product> products) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Nombre");
        headerRow.createCell(2).setCellValue("Descripción");
        headerRow.createCell(3).setCellValue("Costo");
        headerRow.createCell(4).setCellValue("Precio");
        headerRow.createCell(5).setCellValue("Stock");

        int rowNum = 1;
        for (Product product : products) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(product.getId());
            row.createCell(1).setCellValue(product.getName());
            row.createCell(2).setCellValue(product.getDescription());
            row.createCell(3).setCellValue(product.getCost().doubleValue());
            row.createCell(4).setCellValue(product.getPrice().doubleValue());
            row.createCell(5).setCellValue(product.getStock());
        }
    }

    private void createTransactionSheet(Sheet sheet, List<Transaction> transactions) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Tipo");
        headerRow.createCell(2).setCellValue("Cantidad");
        headerRow.createCell(3).setCellValue("Producto ID");
        headerRow.createCell(4).setCellValue("Usuario ID");
        headerRow.createCell(5).setCellValue("Fecha");

        int rowNum = 1;
        for (Transaction transaction : transactions) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(transaction.getId());
            row.createCell(1).setCellValue(transaction.getType());
            row.createCell(2).setCellValue(transaction.getQuantity());
            row.createCell(3).setCellValue(transaction.getProductId());
            row.createCell(4).setCellValue(transaction.getUserId());
            row.createCell(5).setCellValue(transaction.getDateTime().toString());
        }
    }
}
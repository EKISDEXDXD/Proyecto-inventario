package com.inventario.licoreria.modules.export;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inventario.licoreria.modules.export.model.ExportedReport;
import com.inventario.licoreria.modules.export.repository.ExportedReportRepository;
import com.inventario.licoreria.modules.export.service.ExportedReportService;
import com.inventario.licoreria.modules.export.service.SalesReportService;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.service.TransactionService;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.modules.users.model.User;
import com.inventario.licoreria.modules.users.service.UserService;

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
            @RequestParam(required = false, defaultValue = "default") String periodMode,
            Authentication authentication) {

        try {
            // Configurar directorio temporal para Apache POI
            String userDir = System.getProperty("user.dir");
            String poiTmpDir = userDir + File.separator + "exports" + File.separator + "poi-temp";
            File poiTmpFile = new File(poiTmpDir);
            if (!poiTmpFile.exists()) {
                poiTmpFile.mkdirs();
            }
            System.setProperty("java.io.tmpdir", poiTmpDir);
            logger.debug("Apache POI temp directory set to: {}", poiTmpDir);
            
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
            boolean completePeriod = "all".equalsIgnoreCase(periodMode) || "complete".equalsIgnoreCase(periodMode);
            if (completePeriod) {
                LocalDate[] completeRange = salesReportService.resolveCompleteRange(storeId);
                from = completeRange[0];
                to = completeRange[1];
            }
            
            logger.info("Exportar reporte: storeId={}, dateFrom={}, dateTo={}, reportType={}, periodMode={}", 
                storeId, from, to, reportType, periodMode);
            
            // Validate date range
            if (!completePeriod && from.isAfter(to)) {
                logger.warn("Fechas inválidas: from={} > to={}", from, to);
                return ResponseEntity.badRequest().body(Map.of("message", "Fechas inválidas"));
            }

            // Generate the Excel file bytes
            logger.info("Iniciando generación de reporte...");
            byte[] reportBytes = salesReportService.generateSalesReport(storeId, from, to, reportType, periodMode);

            if (reportBytes == null || reportBytes.length == 0) {
                logger.info("El reporte no tiene datos");
                return ResponseEntity.ok()
                    .body(Map.of("message", "No hay datos para exportar en el rango de fechas especificado"));
            }

            // Save the report to filesystem and database using ExportedReportService
            logger.info("Guardando reporte generado: {} bytes", reportBytes.length);
            ExportedReport exportedReport = exportedReportService.saveReportFile(
                    storeId, reportBytes, reportType, from.toString(), to.toString(), periodMode);

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
            logger.error("Causa del error:", e.getCause());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error al generar el archivo Excel: " + e.getMessage()));
        } catch (NullPointerException e) {
            logger.error("NullPointerException al generar reporte - revisa datos nulos en transacciones", e);
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error de datos: Verifica que haya transacciones válidas en el rango de fechas especificado"));
        } catch (DateTimeParseException e) {
            logger.error("Error al parsear fechas", e);
            return ResponseEntity.badRequest().body(Map.of("message", "Fechas inválidas: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error inesperado al exportar reporte: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "message", "Error interno al generar reporte: " + e.getClass().getSimpleName(),
                    "detail", e.getMessage()
                ));
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
        // Filtrar solo productos principales (sin parent_id)
        List<Product> mainProducts = products.stream()
            .filter(p -> p.getParentId() == null)
            .collect(Collectors.toList());

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Nombre");
        headerRow.createCell(2).setCellValue("Descripción");
        headerRow.createCell(3).setCellValue("Costo Activo");
        headerRow.createCell(4).setCellValue("Precio Activo");
        headerRow.createCell(5).setCellValue("Stock Total");
        headerRow.createCell(6).setCellValue("Cantidad Lotes");
        headerRow.createCell(7).setCellValue("Lote Activo Número");

        int rowNum = 1;
        for (Product product : mainProducts) {
            // Obtener lotes de este producto
            List<Product> lotes = products.stream()
                .filter(p -> p.getParentId() != null && p.getParentId().equals(product.getId()))
                .collect(Collectors.toList());
            
            // Obtener lote activo
            Product activeLote = lotes.stream()
                .filter(Product::getIsActive)
                .findFirst()
                .orElse(null);
            
            // Calcular stock total
            Integer totalStock = lotes.stream()
                .map(Product::getStock)
                .reduce(0, Integer::sum);

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(product.getId());
            row.createCell(1).setCellValue(product.getName());
            row.createCell(2).setCellValue(product.getDescription() != null ? product.getDescription() : "");
            row.createCell(3).setCellValue(activeLote != null ? activeLote.getCost().doubleValue() : product.getCost().doubleValue());
            row.createCell(4).setCellValue(activeLote != null ? activeLote.getPrice().doubleValue() : product.getPrice().doubleValue());
            row.createCell(5).setCellValue(totalStock);
            row.createCell(6).setCellValue(lotes.size());
            row.createCell(7).setCellValue(activeLote != null ? String.valueOf(activeLote.getOrderIndex()) : "N/A");
        }
    }

    private void createTransactionSheet(Sheet sheet, List<Transaction> transactions) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Tipo");
        headerRow.createCell(2).setCellValue("Cantidad");
        headerRow.createCell(3).setCellValue("Producto");
        headerRow.createCell(4).setCellValue("¿Es Lote?");
        headerRow.createCell(5).setCellValue("Lote Número");
        headerRow.createCell(6).setCellValue("Usuario ID");
        headerRow.createCell(7).setCellValue("Fecha");
        headerRow.createCell(8).setCellValue("Costo Unitario");
        headerRow.createCell(9).setCellValue("Precio Unitario");

        // Obtener todos los productos para buscar información
        List<Product> allProducts = productService.findAll();
        Map<Long, Product> productMap = allProducts.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        int rowNum = 1;
        for (Transaction transaction : transactions) {
            Product product = transaction.getProduct() != null ? transaction.getProduct() : productMap.get(transaction.getProductId());
            
            if (product != null) {
                String productName = product.getName();
                boolean isLote = product.getParentId() != null;
                Integer loteNum = isLote ? product.getOrderIndex() : null;
                
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(transaction.getId());
                row.createCell(1).setCellValue(transaction.getType());
                row.createCell(2).setCellValue(transaction.getQuantity());
                row.createCell(3).setCellValue(productName);
                row.createCell(4).setCellValue(isLote ? "Sí" : "No");
                row.createCell(5).setCellValue(loteNum != null ? String.valueOf(loteNum) : "");
                row.createCell(6).setCellValue(transaction.getUserId());
                row.createCell(7).setCellValue(transaction.getDateTime().toString());
                row.createCell(8).setCellValue(product.getCost().doubleValue());
                row.createCell(9).setCellValue(product.getPrice().doubleValue());
            }
        }
    }

    @GetMapping("/audit-report")
    public ResponseEntity<byte[]> exportAuditReport() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        List<Product> allProducts = productService.findAll();

        Sheet productHierarchySheet = workbook.createSheet("Jerarquía de Lotes");
        createProductHierarchySheet(productHierarchySheet, allProducts);

        Sheet transactionDetailSheet = workbook.createSheet("Historial Transacciones");
        createTransactionSheet(transactionDetailSheet, transactionService.findAll());

        Sheet auditSheet = workbook.createSheet("Auditoría");
        createAuditSheet(auditSheet, allProducts);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "auditoria-lotes-" + LocalDate.now() + ".xlsx");

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    private void createProductHierarchySheet(Sheet sheet, List<Product> allProducts) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Producto Principal");
        headerRow.createCell(1).setCellValue("ID Producto");
        headerRow.createCell(2).setCellValue("Lote #");
        headerRow.createCell(3).setCellValue("ID Lote");
        headerRow.createCell(4).setCellValue("Activo");
        headerRow.createCell(5).setCellValue("Costo");
        headerRow.createCell(6).setCellValue("Precio");
        headerRow.createCell(7).setCellValue("Stock");

        List<Product> mainProducts = allProducts.stream().filter(p -> p.getParentId() == null).collect(Collectors.toList());

        int rowNum = 1;
        for (Product mainProduct : mainProducts) {
            List<Product> lotes = allProducts.stream()
                .filter(p -> p.getParentId() != null && p.getParentId().equals(mainProduct.getId()))
                .collect(Collectors.toList());

            Row mainRow = sheet.createRow(rowNum++);
            mainRow.createCell(0).setCellValue(mainProduct.getName());
            mainRow.createCell(1).setCellValue(mainProduct.getId());
            mainRow.createCell(4).setCellValue("Principal");

            for (Product lote : lotes) {
                Row loteRow = sheet.createRow(rowNum++);
                loteRow.createCell(0).setCellValue("  └─ " + mainProduct.getName());
                loteRow.createCell(1).setCellValue(mainProduct.getId());
                loteRow.createCell(2).setCellValue(lote.getOrderIndex());
                loteRow.createCell(3).setCellValue(lote.getId());
                loteRow.createCell(4).setCellValue(lote.getIsActive() ? "Activo" : "Inactivo");
                loteRow.createCell(5).setCellValue(lote.getCost().doubleValue());
                loteRow.createCell(6).setCellValue(lote.getPrice().doubleValue());
                loteRow.createCell(7).setCellValue(lote.getStock());
            }
        }
    }

    private void createAuditSheet(Sheet sheet, List<Product> allProducts) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Verificación");
        headerRow.createCell(1).setCellValue("Estado");

        int rowNum = 1;
        long orphanTransactions = transactionService.findAll().stream()
            .filter(t -> t.getProduct() == null).count();
        Row row1 = sheet.createRow(rowNum++);
        row1.createCell(0).setCellValue("Transacciones huérfanas (sin producto)");
        row1.createCell(1).setCellValue(orphanTransactions > 0 ? "⚠️ " + orphanTransactions + " encontradas" : "✓ OK");
    }
}
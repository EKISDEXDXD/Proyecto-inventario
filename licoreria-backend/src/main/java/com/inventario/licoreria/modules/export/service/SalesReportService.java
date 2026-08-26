package com.inventario.licoreria.modules.export.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.inventario.licoreria.modules.administrative_costs.model.AdministrativeCostMovement;
import com.inventario.licoreria.modules.administrative_costs.service.AdministrativeCostMovementService;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.service.TransactionService;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.ProductTag;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.modules.users.model.User;
import com.inventario.licoreria.modules.users.service.UserService;

@Service
public class SalesReportService {

    private final TransactionService transactionService;
    private final ProductService productService;
    private final UserService userService;
    private final AdministrativeCostMovementService administrativeCostMovementService;

    public SalesReportService(TransactionService transactionService, 
                             ProductService productService, 
                             UserService userService,
                             AdministrativeCostMovementService administrativeCostMovementService) {
        this.transactionService = transactionService;
        this.productService = productService;
        this.userService = userService;
        this.administrativeCostMovementService = administrativeCostMovementService;
    }

    public byte[] generateSalesReport(Long storeId, LocalDate dateFrom, LocalDate dateTo, String reportType) throws IOException {
        return generateSalesReport(storeId, dateFrom, dateTo, reportType, "default");
    }

    public byte[] generateSalesReport(Long storeId, LocalDate dateFrom, LocalDate dateTo, String reportType, String periodMode) throws IOException {
        try {
            Workbook workbook = new XSSFWorkbook();

            // Obtener todas las transacciones
            List<Transaction> allTransactions = transactionService.findAll();
            
            // Filtrar por tienda, rango de fechas y excluir productos desactivados o eliminados
            List<Transaction> transactions = allTransactions.stream()
                .filter(t -> {
                    try {
                        if (t == null || t.getDateTime() == null) {
                            return false;
                        }

                        Product product = t.getProduct();
                        if (!isProductEligibleForExport(product, storeId)) {
                            return false;
                        }

                        LocalDate txDate = t.getDateTime().toLocalDate();
                        return !txDate.isBefore(dateFrom) && !txDate.isAfter(dateTo);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

            boolean isCustomPeriod = "custom".equalsIgnoreCase(periodMode) || "personalizado".equalsIgnoreCase(periodMode);
            if (isCustomPeriod) {
                createFileMetadataSheet(workbook, storeId, dateFrom, dateTo, reportType, true);
            }

            // Crear hojas según tipo de reporte
            createExecutiveSummarySheet(workbook, transactions, storeId, isCustomPeriod, dateFrom, dateTo, reportType);
            createDetailedMovementsSheet(workbook, transactions);
            
            // Si es COMPLETE, agregar hojas adicionales en orden específico
            if ("COMPLETE".equalsIgnoreCase(reportType)) {
                createDailyCashFlowSheet(workbook, transactions, storeId);
                createProductAnalysisSheet(workbook, transactions, storeId, dateFrom, dateTo);
                createStockRotationSheet(workbook, transactions, storeId, dateFrom, dateTo);
                createAdministrativeCostsSheet(workbook, storeId, dateFrom, dateTo);
                createAnalysisByLabelsSheet(workbook, transactions, storeId);
                createProductSalesAnalysisSheet(workbook, transactions, storeId);
                // Se eliminaron las hojas de 'Gráficos y indicaciones' y 'Recomendaciones'
                // por solicitud: ya no se generan estas hojas en los reportes.
            }

            // Escribir a bytes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            return outputStream.toByteArray();
        } catch (NullPointerException e) {
            throw new RuntimeException("Error de datos nulos al generar reporte. Verifica que existan transacciones y productos válidos.", e);
        } catch (IOException e) {
            throw new IOException("Error al escribir el archivo Excel: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error inesperado al generar reporte: " + e.getMessage(), e);
        }
    }

    /**
     * Filtra transacciones de productos ACTIVOS para análisis y resúmenes
     */
    private List<Transaction> filterActiveProductTransactions(List<Transaction> transactions) {
        return transactions.stream()
            .filter(t -> {
                try {
                    Product product = t.getProduct();
                    return isProductEligibleForExport(product);
                } catch (Exception e) {
                    return false;
                }
            })
            .collect(Collectors.toList());
    }

    static boolean isProductEligibleForExport(Product product) {
        return isProductEligibleForExport(product, null);
    }

    static boolean isProductEligibleForExport(Product product, Long storeId) {
        if (product == null) {
            return false;
        }

        if (Boolean.FALSE.equals(product.getIsActive())) {
            return false;
        }

        if (product.getStore() == null) {
            return false;
        }

        if (storeId != null && !storeId.equals(product.getStore().getId())) {
            return false;
        }

        return true;
    }

    private void createExecutiveSummarySheet(Workbook workbook, List<Transaction> transactions, Long storeId,
                                             boolean customPeriod, LocalDate dateFrom, LocalDate dateTo, String reportType) {
        Sheet sheet = workbook.createSheet("Resumen Ejecutivo");
        
        // Solo incluir productos activos y de la tienda correcta en resumen ejecutivo
        List<Transaction> activeTransactions = filterActiveProductTransactions(transactions);
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle totalStyle = createTotalStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle currencyWithSymbolStyle = createCurrencyWithSymbolStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 30);
        sheet.setColumnWidth(1, 20);
        sheet.setColumnWidth(2, 15);
        sheet.setColumnWidth(3, 20);

        // TÍTULO PRINCIPAL
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("REPORTE DE VENTAS");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 3));

        rowNum++; // Espacio

        // PERÍODO
        if (!activeTransactions.isEmpty()) {
            LocalDate minDate = activeTransactions.stream()
                .map(t -> t.getDateTime().toLocalDate())
                .min(LocalDate::compareTo).orElse(LocalDate.now());
            LocalDate maxDate = activeTransactions.stream()
                .map(t -> t.getDateTime().toLocalDate())
                .max(LocalDate::compareTo).orElse(LocalDate.now());
            
            Row periodRow = sheet.createRow(rowNum++);
            Cell periodLabelCell = periodRow.createCell(0);
            periodLabelCell.setCellValue("Período:");
            periodLabelCell.setCellStyle(subtitleStyle);
            
            Cell periodFromCell = periodRow.createCell(1);
            periodFromCell.setCellValue(minDate);
            periodFromCell.setCellStyle(dateStyle);
            
            Cell periodToLabelCell = periodRow.createCell(2);
            periodToLabelCell.setCellValue("Hasta:");
            periodToLabelCell.setCellStyle(subtitleStyle);
            
            Cell periodToCell = periodRow.createCell(3);
            periodToCell.setCellValue(maxDate);
            periodToCell.setCellStyle(dateStyle);
        }

        rowNum++; // Espacio

        // CÁLCULOS PRINCIPALES
        BigDecimal totalEntradas = BigDecimal.ZERO;
        BigDecimal totalSalidas = BigDecimal.ZERO;
        BigDecimal totalCostSold = BigDecimal.ZERO;

        for (Transaction t : activeTransactions) {
            Product product = t.getProduct();
            if (product == null) continue;

            boolean isAdjustmentReason = "AJUSTE".equalsIgnoreCase(t.getReason());
            if (isAdjustmentReason) {
                continue;
            }

            if ("ENTRADA".equalsIgnoreCase(t.getType())) {
                BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                totalEntradas = totalEntradas.add(cost.multiply(new BigDecimal(t.getQuantity())));
            } else if ("SALIDA".equalsIgnoreCase(t.getType()) && "VENTA".equalsIgnoreCase(t.getReason())) {
                BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                totalSalidas = totalSalidas.add(price.multiply(new BigDecimal(t.getQuantity())));
                totalCostSold = totalCostSold.add(cost.multiply(new BigDecimal(t.getQuantity())));
            }
        }

        BigDecimal gananciaTotal = totalSalidas.subtract(totalCostSold);
        BigDecimal margenPromedio = totalSalidas.compareTo(BigDecimal.ZERO) > 0 
            ? gananciaTotal.divide(totalSalidas, 4, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Sección de Métricas
        Row metricsHeaderRow = sheet.createRow(rowNum++);
        metricsHeaderRow.setHeightInPoints(18);
        Cell metricsHeaderCell = metricsHeaderRow.createCell(0);
        metricsHeaderCell.setCellValue("MÉTRICAS PRINCIPALES");
        metricsHeaderCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 1));

        // Mostrar estadísticas
        Row row = sheet.createRow(rowNum++);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue("Total Invertido:");
        labelCell.setCellStyle(labelStyle);
        Cell cell = row.createCell(1);
        cell.setCellValue(totalEntradas.doubleValue());
        cell.setCellStyle(currencyWithSymbolStyle);

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Total Ingresos:");
        labelCell.setCellStyle(labelStyle);
        cell = row.createCell(1);
        cell.setCellValue(totalSalidas.doubleValue());
        cell.setCellStyle(currencyWithSymbolStyle);

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Costo de Venta:");
        labelCell.setCellStyle(labelStyle);
        cell = row.createCell(1);
        cell.setCellValue(totalCostSold.doubleValue());
        cell.setCellStyle(currencyWithSymbolStyle);

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Ganancia Bruta:");
        labelCell.setCellStyle(labelStyle);
        cell = row.createCell(1);
        cell.setCellValue(gananciaTotal.doubleValue());
        cell.setCellStyle(currencyWithSymbolStyle);

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Margen de Ganancia:");
        labelCell.setCellStyle(totalStyle);
        cell = row.createCell(1);
        cell.setCellValue(margenPromedio.doubleValue());
        CellStyle percentStyle2 = createPercentageStyle(workbook);
        cell.setCellStyle(percentStyle2);

        rowNum++; // Espacio

        // TOP 5 PRODUCTOS
        row = sheet.createRow(rowNum++);
        row.setHeightInPoints(18);
        Cell topCell = row.createCell(0);
        topCell.setCellValue("TOP 5 PRODUCTOS");
        topCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 2));

        row = sheet.createRow(rowNum++);
        row.setHeightInPoints(16);
        String[] topHeaders = {"Producto", "Cant. Vendida", "Ingresos"};
        for (int i = 0; i < topHeaders.length; i++) {
            Cell hCell = row.createCell(i);
            hCell.setCellValue(topHeaders[i]);
            hCell.setCellStyle(headerStyle);
        }

        Map<String, Integer> productQuantity = new HashMap<>();
        Map<String, BigDecimal> productRevenue = new HashMap<>();

        for (Transaction t : activeTransactions) {
            if ("SALIDA".equalsIgnoreCase(t.getType()) && "VENTA".equalsIgnoreCase(t.getReason())) {
                try {
                    Product product = t.getProduct();
                    if (product != null) {
                        String productKey = product.getName();
                        productQuantity.put(productKey, productQuantity.getOrDefault(productKey, 0) + t.getQuantity());
                        BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                        BigDecimal revenue = price.multiply(new BigDecimal(t.getQuantity()));
                        productRevenue.put(productKey, productRevenue.getOrDefault(productKey, BigDecimal.ZERO).add(revenue));
                    }
                } catch (Exception e) {
                    // Skip product
                }
            }
        }

        for (Map.Entry<String, Integer> entry : productQuantity.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(5)
            .collect(Collectors.toList())) {
            Row dataRow = sheet.createRow(rowNum++);
            dataRow.setHeightInPoints(14);
            
            Cell nameCell = dataRow.createCell(0);
            nameCell.setCellValue(entry.getKey());
            nameCell.setCellStyle(dataCellStyle);
            
            Cell qtyCell = dataRow.createCell(1);
            qtyCell.setCellValue(entry.getValue());
            qtyCell.setCellStyle(createNumberStyle(workbook));
            
            Cell revCell = dataRow.createCell(2);
            revCell.setCellValue(productRevenue.getOrDefault(entry.getKey(), BigDecimal.ZERO).doubleValue());
            revCell.setCellStyle(currencyStyle);
        }

        rowNum++; // Espacio

        // CUADRO INFORMATIVO DE DEFINICIONES DEBAJO DE TOP 5 PRODUCTOS
        Row definitionsHeaderRow = sheet.createRow(rowNum++);
        definitionsHeaderRow.setHeightInPoints(18);
        Cell definitionsHeaderCell = definitionsHeaderRow.createCell(0);
        definitionsHeaderCell.setCellValue("¿Qué significa cada indicador?");
        definitionsHeaderCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));

        Row definitionsIntroRow = sheet.createRow(rowNum++);
        definitionsIntroRow.createCell(0).setCellValue("Este cuadro resume, de forma objetiva, qué representa cada valor del reporte.");
        definitionsIntroRow.getCell(0).setCellStyle(dataCellStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));

        String[][] definitions = {
            {"Total Invertido", "Es el valor total destinado a entradas de inventario durante el período."},
            {"Total Ingreso", "Es el valor total generado por las ventas registradas en el período."},
            {"Costo de Venta", "Es el costo asociado a los productos que se vendieron."},
            {"Ganancia Bruta", "Es la diferencia entre ingresos y costo de venta."},
            {"Margen de Ganancia", "Es el porcentaje de utilidad que representa la ganancia sobre las ventas."}
        };

        for (String[] definition : definitions) {
            Row definitionRow = sheet.createRow(rowNum++);
            Cell metricLabelCell = definitionRow.createCell(0);
            metricLabelCell.setCellValue(definition[0]);
            metricLabelCell.setCellStyle(labelStyle);

            Cell metricValueCell = definitionRow.createCell(1);
            metricValueCell.setCellValue(definition[1]);
            metricValueCell.setCellStyle(dataCellStyle);
        }

        // Auto-ajustar columnas
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
    }

    private void createFileMetadataSheet(Workbook workbook, Long storeId, LocalDate dateFrom, LocalDate dateTo, String reportType, boolean customPeriod) {
        Sheet sheet = workbook.createSheet("Información del Archivo");

        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);

        int rowNum = 0;
        sheet.setColumnWidth(0, 28);
        sheet.setColumnWidth(1, 45);

        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(24);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("INFORMACIÓN DEL ARCHIVO");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));

        Row typeRow = sheet.createRow(rowNum++);
        typeRow.createCell(0).setCellValue("Tipo de período:");
        typeRow.createCell(1).setCellValue(customPeriod ? "Personalizado" : "Predeterminado");
        typeRow.getCell(0).setCellStyle(labelStyle);
        typeRow.getCell(1).setCellStyle(dataCellStyle);

        Row rangeRow = sheet.createRow(rowNum++);
        rangeRow.createCell(0).setCellValue("Rango:");
        rangeRow.createCell(1).setCellValue(dateFrom + " a " + dateTo);
        rangeRow.getCell(0).setCellStyle(labelStyle);
        rangeRow.getCell(1).setCellStyle(dataCellStyle);

        Row reportTypeRow = sheet.createRow(rowNum++);
        reportTypeRow.createCell(0).setCellValue("Tipo de reporte:");
        reportTypeRow.createCell(1).setCellValue("COMPLETE".equalsIgnoreCase(reportType) ? "Completo" : "Resumido");
        reportTypeRow.getCell(0).setCellStyle(labelStyle);
        reportTypeRow.getCell(1).setCellStyle(dataCellStyle);

        Row storeRow = sheet.createRow(rowNum++);
        storeRow.createCell(0).setCellValue("Tienda:");
        storeRow.createCell(1).setCellValue(storeId != null ? storeId.toString() : "N/A");
        storeRow.getCell(0).setCellStyle(labelStyle);
        storeRow.getCell(1).setCellStyle(dataCellStyle);

        Row generatedRow = sheet.createRow(rowNum++);
        generatedRow.createCell(0).setCellValue("Generado el:");
        generatedRow.createCell(1).setCellValue(LocalDateTime.now().toString());
        generatedRow.getCell(0).setCellStyle(labelStyle);
        generatedRow.getCell(1).setCellStyle(dataCellStyle);

        Row noteRow = sheet.createRow(rowNum++);
        noteRow.createCell(0).setCellValue("Nota:");
        noteRow.createCell(1).setCellValue("Este archivo corresponde a un período personalizado de exportación.");
        noteRow.getCell(0).setCellStyle(labelStyle);
        noteRow.getCell(1).setCellStyle(dataCellStyle);

        for (int i = 0; i < 2; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createDetailedMovementsSheet(Workbook workbook, List<Transaction> transactions) {
        Sheet sheet = workbook.createSheet("Movimientos Detallados");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 24);  // Para fechas completas con hora
        sheet.setColumnWidth(1, 12);  // Tipo
        sheet.setColumnWidth(2, 15);  // Motivo
        sheet.setColumnWidth(3, 20);  // Método de Pago
        sheet.setColumnWidth(4, 20);  // Producto
        sheet.setColumnWidth(5, 12);  // Cantidad
        sheet.setColumnWidth(6, 16);  // Costo Unit
        sheet.setColumnWidth(7, 16);  // Costo Total
        sheet.setColumnWidth(8, 16);  // Precio Unit
        sheet.setColumnWidth(9, 16);  // Precio Total
        sheet.setColumnWidth(10, 16); // Ganancia Unit
        sheet.setColumnWidth(11, 16); // Ganancia Total
        sheet.setColumnWidth(12, 15); // Usuario

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("MOVIMIENTOS DETALLADOS");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 12));

        // Encabezados de columnas (sin espacios extras)
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Fecha", "Tipo", "Motivo", "Método de Pago", "Producto", "Cantidad", "Costo Unit", "Costo Total", 
                          "Precio Unit", "Precio Total", "Ganancia Unit", "Ganancia Total", "Usuario"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Organizar por meses
        Map<YearMonth, List<Transaction>> transactionsByMonth = transactions.stream()
            .filter(t -> t.getDateTime() != null)
            .collect(Collectors.groupingBy(
                t -> YearMonth.from(t.getDateTime()),
                Collectors.toList()
            ));

        for (YearMonth month : transactionsByMonth.keySet().stream().sorted().collect(Collectors.toList())) {
            List<Transaction> monthTransactions = transactionsByMonth.get(month);
            
            // ENCABEZADO DE MES
            Row monthRow = sheet.createRow(rowNum++);
            monthRow.setHeightInPoints(16);
            monthRow.createCell(0).setCellValue(month.toString());
            monthRow.getCell(0).setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 12));

            // Datos del mes
            for (Transaction t : monthTransactions.stream()
                    .sorted(Comparator.comparing(Transaction::getDateTime))
                    .collect(Collectors.toList())) {
                Product product;
                try {
                    product = productService.findById(t.getProductId());
                } catch (Exception e) {
                    continue;
                }
                if (product == null) continue;

                User user = null;
                try {
                    user = userService.findById(t.getUserId());
                } catch (Exception e) {
                    user = null;
                }

                Row row = sheet.createRow(rowNum++);
                row.setHeightInPoints(14);

                Cell dateCell = row.createCell(0);
                dateCell.setCellValue(t.getDateTime().toLocalDate());
                dateCell.setCellStyle(dateStyle);

                Cell typeCell = row.createCell(1);
                typeCell.setCellValue(t.getType());
                typeCell.setCellStyle(dataCellStyle);

                Cell reasonCell = row.createCell(2);
                reasonCell.setCellValue(t.getReason() != null ? t.getReason() : "N/A");
                reasonCell.setCellStyle(dataCellStyle);

                Cell paymentMethodCell = row.createCell(3);
                String paymentMethodName = "N/A";
                try {
                    if (t.getPaymentMethod() != null && t.getPaymentMethod().getPaymentMethodConfig() != null) {
                        paymentMethodName = t.getPaymentMethod().getPaymentMethodConfig().getName();
                    }
                } catch (Exception e) {
                    paymentMethodName = "N/A";
                }
                paymentMethodCell.setCellValue(paymentMethodName);
                paymentMethodCell.setCellStyle(dataCellStyle);

                Cell productCell = row.createCell(4);
                productCell.setCellValue(product.getName());
                productCell.setCellStyle(dataCellStyle);

                Cell qtyCell = row.createCell(5);
                qtyCell.setCellValue(t.getQuantity());
                qtyCell.setCellStyle(numberStyle);

                // Handle null prices/costs
                BigDecimal costUnit = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                BigDecimal priceUnit = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal costTotal = costUnit.multiply(new BigDecimal(t.getQuantity()));
                BigDecimal priceTotal = priceUnit.multiply(new BigDecimal(t.getQuantity()));

                boolean isAdjustmentReason = "AJUSTE".equalsIgnoreCase(t.getReason());
                BigDecimal gainUnit = BigDecimal.ZERO;
                BigDecimal gainTotal = BigDecimal.ZERO;

                if ("SALIDA".equalsIgnoreCase(t.getType()) && !isAdjustmentReason) {
                    gainUnit = priceUnit.subtract(costUnit);
                    gainTotal = gainUnit.multiply(new BigDecimal(t.getQuantity()));
                }

                row.createCell(6).setCellValue(costUnit.doubleValue());
                row.getCell(6).setCellStyle(currencyStyle);
                
                row.createCell(7).setCellValue(costTotal.doubleValue());
                row.getCell(7).setCellStyle(currencyStyle);
                
                row.createCell(8).setCellValue(priceUnit.doubleValue());
                row.getCell(8).setCellStyle(currencyStyle);
                
                row.createCell(9).setCellValue(priceTotal.doubleValue());
                row.getCell(9).setCellStyle(currencyStyle);
                
                row.createCell(10).setCellValue(gainUnit.doubleValue());
                row.getCell(10).setCellStyle(currencyStyle);
                
                row.createCell(11).setCellValue(gainTotal.doubleValue());
                row.getCell(11).setCellStyle(currencyStyle);

                Cell userCell = row.createCell(12);
                userCell.setCellValue(user != null ? user.getUsername() : "N/A");
                userCell.setCellStyle(dataCellStyle);
            }
        }

        // Auto-ajustar columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createProductAnalysisSheet(Workbook workbook, List<Transaction> transactions, Long storeId, LocalDate dateFrom, LocalDate dateTo) {
        // Filtrar solo productos activos y elegibles
        List<Transaction> activeTransactions = filterActiveProductTransactions(transactions);
        
        Sheet sheet = workbook.createSheet("Análisis por Producto");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle percentStyle = createPercentageStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas (15 columnas)
        sheet.setColumnWidth(0, 25);  // Producto
        sheet.setColumnWidth(1, 16);  // Cantidad Entrada
        sheet.setColumnWidth(2, 18);  // Veces Entrada
        sheet.setColumnWidth(3, 16);  // Cantidad Salida
        sheet.setColumnWidth(4, 18);  // Veces Salida
        sheet.setColumnWidth(5, 14);  // Stock Actual
        sheet.setColumnWidth(6, 14);  // Costo Unit
        sheet.setColumnWidth(7, 14);  // Precio Unit
        sheet.setColumnWidth(8, 16);  // Costo Invertido
        sheet.setColumnWidth(9, 14);  // Precio total
        sheet.setColumnWidth(10, 14); // Costo total
        sheet.setColumnWidth(11, 16); // Ingreso Total
        sheet.setColumnWidth(12, 16); // Ganancia Total
        sheet.setColumnWidth(13, 14); // Ganancia %
        sheet.setColumnWidth(14, 22); // Velocidad Rotación Diaria

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS POR PRODUCTO");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 14));

        rowNum++; // Espacio

        // Encabezados
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Producto", "Cantidad Entrada", "Veces Entrada", "Cantidad Salida", "Veces Salida", "Stock Actual", 
                          "Costo Unit", "Precio Unit", "Costo Invertido", "Precio total",
                          "Costo total", "Ingreso Total", "Ganancia Total", "Ganancia %", "Velocidad Rotación Diaria"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Calcular días del período
        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;

        // Agrupar por producto (solo transacciones activas)
        Map<Long, List<Transaction>> transactionsByProductId = activeTransactions.stream()
            .filter(t -> t.getDateTime() != null)
            .filter(t -> {
                try {
                    LocalDate transDate = t.getDateTime().toLocalDate();
                    return !transDate.isBefore(dateFrom) && !transDate.isAfter(dateTo);
                } catch (Exception e) {
                    return false;
                }
            })
            .collect(Collectors.groupingBy(
                t -> t.getProduct().getId(),
                Collectors.toList()
            ));

        for (Map.Entry<Long, List<Transaction>> entry : transactionsByProductId.entrySet()) {
            List<Transaction> productTransactions = entry.getValue();
            
            if (productTransactions.isEmpty()) continue;
            Product product = productTransactions.get(0).getProduct();
            if (product == null) continue;

            // Contar ENTRADA y SALIDA excluyendo AJUSTE
            List<Transaction> entradas = productTransactions.stream()
                .filter(t -> "ENTRADA".equalsIgnoreCase(t.getType()) && !"AJUSTE".equalsIgnoreCase(t.getReason()))
                .collect(Collectors.toList());
            
            List<Transaction> salidas = productTransactions.stream()
                .filter(t -> "SALIDA".equalsIgnoreCase(t.getType()) && !"AJUSTE".equalsIgnoreCase(t.getReason()))
                .collect(Collectors.toList());
            
            int cantEntrada = entradas.stream().mapToInt(Transaction::getQuantity).sum();
            int cantSalida = salidas.stream().mapToInt(Transaction::getQuantity).sum();
            int vecesEntrada = entradas.size();
            int vecesSalida = salidas.size();

            // Calcular costos e ingresos
            BigDecimal costUnit = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
            BigDecimal priceUnit = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;

            // Costo Invertido = Costo Unit * Cantidad Entrada (solo ENTRADA sin AJUSTE)
            BigDecimal costInvested = costUnit.multiply(new BigDecimal(cantEntrada));
            
            // Precio Total = Precio Unit * Cantidad Salida (solo SALIDA sin AJUSTE, no ENTRADA)
            BigDecimal precioTotal = priceUnit.multiply(new BigDecimal(cantSalida));
            
            // Costo Total = Costo Unit * Cantidad Salida (lo que se vendió)
            BigDecimal costSold = costUnit.multiply(new BigDecimal(cantSalida));
            
            // Ingreso Total = Precio Unit * Cantidad Salida (solo venta genera ingreso)
            BigDecimal ingresoTotal = priceUnit.multiply(new BigDecimal(cantSalida));
            
            // Ganancia Total = Ingreso - Costo de lo vendido (solo SALIDA genera ganancia)
            BigDecimal gananciaTotal = ingresoTotal.subtract(costSold);
            
            BigDecimal gananciaPercent = ingresoTotal.compareTo(BigDecimal.ZERO) > 0 
                ? gananciaTotal.divide(ingresoTotal, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            // Calcular velocidad de rotación diaria (promedio de salidas por día)
            double velocidadRotacion = cantSalida > 0 ? (double) cantSalida / periodDays : 0.0;

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(14);

            Cell productCell = row.createCell(0);
            productCell.setCellValue(product.getName());
            productCell.setCellStyle(dataCellStyle);

            Cell entCell = row.createCell(1);
            entCell.setCellValue(cantEntrada);
            entCell.setCellStyle(numberStyle);

            Cell vecesEntCell = row.createCell(2);
            vecesEntCell.setCellValue(vecesEntrada);
            vecesEntCell.setCellStyle(numberStyle);

            Cell salCell = row.createCell(3);
            salCell.setCellValue(cantSalida);
            salCell.setCellStyle(numberStyle);

            Cell vecesSalCell = row.createCell(4);
            vecesSalCell.setCellValue(vecesSalida);
            vecesSalCell.setCellStyle(numberStyle);

            Cell stockCell = row.createCell(5);
            stockCell.setCellValue(product.getStock());
            stockCell.setCellStyle(numberStyle);
            
            Cell costUnitCell = row.createCell(6);
            costUnitCell.setCellValue(costUnit.doubleValue());
            costUnitCell.setCellStyle(currencyStyle);

            Cell priceUnitCell = row.createCell(7);
            priceUnitCell.setCellValue(priceUnit.doubleValue());
            priceUnitCell.setCellStyle(currencyStyle);

            Cell costInvCell = row.createCell(8);
            costInvCell.setCellValue(costInvested.doubleValue());
            costInvCell.setCellStyle(currencyStyle);

            Cell precioTotalCell = row.createCell(9);
            precioTotalCell.setCellValue(precioTotal.doubleValue());
            precioTotalCell.setCellStyle(currencyStyle);

            Cell costSoldCell = row.createCell(10);
            costSoldCell.setCellValue(costSold.doubleValue());
            costSoldCell.setCellStyle(currencyStyle);

            Cell ingresoCell = row.createCell(11);
            ingresoCell.setCellValue(ingresoTotal.doubleValue());
            ingresoCell.setCellStyle(currencyStyle);

            Cell gananciaCell = row.createCell(12);
            gananciaCell.setCellValue(gananciaTotal.doubleValue());
            gananciaCell.setCellStyle(currencyStyle);

            Cell gananciaPercentCell = row.createCell(13);
            gananciaPercentCell.setCellValue(gananciaPercent.doubleValue());
            gananciaPercentCell.setCellStyle(percentStyle);

            Cell velocidadCell = row.createCell(14);
            velocidadCell.setCellValue(velocidadRotacion);
            velocidadCell.setCellStyle(numberStyle);
        }

        // Auto-ajustar columnas
        for (int i = 0; i < 15; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createDailyCashFlowSheet(Workbook workbook, List<Transaction> transactions, Long storeId) {
        // Filtrar solo productos activos para este análisis
        List<Transaction> activeTransactions = filterActiveProductTransactions(transactions);
        Sheet sheet = workbook.createSheet("Flujo Caja Diario");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 18);  // Fecha
        sheet.setColumnWidth(1, 16);  // Cant. Salidas
        sheet.setColumnWidth(2, 16);  // Cant. Entradas
        sheet.setColumnWidth(3, 18);  // Ventas
        sheet.setColumnWidth(4, 20);  // Gasto Entradas
        sheet.setColumnWidth(5, 20);  // Ganancia Neta

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("FLUJO CAJA DIARIO");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 5));

        rowNum++; // Espacio

        // Encabezados
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Día", "Cant. Salidas", "Cant. Entradas", "Ventas", "Gasto Entradas", "Ganancia Neta"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Agrupar por día (filtrar transacciones sin dateTime válido)
        Map<LocalDate, List<Transaction>> transactionsByDay = activeTransactions.stream()
            .filter(t -> t.getDateTime() != null)
            .collect(Collectors.groupingBy(
                t -> t.getDateTime().toLocalDate(),
                TreeMap::new,
                Collectors.toList()
            ));

        for (Map.Entry<LocalDate, List<Transaction>> entry : transactionsByDay.entrySet()) {
            LocalDate day = entry.getKey();
            List<Transaction> dayTransactions = entry.getValue();

            int cantSalidas = 0;
            int cantEntradas = 0;
            BigDecimal ventas = BigDecimal.ZERO;
            BigDecimal costoVenta = BigDecimal.ZERO;
            BigDecimal gastoEntradas = BigDecimal.ZERO;

            for (Transaction t : dayTransactions) {
                // Obtener producto de la transacción
                Product product = t.getProduct();
                
                // Verificar que el producto sea elegible (activo, no eliminado, tienda correcta)
                if (!isProductEligibleForExport(product)) {
                    continue;
                }

                // Excluir movimientos de ajuste (no generan ganancias ni pérdidas)
                if ("AJUSTE".equalsIgnoreCase(t.getReason())) {
                    continue;
                }

                BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal unitCost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                BigDecimal quantity = BigDecimal.valueOf(t.getQuantity() != null ? t.getQuantity() : 0);

                // ENTRADA: Solo registrar costo, no genera ganancia
                if ("ENTRADA".equalsIgnoreCase(t.getType())) {
                    cantEntradas++;
                    gastoEntradas = gastoEntradas.add(unitCost.multiply(quantity));
                } 
                // SALIDA: Registrar ventas y calcular ganancia bruta
                else if ("SALIDA".equalsIgnoreCase(t.getType())) {
                    cantSalidas++;
                    ventas = ventas.add(unitPrice.multiply(quantity));
                    costoVenta = costoVenta.add(unitCost.multiply(quantity));
                }
            }

            BigDecimal gananciaNeta = ventas.subtract(costoVenta);

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(14);

            Cell dayCell = row.createCell(0);
            dayCell.setCellValue(day);
            dayCell.setCellStyle(dateStyle);
            addBorders(dayCell);
            
            Cell cantSalidasCell = row.createCell(1);
            cantSalidasCell.setCellValue(cantSalidas);
            cantSalidasCell.setCellStyle(numberStyle);
            addBorders(cantSalidasCell);

            Cell cantEntradasCell = row.createCell(2);
            cantEntradasCell.setCellValue(cantEntradas);
            cantEntradasCell.setCellStyle(numberStyle);
            addBorders(cantEntradasCell);

            Cell ventasCell = row.createCell(3);
            ventasCell.setCellValue(ventas.doubleValue());
            ventasCell.setCellStyle(currencyStyle);
            addBorders(ventasCell);

            Cell gastoEntradasCell = row.createCell(4);
            gastoEntradasCell.setCellValue(gastoEntradas.doubleValue());
            gastoEntradasCell.setCellStyle(currencyStyle);
            addBorders(gastoEntradasCell);

            Cell gananciaNetaCell = row.createCell(5);
            gananciaNetaCell.setCellValue(gananciaNeta.doubleValue());
            gananciaNetaCell.setCellStyle(currencyStyle);
            addBorders(gananciaNetaCell);
        }

        // Auto-ajustar columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createAdministrativeCostsSheet(Workbook workbook, Long storeId, LocalDate dateFrom, LocalDate dateTo) {
        Sheet sheet = workbook.createSheet("Costos Administrativos");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dateTimeStyle = createDateTimeStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 22);
        sheet.setColumnWidth(1, 25);
        sheet.setColumnWidth(2, 15);
        sheet.setColumnWidth(3, 18);
        sheet.setColumnWidth(4, 18);

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("MOVIMIENTOS DE COSTOS ADMINISTRATIVOS");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 4));

        rowNum++; // Espacio

        // PERÍODO
        Row periodRow = sheet.createRow(rowNum++);
        periodRow.setHeightInPoints(16);
        Cell periodLabelCell = periodRow.createCell(0);
        periodLabelCell.setCellValue("Período:");
        periodLabelCell.setCellStyle(subtitleStyle);
        
        Cell periodFromCell = periodRow.createCell(1);
        periodFromCell.setCellValue(dateFrom);
        periodFromCell.setCellStyle(dateTimeStyle);
        
        Cell periodToLabelCell = periodRow.createCell(2);
        periodToLabelCell.setCellValue("Hasta:");
        periodToLabelCell.setCellStyle(subtitleStyle);
        
        Cell periodToCell = periodRow.createCell(3);
        periodToCell.setCellValue(dateTo);
        periodToCell.setCellStyle(dateTimeStyle);

        rowNum++; // Espacio

        // ENCABEZADOS
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Fecha", "Costo", "Tipo", "Monto Pagado", "Usuario"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Obtener costos administrativos filtrados
        try {
            List<AdministrativeCostMovement> movements = new ArrayList<>();
            try {
                List<AdministrativeCostMovement> allMovements = administrativeCostMovementService.findAll();
                if (allMovements != null) {
                    movements = allMovements.stream()
                        .filter(m -> {
                            try {
                                return m != null && 
                                       m.getAdministrativeCost() != null &&
                                       m.getAdministrativeCost().getStore() != null &&
                                       m.getAdministrativeCost().getStore().getId().equals(storeId) &&
                                       m.getDateTime() != null &&
                                       m.getDateTime().toLocalDate().isAfter(dateFrom.minusDays(1)) &&
                                       m.getDateTime().toLocalDate().isBefore(dateTo.plusDays(1));
                            } catch (Exception e) {
                                System.err.println("Error filtrando movimiento: " + e.getMessage());
                                return false;
                            }
                        })
                        .collect(Collectors.toList());
                }
            } catch (Exception e) {
                System.err.println("Error obteniendo costos administrativos: " + e.getMessage());
            }

            // Llenar datos
            BigDecimal totalAmount = BigDecimal.ZERO;
            
            for (AdministrativeCostMovement movement : movements) {
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.setHeightInPoints(14);
                
                // Fecha
                Cell dateCell = dataRow.createCell(0);
                dateCell.setCellValue(movement.getDateTime());
                dateCell.setCellStyle(dateTimeStyle);
                
                // Nombre del costo
                Cell costNameCell = dataRow.createCell(1);
                costNameCell.setCellValue(movement.getAdministrativeCost().getName() != null ? 
                    movement.getAdministrativeCost().getName() : "N/A");
                costNameCell.setCellStyle(dataCellStyle);
                
                // Tipo
                Cell typeCell = dataRow.createCell(2);
                typeCell.setCellValue(movement.getType() != null ? movement.getType() : "N/A");
                typeCell.setCellStyle(dataCellStyle);
                
                // Monto pagado
                Cell amountCell = dataRow.createCell(3);
                if (movement.getAmountPaid() != null) {
                    amountCell.setCellValue(movement.getAmountPaid().doubleValue());
                    totalAmount = totalAmount.add(movement.getAmountPaid());
                } else {
                    amountCell.setCellValue(0.0);
                }
                amountCell.setCellStyle(currencyStyle);
                
                // Usuario
                Cell userCell = dataRow.createCell(4);
                userCell.setCellValue(movement.getUser() != null && movement.getUser().getUsername() != null ? 
                    movement.getUser().getUsername() : "N/A");
                userCell.setCellStyle(dataCellStyle);
            }

            // TOTAL
            if (!movements.isEmpty()) {
                rowNum++;
                Row totalRow = sheet.createRow(rowNum);
                totalRow.setHeightInPoints(16);
                Cell totalLabelCell = totalRow.createCell(2);
                totalLabelCell.setCellValue("TOTAL:");
                totalLabelCell.setCellStyle(createTotalStyle(workbook));
                
                Cell totalValueCell = totalRow.createCell(3);
                totalValueCell.setCellValue(totalAmount.doubleValue());
                totalValueCell.setCellStyle(currencyStyle);
            } else {
                // Si no hay datos
                Row noDataRow = sheet.createRow(rowNum);
                Cell noDataCell = noDataRow.createCell(0);
                noDataCell.setCellValue("No hay movimientos de costos para este período");
            }

        } catch (Exception e) {
            System.err.println("Error al crear hoja de costos administrativos: " + e.getMessage());
            e.printStackTrace();
            // En caso de error, mostrar mensaje en la hoja
            Row errorRow = sheet.createRow(rowNum);
            Cell errorCell = errorRow.createCell(0);
            errorCell.setCellValue("Error al cargar datos de costos administrativos: " + e.getMessage());
        }

        // Auto-ajustar columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Método `createChartsAndIndicatorsSheet` eliminado durante limpieza del código.

    private void createProductSalesAnalysisSheet(Workbook workbook, List<Transaction> transactions, Long storeId) {
        Sheet sheet = workbook.createSheet("Análisis Ventas Productos");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 20);
        sheet.setColumnWidth(1, 16);
        sheet.setColumnWidth(2, 16);
        sheet.setColumnWidth(3, 16);
        sheet.setColumnWidth(4, 16);
        sheet.setColumnWidth(5, 14);

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("RANKING DE PRODUCTOS POR VENTAS");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 5));

        rowNum++; // Espacio

        // Encabezados
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Producto", "Cantidad Vendida", "Ingresos", "Costo Total", "Ganancia", "Posición"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Recopilar datos de productos (solo SALIDA que no sean AJUSTE, y productos activos)
        Map<String, Integer> productQuantity = new TreeMap<>();
        Map<String, BigDecimal> productRevenue = new HashMap<>();
        Map<String, BigDecimal> productCost = new HashMap<>();
        Map<String, BigDecimal> productGain = new HashMap<>();

        for (Transaction t : transactions) {
            if ("SALIDA".equalsIgnoreCase(t.getType())) {
                try {
                    // Excluir movimientos AJUSTE
                    boolean isAdjustmentReason = "AJUSTE".equalsIgnoreCase(t.getReason());
                    if (isAdjustmentReason) {
                        continue;
                    }

                    Product product = t.getProduct();
                    // Validar que el producto sea elegible (activo y de la tienda correcta)
                    if (product != null && isProductEligibleForExport(product, storeId)) {
                        String productKey = product.getName();
                        productQuantity.put(productKey, productQuantity.getOrDefault(productKey, 0) + t.getQuantity());
                        
                        BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                        BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                        
                        BigDecimal revenue = price.multiply(new BigDecimal(t.getQuantity()));
                        BigDecimal costVal = cost.multiply(new BigDecimal(t.getQuantity()));
                        BigDecimal gain = revenue.subtract(costVal);
                        
                        productRevenue.put(productKey, productRevenue.getOrDefault(productKey, BigDecimal.ZERO).add(revenue));
                        productCost.put(productKey, productCost.getOrDefault(productKey, BigDecimal.ZERO).add(costVal));
                        productGain.put(productKey, productGain.getOrDefault(productKey, BigDecimal.ZERO).add(gain));
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }

        // Ordenar por cantidad vendida (descendente)
        List<Map.Entry<String, Integer>> sortedProducts = productQuantity.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());

        int posicion = 1;
        for (Map.Entry<String, Integer> entry : sortedProducts) {
            String productName = entry.getKey();
            Integer quantity = entry.getValue();

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(14);

            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(productName);
            nameCell.setCellStyle(dataCellStyle);

            Cell qtyCell = row.createCell(1);
            qtyCell.setCellValue(quantity);
            qtyCell.setCellStyle(numberStyle);

            Cell revenueCell = row.createCell(2);
            revenueCell.setCellValue(productRevenue.getOrDefault(productName, BigDecimal.ZERO).doubleValue());
            revenueCell.setCellStyle(currencyStyle);

            Cell costCell = row.createCell(3);
            costCell.setCellValue(productCost.getOrDefault(productName, BigDecimal.ZERO).doubleValue());
            costCell.setCellStyle(currencyStyle);

            Cell gainCell = row.createCell(4);
            gainCell.setCellValue(productGain.getOrDefault(productName, BigDecimal.ZERO).doubleValue());
            gainCell.setCellStyle(currencyStyle);

            Cell positionCell = row.createCell(5);
            positionCell.setCellValue(posicion++);
            positionCell.setCellStyle(numberStyle);
        }

        // Agregar sección de productos sin venta
        rowNum += 2;
        Row noSalesHeaderRow = sheet.createRow(rowNum++);
        noSalesHeaderRow.setHeightInPoints(18);
        Cell noSalesHeaderCell = noSalesHeaderRow.createCell(0);
        noSalesHeaderCell.setCellValue("PRODUCTOS SIN VENTA");
        noSalesHeaderCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 5));

        try {
            List<Product> allProducts = productService.findAll();
            List<Product> productsWithoutSales = new ArrayList<>();
            
            for (Product p : allProducts) {
                // Solo mostrar productos activos, sin desactivados ni eliminados
                if (isProductEligibleForExport(p, storeId)) {
                    if (!productQuantity.containsKey(p.getName())) {
                        productsWithoutSales.add(p);
                    }
                }
            }

            if (productsWithoutSales.isEmpty()) {
                Row noDataRow = sheet.createRow(rowNum++);
                Cell noDataCell = noDataRow.createCell(0);
                noDataCell.setCellValue("Todos los productos han sido vendidos!");
                noDataCell.setCellStyle(dataCellStyle);
            } else {
                Row headerRow2 = sheet.createRow(rowNum++);
                headerRow2.setHeightInPoints(16);
                String[] headers2 = {"Producto", "Stock Actual", "Precio", "Costo", "", ""};
                for (int i = 0; i < headers2.length; i++) {
                    Cell cell = headerRow2.createCell(i);
                    cell.setCellValue(headers2[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (Product p : productsWithoutSales) {
                    Row row = sheet.createRow(rowNum++);
                    row.setHeightInPoints(14);

                    Cell nameCell = row.createCell(0);
                    nameCell.setCellValue(p.getName());
                    nameCell.setCellStyle(dataCellStyle);

                    Cell stockCell = row.createCell(1);
                    stockCell.setCellValue(p.getStock());
                    stockCell.setCellStyle(numberStyle);

                    Cell priceCell = row.createCell(2);
                    priceCell.setCellValue(p.getPrice() != null ? p.getPrice().doubleValue() : 0);
                    priceCell.setCellStyle(currencyStyle);

                    Cell costCell = row.createCell(3);
                    costCell.setCellValue(p.getCost() != null ? p.getCost().doubleValue() : 0);
                    costCell.setCellStyle(currencyStyle);
                }
            }
        } catch (Exception e) {
            Row errorRow = sheet.createRow(rowNum);
            Cell errorCell = errorRow.createCell(0);
            errorCell.setCellValue("Error al cargar productos sin venta: " + e.getMessage());
        }

        // Auto-ajustar columnas
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createAnalysisByLabelsSheet(Workbook workbook, List<Transaction> transactions, Long storeId) {
        Sheet sheet = workbook.createSheet("Etiquetas");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);

        // Configurar anchos de columnas - MUCHO MÁS GRANDES para ser visibles
        sheet.setColumnWidth(0, 35 * 256);  // Producto
        sheet.setColumnWidth(1, 25 * 256);  // Cantidad Entrada
        sheet.setColumnWidth(2, 25 * 256);  // Cantidad Salida
        sheet.setColumnWidth(3, 20 * 256);  // Stock Actual
        sheet.setColumnWidth(4, 20 * 256);  // Costo Unit
        sheet.setColumnWidth(5, 20 * 256);  // Precio Unit
        sheet.setColumnWidth(6, 3 * 256);   // Espacio separador

        int rowNum = 0;

        // TÍTULO PRINCIPAL
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS POR ETIQUETAS");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 5));

        rowNum++; // Espacio después del título

        // Obtener todos los productos y agrupar por etiqueta (solo productos activos)
        try {
            List<Product> allProducts = productService.findAll();
            
            // Agrupar por etiqueta
            Map<String, List<Product>> productsByLabel = new HashMap<>();
            
            for (Product product : allProducts) {
                // Solo incluir productos activos y de la tienda correcta
                if (!isProductEligibleForExport(product, storeId)) continue;
                
                // Si el producto no tiene etiquetas o está vacío, lo agrupamos bajo "Sin Etiqueta"
                if (product.getTags() == null || product.getTags().isEmpty()) {
                    productsByLabel.computeIfAbsent("Sin Etiqueta", k -> new ArrayList<>()).add(product);
                } else {
                    for (ProductTag productTag : product.getTags()) {
                        String labelName = productTag.getTag().getName();
                        productsByLabel.computeIfAbsent(labelName, k -> new ArrayList<>()).add(product);
                    }
                }
            }

            // Ordenar las etiquetas
            List<Map.Entry<String, List<Product>>> sortedLabels = productsByLabel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

            // Crear tablas de una en una (una etiqueta por fila)
            for (Map.Entry<String, List<Product>> labelEntry : sortedLabels) {
                rowNum = createLabelTable(sheet, rowNum, labelEntry, 0, transactions, headerStyle, numberStyle, currencyStyle, dataCellStyle);
                rowNum += 2; // Espacio entre tablas
            }

        } catch (Exception e) {
            System.err.println("Error al crear hoja de análisis por etiquetas: " + e.getMessage());
            e.printStackTrace();
            Row errorRow = sheet.createRow(rowNum);
            Cell errorCell = errorRow.createCell(0);
            errorCell.setCellValue("Error al cargar etiquetas: " + e.getMessage());
        }
    }

    private int createLabelTable(Sheet sheet, int startRow, Map.Entry<String, List<Product>> labelEntry, int startCol, 
                                  List<Transaction> transactions, CellStyle headerStyle, CellStyle numberStyle, 
                                  CellStyle currencyStyle, CellStyle dataCellStyle) {
        String labelName = labelEntry.getKey();
        List<Product> products = labelEntry.getValue();
        
        int rowNum = startRow;
        
        // Subtítulo de la etiqueta
        Row subtitleRow = sheet.createRow(rowNum++);
        subtitleRow.setHeightInPoints(18);
        Cell subtitleCell = subtitleRow.createCell(startCol);
        subtitleCell.setCellValue("Etiqueta: " + labelName);
        
        CellStyle subtitleStyle = sheet.getWorkbook().createCellStyle();
        Font subtitleFont = sheet.getWorkbook().createFont();
        subtitleFont.setBold(true);
        subtitleFont.setFontHeightInPoints((short) 12);
        subtitleStyle.setFont(subtitleFont);
        subtitleStyle.setAlignment(HorizontalAlignment.LEFT);
        subtitleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        subtitleCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, startCol, startCol + 5));

        // Encabezados de columnas
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(16);
        String[] headers = {"Producto", "Cantidad Entrada", "Cantidad Salida", "Stock Actual", "Costo Unit", "Precio Unit"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell headerCell = headerRow.createCell(startCol + i);
            headerCell.setCellValue(headers[i]);
            headerCell.setCellStyle(headerStyle);
        }

        // Datos de productos
        for (Product product : products) {
            // Calcular cantidades (excluir transacciones AJUSTE)
            int cantEntrada = transactions.stream()
                .filter(t -> t.getProduct() != null && t.getProduct().getId().equals(product.getId()))
                .filter(t -> "ENTRADA".equalsIgnoreCase(t.getType()))
                .filter(t -> !"AJUSTE".equalsIgnoreCase(t.getReason()))
                .mapToInt(Transaction::getQuantity)
                .sum();

            int cantSalida = transactions.stream()
                .filter(t -> t.getProduct() != null && t.getProduct().getId().equals(product.getId()))
                .filter(t -> "SALIDA".equalsIgnoreCase(t.getType()))
                .filter(t -> !"AJUSTE".equalsIgnoreCase(t.getReason()))
                .mapToInt(Transaction::getQuantity)
                .sum();

            BigDecimal costUnit = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
            BigDecimal priceUnit = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(14);

            Cell productCell = row.createCell(startCol);
            productCell.setCellValue(product.getName());
            productCell.setCellStyle(dataCellStyle);

            Cell entCell = row.createCell(startCol + 1);
            entCell.setCellValue(cantEntrada);
            entCell.setCellStyle(numberStyle);

            Cell salCell = row.createCell(startCol + 2);
            salCell.setCellValue(cantSalida);
            salCell.setCellStyle(numberStyle);

            Cell stockCell = row.createCell(startCol + 3);
            stockCell.setCellValue(product.getStock());
            stockCell.setCellStyle(numberStyle);

            Cell costCell = row.createCell(startCol + 4);
            costCell.setCellValue(costUnit.doubleValue());
            costCell.setCellStyle(currencyStyle);

            Cell priceCell = row.createCell(startCol + 5);
            priceCell.setCellValue(priceUnit.doubleValue());
            priceCell.setCellStyle(currencyStyle);
        }

        return rowNum;
    }

    // Método `createRecommendationsSheet` eliminado durante limpieza del código.

    private void createStockRotationSheet(Workbook workbook, List<Transaction> transactions, Long storeId, LocalDate dateFrom, LocalDate dateTo) {
        Sheet sheet = workbook.createSheet("Rotación de Stock");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        CellStyle percentStyle = createPercentageStyle(workbook);
        
        int rowNum = 0;
        
        // Configurar anchos de columnas (13 columnas: A-M)
        sheet.setColumnWidth(0, 24);   // A - Producto
        sheet.setColumnWidth(1, 18);   // B - Cantidad Entrada
        sheet.setColumnWidth(2, 18);   // C - Cantidad Salida
        sheet.setColumnWidth(3, 16);   // D - Stock Actual
        sheet.setColumnWidth(4, 14);   // E - Costo Unit
        sheet.setColumnWidth(5, 14);   // F - Precio Unit
        sheet.setColumnWidth(6, 18);   // G - Costo Invertido
        sheet.setColumnWidth(7, 16);   // H - Precio total
        sheet.setColumnWidth(8, 16);   // I - Costo total
        sheet.setColumnWidth(9, 16);   // J - Ingreso Total
        sheet.setColumnWidth(10, 18);  // K - Ganancia Total
        sheet.setColumnWidth(11, 14);  // L - Ganancia %
        sheet.setColumnWidth(12, 22);  // M - Prioridad
        
        // ========== TÍTULO Y PERÍODO ==========
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS POR PRODUCTO");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 12));
        
        rowNum++; // Espacio
        
        Row periodRow = sheet.createRow(rowNum++);
        Cell periodCell = periodRow.createCell(0);
        periodCell.setCellValue("Período: " + dateFrom + " a " + dateTo + " (" + java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + " días)");
        periodCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 12));
        
        rowNum++; // Espacio
        
        // ========== ENCABEZADOS DE COLUMNAS ==========
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(20);
        for (int i = 0; i < 13; i++) {
            headerRow.createCell(i).setCellStyle(headerStyle);
        }
        
        String[] headers = {
            "Producto", 
            "Cantidad Entrada", 
            "Cantidad Salida",
            "Stock Actual",
            "Costo Unit",
            "Precio Unit",
            "Costo Invertido",
            "Precio total",
            "Costo total",
            "Ingreso Total",
            "Ganancia Total",
            "Ganancia %",
            "Prioridad"
        };
        
        for (int i = 0; i < headers.length; i++) {
            headerRow.getCell(i).setCellValue(headers[i]);
        }
        
        rowNum++; // Espacio
        
        // ========== CALCULAR DATOS ==========
        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        Map<Long, RotationAnalysisData> productRotationData = calculateRotationData(transactions, storeId, periodDays, dateFrom, dateTo);
        
        // ========== LLENAR TABLA DE PRODUCTOS ==========
        for (RotationAnalysisData data : productRotationData.values()) {
            Row dataRow = sheet.createRow(rowNum++);
            dataRow.setHeightInPoints(16);
            
            int col = 0;
            
            // Producto
            Cell prodCell = dataRow.createCell(col++);
            prodCell.setCellValue(data.productName);
            prodCell.setCellStyle(dataCellStyle);
            
            // Cantidad Entrada
            Cell entradasCell = dataRow.createCell(col++);
            entradasCell.setCellValue(data.entradas);
            entradasCell.setCellStyle(numberStyle);
            
            // Cantidad Salida
            Cell salidasCell = dataRow.createCell(col++);
            salidasCell.setCellValue(data.salidas);
            salidasCell.setCellStyle(numberStyle);
            
            // Stock Actual
            Cell stockCell = dataRow.createCell(col++);
            stockCell.setCellValue(data.currentStock);
            stockCell.setCellStyle(numberStyle);
            
            // Costo Unit
            Cell costoUnitCell = dataRow.createCell(col++);
            costoUnitCell.setCellValue(data.costUnit.doubleValue());
            costoUnitCell.setCellStyle(currencyStyle);
            
            // Precio Unit
            Cell precioUnitCell = dataRow.createCell(col++);
            precioUnitCell.setCellValue(data.priceUnit.doubleValue());
            precioUnitCell.setCellStyle(currencyStyle);
            
            // Costo Invertido (Costo Unit × Cantidad Entrada)
            BigDecimal costoInvertido = data.costUnit.multiply(new BigDecimal(data.entradas));
            Cell costoInvCell = dataRow.createCell(col++);
            costoInvCell.setCellValue(costoInvertido.doubleValue());
            costoInvCell.setCellStyle(currencyStyle);
            
            // Precio total (Precio Unit × Cantidad Salida, no Entrada)
            BigDecimal precioTotal = data.priceUnit.multiply(new BigDecimal(data.salidas));
            Cell precioTotalCell = dataRow.createCell(col++);
            precioTotalCell.setCellValue(precioTotal.doubleValue());
            precioTotalCell.setCellStyle(currencyStyle);
            
            // Costo total (Costo Unit × Cantidad Salida)
            BigDecimal costoTotal = data.costUnit.multiply(new BigDecimal(data.salidas));
            Cell costoTotalCell = dataRow.createCell(col++);
            costoTotalCell.setCellValue(costoTotal.doubleValue());
            costoTotalCell.setCellStyle(currencyStyle);
            
            // Ingreso Total (Precio Unit × Cantidad Salida)
            BigDecimal ingresoTotal = data.priceUnit.multiply(new BigDecimal(data.salidas));
            Cell ingresoCell = dataRow.createCell(col++);
            ingresoCell.setCellValue(ingresoTotal.doubleValue());
            ingresoCell.setCellStyle(currencyStyle);
            
            // Ganancia Total (Ingreso Total - Costo total)
            BigDecimal gananciaTotal = ingresoTotal.subtract(costoTotal);
            Cell gananciaCell = dataRow.createCell(col++);
            gananciaCell.setCellValue(gananciaTotal.doubleValue());
            gananciaCell.setCellStyle(currencyStyle);
            
            // Ganancia % ((Ganancia Total / Ingreso Total))
            Cell margenCell = dataRow.createCell(col++);
            if (ingresoTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margenPct = gananciaTotal.divide(ingresoTotal, 4, java.math.RoundingMode.HALF_UP);
                margenCell.setCellValue(margenPct.doubleValue());
            } else {
                margenCell.setCellValue(0);
            }
            margenCell.setCellStyle(percentStyle);
            
            // Prioridad
            Cell prioridadCell = dataRow.createCell(col);
            prioridadCell.setCellValue(data.prioridad);
            prioridadCell.setCellStyle(dataCellStyle);
        }
        
        rowNum += 2; // Espacio
        
        // ========== SECCIÓN DE ROTACIÓN ==========
        Row rotHeaderRow = sheet.createRow(rowNum++);
        rotHeaderRow.setHeightInPoints(18);
        Cell rotHeaderCell = rotHeaderRow.createCell(0);
        rotHeaderCell.setCellValue("Rotación");
        rotHeaderCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 4));
        
        rowNum++; // Espacio
        
        // Encabezados de tabla de rotación
        Row headerRotRow = sheet.createRow(rowNum++);
        headerRotRow.setHeightInPoints(14);
        String[] rotHeaders = {"Producto", "Stock Inicial", "Stock Final", "Inv. Promedio", "Rotación"};
        for (int i = 0; i < rotHeaders.length; i++) {
            Cell headerCell = headerRotRow.createCell(i);
            headerCell.setCellValue(rotHeaders[i]);
            headerCell.setCellStyle(headerStyle);
        }
        
        // Listar todos los productos en tabla
        for (RotationAnalysisData product : productRotationData.values()) {
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(13);
            
            int col = 0;
            
            // Producto
            Cell prodCell = row.createCell(col++);
            prodCell.setCellValue(product.productName);
            prodCell.setCellStyle(dataCellStyle);
            
            // Stock Inicial - VACÍO para que lo llene el usuario
            Cell stockInitCell = row.createCell(col++);
            stockInitCell.setCellStyle(dataCellStyle);
            
            // Stock Final - VACÍO para que lo llene el usuario
            Cell stockFinalCell = row.createCell(col++);
            stockFinalCell.setCellStyle(dataCellStyle);
            
            // Inventario Promedio - VACÍO para que lo llene el usuario
            Cell invPromCell = row.createCell(col++);
            invPromCell.setCellStyle(dataCellStyle);
            
            // Rotación - VACÍO para que lo llene el usuario
            Cell rotCell = row.createCell(col++);
            rotCell.setCellStyle(dataCellStyle);
        }
        
        // Auto-ajustar columnas
        for (int i = 0; i < 13; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Método auxiliar para calcular datos de rotación
    private Map<Long, RotationAnalysisData> calculateRotationData(List<Transaction> transactions, Long storeId, 
                                                                   long periodDays, LocalDate dateFrom, LocalDate dateTo) {
        Map<Long, RotationAnalysisData> data = new HashMap<>();
        
        try {
            // Filtrar solo productos activos y elegibles
            List<Product> eligibleProducts = new ArrayList<>();
            List<Transaction> activeTransactions = filterActiveProductTransactions(transactions);
            
            for (Transaction t : activeTransactions) {
                Product product = t.getProduct();
                if (product != null && isProductEligibleForExport(product) && 
                    !eligibleProducts.stream().anyMatch(p -> p.getId().equals(product.getId()))) {
                    eligibleProducts.add(product);
                }
            }
            
            for (Product product : eligibleProducts) {
                RotationAnalysisData rotData = new RotationAnalysisData();
                rotData.productName = product.getName();
                rotData.currentStock = product.getStock();
                rotData.costUnit = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                rotData.priceUnit = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                
                // Filtrar transacciones del producto en el período (solo activas)
                List<Transaction> productTransactions = activeTransactions.stream()
                    .filter(t -> t.getProduct() != null && t.getProduct().getId().equals(product.getId()))
                    .filter(t -> t.getDateTime() != null)
                    .filter(t -> !t.getDateTime().toLocalDate().isBefore(dateFrom) && 
                                 !t.getDateTime().toLocalDate().isAfter(dateTo))
                    .collect(Collectors.toList());
                
                // Contar ENTRADA y SALIDA excluyendo AJUSTE
                rotData.entradas = productTransactions.stream()
                    .filter(t -> "ENTRADA".equalsIgnoreCase(t.getType()) && !"AJUSTE".equalsIgnoreCase(t.getReason()))
                    .mapToInt(Transaction::getQuantity)
                    .sum();
                
                rotData.salidas = productTransactions.stream()
                    .filter(t -> "SALIDA".equalsIgnoreCase(t.getType()) && !"AJUSTE".equalsIgnoreCase(t.getReason()))
                    .mapToInt(Transaction::getQuantity)
                    .sum();
                
                // MÉTRICAS DE ROTACIÓN
                rotData.velocidadDiaria = periodDays > 0 ? (double) rotData.salidas / periodDays : 0;
                
                // Calcular Stock Inicial: Stock Final - (Salidas - Entradas)
                int stockInicial = rotData.currentStock - (rotData.salidas - rotData.entradas);
                if (stockInicial < 0) stockInicial = 0; // No puede ser negativo
                
                // Guardar en el objeto
                rotData.stockInicial = stockInicial;
                rotData.stockFinal = rotData.currentStock;
                
                // Inventario Promedio = (Stock Inicial + Stock Final) / 2
                int inventarioPromedio = (stockInicial + rotData.currentStock) / 2;
                rotData.inventarioPromedio = inventarioPromedio;
                
                // Rotación = Salidas / Inventario Promedio
                rotData.rotacion = inventarioPromedio > 0 ? (double) rotData.salidas / inventarioPromedio : 0;
                
                rotData.diasCobertura = rotData.velocidadDiaria > 0 ? rotData.currentStock / rotData.velocidadDiaria : 0;
                
                // FINANCIERO & ANÁLISIS
                // Ganancia solo en SALIDA (no en ENTRADA)
                rotData.margenAbsoluto = rotData.priceUnit.subtract(rotData.costUnit);
                rotData.margenPorcentaje = rotData.priceUnit.compareTo(BigDecimal.ZERO) > 0 
                    ? rotData.margenAbsoluto.divide(rotData.priceUnit, 4, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                
                // STOCK & PRIORIDAD
                if (rotData.currentStock < 3) {
                    rotData.prioridad = "🔴 URGENTE";
                } else if (rotData.currentStock < 5) {
                    rotData.prioridad = "🟡 ALTA";
                } else {
                    rotData.prioridad = "🟢 NORMAL";
                }
                
                data.put(product.getId(), rotData);
            }
        } catch (Exception e) {
            System.err.println("Error calculando rotación: " + e.getMessage());
        }
        
        return data;
    }

    // Clase auxiliar para datos de rotación
    @SuppressWarnings("unused")
    private class RotationAnalysisData {
        String productName;
        int currentStock;
        int stockInicial;
        int stockFinal;
        int inventarioPromedio;
        int entradas;
        int salidas;
        double velocidadDiaria;
        double rotacion;
        double diasCobertura;
        BigDecimal costUnit;
        BigDecimal priceUnit;
        BigDecimal margenAbsoluto;
        BigDecimal margenPorcentaje;
        String prioridad;
    }

    // Estilos - LIMPIOS Y CLAROS SIN COLORES
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        addBorders(style);
        return style;
    }

    private CellStyle createLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        addBorders(style);
        return style;
    }



    private CellStyle createPercentageStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        addBorders(style);
        return style;
    }
// Estilos para datos específicos 
    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        // Usar formato que muestre solo decimales necesarios
        style.setDataFormat(workbook.createDataFormat().getFormat("General"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createCurrencyWithSymbolStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        // Usar formato con símbolo $ que no muestre decimales innecesarios
        style.setDataFormat(workbook.createDataFormat().getFormat("\"$\"####;\"-$\"-####"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createDateTimeStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0"));
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createSubtitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createDataCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(false);
        addBorders(style);
        return style;
    }



    private void addBorders(Cell cell) {
        CellStyle style = cell.getCellStyle();
        if (style == null) {
            style = cell.getSheet().getWorkbook().createCellStyle();
        }
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        cell.setCellStyle(style);
    }

    private void addBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

}

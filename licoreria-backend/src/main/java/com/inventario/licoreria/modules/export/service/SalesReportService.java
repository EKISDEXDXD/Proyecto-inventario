package com.inventario.licoreria.modules.export.service;

import com.inventario.licoreria.modules.administrative_costs.model.AdministrativeCostMovement;
import com.inventario.licoreria.modules.administrative_costs.service.AdministrativeCostMovementService;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.service.TransactionService;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.modules.users.model.User;
import com.inventario.licoreria.modules.users.service.UserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

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
        try {
            Workbook workbook = new XSSFWorkbook();

            // Obtener todas las transacciones
            List<Transaction> allTransactions = transactionService.findAll();
            
            // Filtrar por tienda y rango de fechas
            List<Transaction> transactions = allTransactions.stream()
                .filter(t -> {
                    try {
                        // Usar la relación Product desde Transaction
                        Product product = t.getProduct();
                        if (product == null) {
                            return false;
                        }
                        return product.getStore() != null && product.getStore().getId().equals(storeId) 
                            && t.getDateTime().toLocalDate().isAfter(dateFrom.minusDays(1))
                            && t.getDateTime().toLocalDate().isBefore(dateTo.plusDays(1));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

            // Crear hojas según tipo de reporte
            createExecutiveSummarySheet(workbook, transactions);
            createDetailedMovementsSheet(workbook, transactions);
            createAdministrativeCostsSheet(workbook, storeId, dateFrom, dateTo);
            
            // Si es COMPLETE, agregar hojas adicionales
            if ("COMPLETE".equalsIgnoreCase(reportType)) {
                createProductAnalysisSheet(workbook, transactions, storeId);
                createDailyCashFlowSheet(workbook, transactions);
                createChartsAndIndicatorsSheet(workbook, transactions, storeId);
                createProductSalesAnalysisSheet(workbook, transactions, storeId);
                createRecommendationsSheet(workbook, transactions, storeId);
                createStockRotationSheet(workbook, transactions, storeId);
            }
            // Para DAILY, no hay hojas adicionales (similar a SIMPLE)

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

    private void createExecutiveSummarySheet(Workbook workbook, List<Transaction> transactions) {
        Sheet sheet = workbook.createSheet("Resumen Ejecutivo");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle totalStyle = createTotalStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle currencyWithSymbolStyle = createCurrencyWithSymbolStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);
        CellStyle percentStyle = createPercentageStyle(workbook);

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
        if (!transactions.isEmpty()) {
            LocalDate minDate = transactions.stream()
                .map(t -> t.getDateTime().toLocalDate())
                .min(LocalDate::compareTo).orElse(LocalDate.now());
            LocalDate maxDate = transactions.stream()
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

        for (Transaction t : transactions) {
            Product product = t.getProduct();
            if (product == null) continue;

            if ("ENTRADA".equalsIgnoreCase(t.getType())) {
                BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                totalEntradas = totalEntradas.add(cost.multiply(new BigDecimal(t.getQuantity())));
            } else if ("SALIDA".equalsIgnoreCase(t.getType())) {
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

        for (Transaction t : transactions) {
            if ("SALIDA".equalsIgnoreCase(t.getType())) {
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

        // Auto-ajustar columnas
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
    }

    private void createDetailedMovementsSheet(Workbook workbook, List<Transaction> transactions) {
        Sheet sheet = workbook.createSheet("Movimientos Detallados");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle dateTimeStyle = createDateTimeStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 24);  // Para fechas completas con hora
        sheet.setColumnWidth(1, 12);
        sheet.setColumnWidth(2, 20);
        sheet.setColumnWidth(3, 12);
        sheet.setColumnWidth(4, 16);  // Aumentado para dinero
        sheet.setColumnWidth(5, 16);  // Aumentado para dinero
        sheet.setColumnWidth(6, 16);  // Aumentado para dinero
        sheet.setColumnWidth(7, 16);  // Aumentado para dinero
        sheet.setColumnWidth(8, 16);  // Aumentado para dinero
        sheet.setColumnWidth(9, 16);  // Aumentado para dinero
        sheet.setColumnWidth(10, 15);

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("MOVIMIENTOS DETALLADOS");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 10));

        // Encabezados de columnas (sin espacios extras)
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Fecha", "Tipo", "Producto", "Cantidad", "Costo Unit", "Costo Total", 
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
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 10));

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

                Cell productCell = row.createCell(2);
                productCell.setCellValue(product.getName());
                productCell.setCellStyle(dataCellStyle);

                Cell qtyCell = row.createCell(3);
                qtyCell.setCellValue(t.getQuantity());
                qtyCell.setCellStyle(numberStyle);

                // Handle null prices/costs
                BigDecimal costUnit = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                BigDecimal priceUnit = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal costTotal = costUnit.multiply(new BigDecimal(t.getQuantity()));
                BigDecimal priceTotal = priceUnit.multiply(new BigDecimal(t.getQuantity()));
                BigDecimal gainUnit = priceUnit.subtract(costUnit);
                BigDecimal gainTotal = "SALIDA".equalsIgnoreCase(t.getType()) 
                    ? gainUnit.multiply(new BigDecimal(t.getQuantity())) 
                    : BigDecimal.ZERO;

                row.createCell(4).setCellValue(costUnit.doubleValue());
                row.getCell(4).setCellStyle(currencyStyle);
                
                row.createCell(5).setCellValue(costTotal.doubleValue());
                row.getCell(5).setCellStyle(currencyStyle);
                
                row.createCell(6).setCellValue(priceUnit.doubleValue());
                row.getCell(6).setCellStyle(currencyStyle);
                
                row.createCell(7).setCellValue(priceTotal.doubleValue());
                row.getCell(7).setCellStyle(currencyStyle);
                
                row.createCell(8).setCellValue(gainUnit.doubleValue());
                row.getCell(8).setCellStyle(currencyStyle);
                
                row.createCell(9).setCellValue(gainTotal.doubleValue());
                row.getCell(9).setCellStyle(currencyStyle);

                Cell userCell = row.createCell(10);
                userCell.setCellValue(user != null ? user.getUsername() : "N/A");
                userCell.setCellStyle(dataCellStyle);
            }
        }

        // Auto-ajustar columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createProductAnalysisSheet(Workbook workbook, List<Transaction> transactions, Long storeId) {
        Sheet sheet = workbook.createSheet("Análisis por Producto");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle percentStyle = createPercentageStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 25);  // Producto
        sheet.setColumnWidth(1, 16);  // Cantidad Entrada
        sheet.setColumnWidth(2, 16);  // Cantidad Salida
        sheet.setColumnWidth(3, 14);  // Stock Actual
        sheet.setColumnWidth(4, 16);  // Costo Invertido
        sheet.setColumnWidth(5, 16);  // Ingreso Total
        sheet.setColumnWidth(6, 16);  // Ganancia Total
        sheet.setColumnWidth(7, 14);  // Ganancia %

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS POR PRODUCTO");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 7));

        rowNum++; // Espacio

        // Encabezados
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Producto", "Cantidad Entrada", "Cantidad Salida", "Stock Actual", 
                          "Costo Invertido", "Ingreso Total", "Ganancia Total", "Ganancia %"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Agrupar por producto
        Map<Long, List<Transaction>> transactionsByProductId = transactions.stream()
            .filter(t -> t.getProduct() != null)
            .collect(Collectors.groupingBy(
                t -> t.getProduct().getId(),
                Collectors.toList()
            ));

        boolean altRow = false;
        for (Map.Entry<Long, List<Transaction>> entry : transactionsByProductId.entrySet()) {
            Long productId = entry.getKey();
            List<Transaction> productTransactions = entry.getValue();
            
            if (productTransactions.isEmpty()) continue;
            Product product = productTransactions.get(0).getProduct();
            if (product == null || product.getStore() == null || !product.getStore().getId().equals(storeId)) continue;

            int cantEntrada = productTransactions.stream()
                .filter(t -> "ENTRADA".equalsIgnoreCase(t.getType()))
                .mapToInt(Transaction::getQuantity)
                .sum();

            int cantSalida = productTransactions.stream()
                .filter(t -> "SALIDA".equalsIgnoreCase(t.getType()))
                .mapToInt(Transaction::getQuantity)
                .sum();

            BigDecimal costInvested = productTransactions.stream()
                .filter(t -> "ENTRADA".equalsIgnoreCase(t.getType()))
                .map(t -> {
                    BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                    return cost.multiply(new BigDecimal(t.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal ingresoTotal = productTransactions.stream()
                .filter(t -> "SALIDA".equalsIgnoreCase(t.getType()))
                .map(t -> {
                    BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                    return price.multiply(new BigDecimal(t.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal costSold = productTransactions.stream()
                .filter(t -> "SALIDA".equalsIgnoreCase(t.getType()))
                .map(t -> {
                    BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                    return cost.multiply(new BigDecimal(t.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal gananciaTotal = ingresoTotal.subtract(costSold);
            BigDecimal gananciaPercent = ingresoTotal.compareTo(BigDecimal.ZERO) > 0 
                ? gananciaTotal.divide(ingresoTotal, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(14);

            Cell productCell = row.createCell(0);
            productCell.setCellValue(product.getName());
            productCell.setCellStyle(dataCellStyle);

            Cell entCell = row.createCell(1);
            entCell.setCellValue(cantEntrada);
            entCell.setCellStyle(numberStyle);

            Cell salCell = row.createCell(2);
            salCell.setCellValue(cantSalida);
            salCell.setCellStyle(numberStyle);

            Cell stockCell = row.createCell(3);
            stockCell.setCellValue(product.getStock());
            stockCell.setCellStyle(numberStyle);
            
            row.createCell(4).setCellValue(costInvested.doubleValue());
            row.getCell(4).setCellStyle(currencyStyle);

            row.createCell(5).setCellValue(ingresoTotal.doubleValue());
            row.getCell(5).setCellStyle(currencyStyle);

            row.createCell(6).setCellValue(gananciaTotal.doubleValue());
            row.getCell(6).setCellStyle(currencyStyle);

            row.createCell(7).setCellValue(gananciaPercent.doubleValue());
            row.getCell(7).setCellStyle(percentStyle);
        }

        // Auto-ajustar columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createDailyCashFlowSheet(Workbook workbook, List<Transaction> transactions) {
        Sheet sheet = workbook.createSheet("Flujo Caja Diario");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 18);  // Fecha
        sheet.setColumnWidth(1, 18);  // Entradas
        sheet.setColumnWidth(2, 18);  // Salidas
        sheet.setColumnWidth(3, 18);  // Ganancia Neta

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("FLUJO CAJA DIARIO");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 3));

        rowNum++; // Espacio

        // Encabezados
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Día", "Entradas", "Salidas", "Ganancia Neta"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Agrupar por día (filtrar transacciones sin dateTime válido)
        Map<LocalDate, List<Transaction>> transactionsByDay = transactions.stream()
            .filter(t -> t.getDateTime() != null)
            .collect(Collectors.groupingBy(
                t -> t.getDateTime().toLocalDate(),
                TreeMap::new,
                Collectors.toList()
            ));

        for (Map.Entry<LocalDate, List<Transaction>> entry : transactionsByDay.entrySet()) {
            LocalDate day = entry.getKey();
            List<Transaction> dayTransactions = entry.getValue();

            BigDecimal entradas = BigDecimal.ZERO;
            BigDecimal salidas = BigDecimal.ZERO;

            for (Transaction t : dayTransactions) {
                Product product;
                try {
                    product = productService.findById(t.getProductId());
                } catch (Exception e) {
                    continue;
                }
                if (product == null) continue;

                if ("ENTRADA".equalsIgnoreCase(t.getType())) {
                    entradas = entradas.add(product.getCost().multiply(new BigDecimal(t.getQuantity())));
                } else if ("SALIDA".equalsIgnoreCase(t.getType())) {
                    salidas = salidas.add(product.getPrice().multiply(new BigDecimal(t.getQuantity())));
                }
            }

            BigDecimal ganancia = salidas.subtract(entradas);

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(14);

            Cell dayCell = row.createCell(0);
            dayCell.setCellValue(day);
            dayCell.setCellStyle(dateStyle);
            addBorders(dayCell);
            
            row.createCell(1).setCellValue(entradas.doubleValue());
            row.getCell(1).setCellStyle(currencyStyle);
            addBorders(row.getCell(1));

            row.createCell(2).setCellValue(salidas.doubleValue());
            row.getCell(2).setCellStyle(currencyStyle);
            addBorders(row.getCell(2));

            row.createCell(3).setCellValue(ganancia.doubleValue());
            row.getCell(3).setCellStyle(currencyStyle);
            addBorders(row.getCell(3));
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

    private void createChartsAndIndicatorsSheet(Workbook workbook, List<Transaction> transactions, Long storeId) {
        Sheet sheet = workbook.createSheet("Gráficos e Indicadores");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle percentStyle = createPercentageStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 25);
        sheet.setColumnWidth(1, 18);
        sheet.setColumnWidth(2, 18);

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("GRÁFICOS E INDICADORES");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 2));

        rowNum++; // Espacio

        // CÁLCULOS PRINCIPALES
        BigDecimal totalEntradas = BigDecimal.ZERO;
        BigDecimal totalSalidas = BigDecimal.ZERO;
        BigDecimal totalCostSold = BigDecimal.ZERO;
        int totalProductsSold = 0;
        int totalProductsBought = 0;

        for (Transaction t : transactions) {
            Product product = t.getProduct();
            if (product == null) continue;

            if ("ENTRADA".equalsIgnoreCase(t.getType())) {
                BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                totalEntradas = totalEntradas.add(cost.multiply(new BigDecimal(t.getQuantity())));
                totalProductsBought += t.getQuantity();
            } else if ("SALIDA".equalsIgnoreCase(t.getType())) {
                BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                totalSalidas = totalSalidas.add(price.multiply(new BigDecimal(t.getQuantity())));
                totalCostSold = totalCostSold.add(cost.multiply(new BigDecimal(t.getQuantity())));
                totalProductsSold += t.getQuantity();
            }
        }

        BigDecimal gananciaTotal = totalSalidas.subtract(totalCostSold);
        BigDecimal margenPromedio = totalSalidas.compareTo(BigDecimal.ZERO) > 0 
            ? gananciaTotal.divide(totalSalidas, 4, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Sección de Indicadores Clave
        Row indicatorsHeaderRow = sheet.createRow(rowNum++);
        indicatorsHeaderRow.setHeightInPoints(18);
        Cell indicatorsHeaderCell = indicatorsHeaderRow.createCell(0);
        indicatorsHeaderCell.setCellValue("INDICADORES CLAVE");
        indicatorsHeaderCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 2));

        rowNum++; // Espacio

        // Encabezados de tabla
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(18);
        String[] headers = {"Indicador", "Valor", "Porcentaje"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Filas de datos
        Row row = sheet.createRow(rowNum++);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue("Ingresos Totales");
        labelCell.setCellStyle(labelStyle);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(totalSalidas.doubleValue());
        valueCell.setCellStyle(currencyStyle);
        Cell percentCell = row.createCell(2);
        percentCell.setCellValue(1.0);
        percentCell.setCellStyle(percentStyle);

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Costo de Venta");
        labelCell.setCellStyle(labelStyle);
        valueCell = row.createCell(1);
        valueCell.setCellValue(totalCostSold.doubleValue());
        valueCell.setCellStyle(currencyStyle);
        percentCell = row.createCell(2);
        if (totalSalidas.compareTo(BigDecimal.ZERO) > 0) {
            percentCell.setCellValue(totalCostSold.divide(totalSalidas, 4, java.math.RoundingMode.HALF_UP).doubleValue());
        } else {
            percentCell.setCellValue(0.0);
        }
        percentCell.setCellStyle(percentStyle);

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Ganancia Bruta");
        labelCell.setCellStyle(labelStyle);
        valueCell = row.createCell(1);
        valueCell.setCellValue(gananciaTotal.doubleValue());
        valueCell.setCellStyle(currencyStyle);
        percentCell = row.createCell(2);
        percentCell.setCellValue(margenPromedio.doubleValue());
        percentCell.setCellStyle(percentStyle);

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Margen de Ganancia");
        labelCell.setCellStyle(labelStyle);
        valueCell = row.createCell(1);
        valueCell.setCellValue(margenPromedio.doubleValue());
        valueCell.setCellStyle(percentStyle);
        percentCell = row.createCell(2);
        percentCell.setCellValue(margenPromedio.doubleValue());
        percentCell.setCellStyle(percentStyle);

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Unidades Vendidas");
        labelCell.setCellStyle(labelStyle);
        valueCell = row.createCell(1);
        valueCell.setCellValue(totalProductsSold);
        percentCell = row.createCell(2);
        percentCell.setCellValue("-");

        row = sheet.createRow(rowNum++);
        labelCell = row.createCell(0);
        labelCell.setCellValue("Unidades Compradas");
        labelCell.setCellStyle(labelStyle);
        valueCell = row.createCell(1);
        valueCell.setCellValue(totalProductsBought);
        percentCell = row.createCell(2);
        percentCell.setCellValue("-");

        // Auto-ajustar columnas
        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }

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

        // Recopilar datos de productos
        Map<String, Integer> productQuantity = new TreeMap<>();
        Map<String, BigDecimal> productRevenue = new HashMap<>();
        Map<String, BigDecimal> productCost = new HashMap<>();
        Map<String, BigDecimal> productGain = new HashMap<>();

        for (Transaction t : transactions) {
            if ("SALIDA".equalsIgnoreCase(t.getType())) {
                try {
                    Product product = t.getProduct();
                    if (product != null) {
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
                if (p.getStore() != null && p.getStore().getId().equals(storeId)) {
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

    private void createRecommendationsSheet(Workbook workbook, List<Transaction> transactions, Long storeId) {
        Sheet sheet = workbook.createSheet("Recomendaciones");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle percentStyle = createPercentageStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 35);
        sheet.setColumnWidth(1, 25);

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS Y RECOMENDACIONES");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 1));

        rowNum++; // Espacio

        // SECCIÓN 1: Productos Más Rentables
        Row section1Header = sheet.createRow(rowNum++);
        section1Header.setHeightInPoints(18);
        Cell section1Cell = section1Header.createCell(0);
        section1Cell.setCellValue("TOP PRODUCTOS MÁS RENTABLES");
        section1Cell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 1));

        Map<String, BigDecimal> productMargin = new HashMap<>();
        Map<String, Integer> productQuantity = new HashMap<>();
        Map<String, BigDecimal> productGain = new HashMap<>();

        for (Transaction t : transactions) {
            if ("SALIDA".equalsIgnoreCase(t.getType())) {
                try {
                    Product product = t.getProduct();
                    if (product != null) {
                        String productKey = product.getName();
                        BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                        BigDecimal cost = product.getCost() != null ? product.getCost() : BigDecimal.ZERO;
                        
                        if (price.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal margin = price.subtract(cost).divide(price, 4, java.math.RoundingMode.HALF_UP);
                            productMargin.put(productKey, margin);
                        }
                        
                        productQuantity.put(productKey, productQuantity.getOrDefault(productKey, 0) + t.getQuantity());
                        BigDecimal gain = price.subtract(cost).multiply(new BigDecimal(t.getQuantity()));
                        productGain.put(productKey, productGain.getOrDefault(productKey, BigDecimal.ZERO).add(gain));
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }

        List<Map.Entry<String, BigDecimal>> topProfitable = productGain.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(5)
            .collect(Collectors.toList());

        Row headerRow1 = sheet.createRow(rowNum++);
        headerRow1.setHeightInPoints(16);
        Cell hCell1 = headerRow1.createCell(0);
        hCell1.setCellValue("Producto");
        hCell1.setCellStyle(headerStyle);
        Cell hCell2 = headerRow1.createCell(1);
        hCell2.setCellValue("Ganancia Total");
        hCell2.setCellStyle(headerStyle);

        for (Map.Entry<String, BigDecimal> entry : topProfitable) {
            Row row = sheet.createRow(rowNum++);
            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(entry.getKey());
            nameCell.setCellStyle(dataCellStyle);
            Cell gainCell = row.createCell(1);
            gainCell.setCellValue(entry.getValue().doubleValue());
            gainCell.setCellStyle(currencyStyle);
        }

        rowNum += 2; // Espacio

        // SECCIÓN 2: Productos con Bajo Desempeño
        Row section2Header = sheet.createRow(rowNum++);
        section2Header.setHeightInPoints(18);
        Cell section2Cell = section2Header.createCell(0);
        section2Cell.setCellValue("PRODUCTOS CON BAJO MOVIMIENTO");
        section2Cell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 1));

        try {
            List<Product> allProducts = productService.findAll();
            List<Map.Entry<String, Integer>> lowMovement = productQuantity.entrySet().stream()
                .filter(e -> e.getValue() < 5)
                .sorted((a, b) -> Integer.compare(a.getValue(), b.getValue()))
                .limit(5)
                .collect(Collectors.toList());

            if (lowMovement.isEmpty()) {
                Row noDataRow = sheet.createRow(rowNum++);
                Cell noDataCell = noDataRow.createCell(0);
                noDataCell.setCellValue("Todos los productos tienen buen movimiento");
                noDataCell.setCellStyle(dataCellStyle);
            } else {
                Row headerRow2 = sheet.createRow(rowNum++);
                headerRow2.setHeightInPoints(16);
                Cell hCell3 = headerRow2.createCell(0);
                hCell3.setCellValue("Producto");
                hCell3.setCellStyle(headerStyle);
                Cell hCell4 = headerRow2.createCell(1);
                hCell4.setCellValue("Cantidad Vendida");
                hCell4.setCellStyle(headerStyle);

                for (Map.Entry<String, Integer> entry : lowMovement) {
                    Row row = sheet.createRow(rowNum++);
                    Cell nameCell = row.createCell(0);
                    nameCell.setCellValue(entry.getKey());
                    nameCell.setCellStyle(dataCellStyle);
                    Cell qtyCell = row.createCell(1);
                    qtyCell.setCellValue(entry.getValue());
                    qtyCell.setCellStyle(createNumberStyle(workbook));
                }
            }
        } catch (Exception e) {
            Row errorRow = sheet.createRow(rowNum);
            Cell errorCell = errorRow.createCell(0);
            errorCell.setCellValue("Error al calcular movimiento: " + e.getMessage());
        }

        rowNum += 2; // Espacio

        // SECCIÓN 3: Recomendaciones Generales
        Row section3Header = sheet.createRow(rowNum++);
        section3Header.setHeightInPoints(18);
        Cell section3Cell = section3Header.createCell(0);
        section3Cell.setCellValue("RECOMENDACIONES");
        section3Cell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 1));

        Row rec1 = sheet.createRow(rowNum++);
        Cell recCell1 = rec1.createCell(0);
        recCell1.setCellValue("1. Enfocarse en productos de alto margen de ganancia");
        recCell1.setCellStyle(dataCellStyle);

        Row rec2 = sheet.createRow(rowNum++);
        Cell recCell2 = rec2.createCell(0);
        recCell2.setCellValue("2. Revisar precios de productos con bajo movimiento");
        recCell2.setCellStyle(dataCellStyle);

        Row rec3 = sheet.createRow(rowNum++);
        Cell recCell3 = rec3.createCell(0);
        recCell3.setCellValue("3. Considerar promociones en productos lentos");
        recCell3.setCellStyle(dataCellStyle);

        Row rec4 = sheet.createRow(rowNum++);
        Cell recCell4 = rec4.createCell(0);
        recCell4.setCellValue("4. Aumentar stock de productos más vendidos");
        recCell4.setCellStyle(dataCellStyle);

        Row rec5 = sheet.createRow(rowNum++);
        Cell recCell5 = rec5.createCell(0);
        recCell5.setCellValue("5. Analizar tendencias de venta mensualmente");
        recCell5.setCellStyle(dataCellStyle);

        Row rec6 = sheet.createRow(rowNum++);
        Cell recCell6 = rec6.createCell(0);
        recCell6.setCellValue("6. Mantener control de costos administrativos");
        recCell6.setCellStyle(dataCellStyle);

        // Auto-ajustar columnas
        for (int i = 0; i < 2; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createStockRotationSheet(Workbook workbook, List<Transaction> transactions, Long storeId) {
        Sheet sheet = workbook.createSheet("Rotación de Stock");
        
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle dataCellStyle = createDataCellStyle(workbook);

        int rowNum = 0;

        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 22);
        sheet.setColumnWidth(1, 16);
        sheet.setColumnWidth(2, 16);
        sheet.setColumnWidth(3, 16);
        sheet.setColumnWidth(4, 16);
        sheet.setColumnWidth(5, 16);
        sheet.setColumnWidth(6, 18);

        // TÍTULO
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS DE ROTACIÓN Y REPOSICIÓN");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

        rowNum++; // Espacio

        // Recopilar datos
        Map<String, Integer> productQuantitySold = new HashMap<>();
        Map<String, Integer> productCurrentStock = new HashMap<>();
        Map<String, BigDecimal> productCost = new HashMap<>();
        Map<String, BigDecimal> productPrice = new HashMap<>();

        for (Transaction t : transactions) {
            Product product = t.getProduct();
            if (product == null) continue;

            if ("SALIDA".equalsIgnoreCase(t.getType())) {
                String productKey = product.getName();
                productQuantitySold.put(productKey, productQuantitySold.getOrDefault(productKey, 0) + t.getQuantity());
            }
        }

        // Obtener todos los productos con stock actual
        try {
            List<Product> allProducts = productService.findAll();
            for (Product p : allProducts) {
                if (p.getStore() != null && p.getStore().getId().equals(storeId)) {
                    productCurrentStock.put(p.getName(), p.getStock() != null ? p.getStock() : 0);
                    productCost.put(p.getName(), p.getCost() != null ? p.getCost() : BigDecimal.ZERO);
                    productPrice.put(p.getName(), p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO);
                }
            }
        } catch (Exception e) {
            // Skip
        }

        // SECCIÓN 1: Productos con Baja Rotación (necesitan reposición)
        Row section1Header = sheet.createRow(rowNum++);
        section1Header.setHeightInPoints(18);
        Cell section1Cell = section1Header.createCell(0);
        section1Cell.setCellValue("PRODUCTOS QUE NECESITAN REPOSICIÓN");
        section1Cell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

        Row headerRow1 = sheet.createRow(rowNum++);
        headerRow1.setHeightInPoints(16);
        String[] headers1 = {"Producto", "Stock Actual", "Vendido", "Costo Unit", "Reposición Recomendada", "Costo Total", "Prioridad"};
        for (int i = 0; i < headers1.length; i++) {
            Cell cell = headerRow1.createCell(i);
            cell.setCellValue(headers1[i]);
            cell.setCellStyle(headerStyle);
        }

        // Productos con bajo stock o bajo movimiento
        Map<String, Integer> suggestedReplenishment = new HashMap<>();
        for (String productName : productCurrentStock.keySet()) {
            Integer currentStock = productCurrentStock.get(productName);
            Integer quantitySold = productQuantitySold.getOrDefault(productName, 0);
            
            // Si tiene poco stock O muy poca venta, sugerir reposición
            if (currentStock != null && (currentStock < 5 || quantitySold == 0)) {
                // Sugerir al menos 10 unidades o el doble de lo que se vendió
                int suggested = Math.max(10, quantitySold * 2);
                suggestedReplenishment.put(productName, suggested);
            }
        }

        // Ordenar por prioridad (menor stock primero)
        List<Map.Entry<String, Integer>> sortedReplenishment = suggestedReplenishment.entrySet().stream()
            .sorted((a, b) -> {
                Integer stockA = productCurrentStock.getOrDefault(a.getKey(), 0);
                Integer stockB = productCurrentStock.getOrDefault(b.getKey(), 0);
                return Integer.compare(stockA, stockB);
            })
            .collect(Collectors.toList());

        int priority = 1;
        for (Map.Entry<String, Integer> entry : sortedReplenishment) {
            String productName = entry.getKey();
            Integer suggested = entry.getValue();
            Integer currentStock = productCurrentStock.getOrDefault(productName, 0);
            Integer sold = productQuantitySold.getOrDefault(productName, 0);
            BigDecimal cost = productCost.getOrDefault(productName, BigDecimal.ZERO);

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(14);

            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(productName);
            nameCell.setCellStyle(dataCellStyle);

            Cell stockCell = row.createCell(1);
            stockCell.setCellValue(currentStock);
            stockCell.setCellStyle(numberStyle);

            Cell soldCell = row.createCell(2);
            soldCell.setCellValue(sold);
            soldCell.setCellStyle(numberStyle);

            Cell costCell = row.createCell(3);
            costCell.setCellValue(cost.doubleValue());
            costCell.setCellStyle(currencyStyle);

            Cell replenishCell = row.createCell(4);
            replenishCell.setCellValue(suggested);
            replenishCell.setCellStyle(numberStyle);

            Cell totalCostCell = row.createCell(5);
            totalCostCell.setCellValue(cost.multiply(new BigDecimal(suggested)).doubleValue());
            totalCostCell.setCellStyle(currencyStyle);

            Cell priorityCell = row.createCell(6);
            if (currentStock < 3) {
                priorityCell.setCellValue("URGENTE");
            } else if (currentStock < 5) {
                priorityCell.setCellValue("ALTA");
            } else {
                priorityCell.setCellValue("MEDIA");
            }
            priorityCell.setCellStyle(dataCellStyle);

            priority++;
        }

        if (sortedReplenishment.isEmpty()) {
            Row noDataRow = sheet.createRow(rowNum++);
            Cell noDataCell = noDataRow.createCell(0);
            noDataCell.setCellValue("Todos los productos tienen stock adecuado");
            noDataCell.setCellStyle(dataCellStyle);
        }

        rowNum += 2; // Espacio

        // SECCIÓN 2: Análisis de Rotación
        Row section2Header = sheet.createRow(rowNum++);
        section2Header.setHeightInPoints(18);
        Cell section2Cell = section2Header.createCell(0);
        section2Cell.setCellValue("ANÁLISIS DE ROTACIÓN");
        section2Cell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

        Row headerRow2 = sheet.createRow(rowNum++);
        headerRow2.setHeightInPoints(16);
        String[] headers2 = {"Producto", "Stock Actual", "Rotación", "Velocidad", "Reorden Sugerida", "Costo", "Estado"};
        for (int i = 0; i < headers2.length; i++) {
            Cell cell = headerRow2.createCell(i);
            cell.setCellValue(headers2[i]);
            cell.setCellStyle(headerStyle);
        }

        // Mostrar todos los productos con su rotación
        List<Map.Entry<String, Integer>> allProducts = productCurrentStock.entrySet().stream()
            .sorted((a, b) -> Integer.compare(productQuantitySold.getOrDefault(b.getKey(), 0), productQuantitySold.getOrDefault(a.getKey(), 0)))
            .collect(Collectors.toList());

        for (Map.Entry<String, Integer> entry : allProducts) {
            String productName = entry.getKey();
            Integer currentStock = entry.getValue();
            Integer sold = productQuantitySold.getOrDefault(productName, 0);
            BigDecimal cost = productCost.getOrDefault(productName, BigDecimal.ZERO);

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(14);

            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(productName);
            nameCell.setCellStyle(dataCellStyle);

            Cell stockCell = row.createCell(1);
            stockCell.setCellValue(currentStock);
            stockCell.setCellStyle(numberStyle);

            Cell rotationCell = row.createCell(2);
            rotationCell.setCellValue(sold);
            rotationCell.setCellStyle(numberStyle);

            Cell velocityCell = row.createCell(3);
            if (currentStock > 0 && sold > 0) {
                velocityCell.setCellValue((double) sold / currentStock);
            } else {
                velocityCell.setCellValue(0.0);
            }

            Cell reorderCell = row.createCell(4);
            int reorderPoint = Math.max(5, sold / 2);
            reorderCell.setCellValue(reorderPoint);
            reorderCell.setCellStyle(numberStyle);

            Cell costCell = row.createCell(5);
            costCell.setCellValue(cost.doubleValue());
            costCell.setCellStyle(currencyStyle);

            Cell statusCell = row.createCell(6);
            String status;
            if (sold == 0) {
                status = "SIN VENTA";
            } else if (sold < 3) {
                status = "LENTO";
            } else if (sold < 10) {
                status = "NORMAL";
            } else {
                status = "RÁPIDO";
            }
            statusCell.setCellValue(status);
            statusCell.setCellStyle(dataCellStyle);
        }

        rowNum += 2; // Espacio

        // SECCIÓN 3: Resumen de Inversión
        Row section3Header = sheet.createRow(rowNum++);
        section3Header.setHeightInPoints(18);
        Cell section3Cell = section3Header.createCell(0);
        section3Cell.setCellValue("INVERSIÓN EN STOCK");
        section3Cell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

        Row headerRow3 = sheet.createRow(rowNum++);
        headerRow3.setHeightInPoints(16);
        String[] headers3 = {"Concepto", "Cantidad", "", "", "", "", ""};
        for (int i = 0; i < headers3.length; i++) {
            Cell cell = headerRow3.createCell(i);
            cell.setCellValue(headers3[i]);
            cell.setCellStyle(headerStyle);
        }

        BigDecimal totalCurrentInvestment = BigDecimal.ZERO;
        BigDecimal totalSuggestedInvestment = BigDecimal.ZERO;

        for (String productName : productCurrentStock.keySet()) {
            Integer currentStock = productCurrentStock.get(productName);
            BigDecimal cost = productCost.getOrDefault(productName, BigDecimal.ZERO);
            totalCurrentInvestment = totalCurrentInvestment.add(cost.multiply(new BigDecimal(currentStock)));

            if (suggestedReplenishment.containsKey(productName)) {
                Integer suggested = suggestedReplenishment.get(productName);
                totalSuggestedInvestment = totalSuggestedInvestment.add(cost.multiply(new BigDecimal(suggested)));
            }
        }

        Row row1 = sheet.createRow(rowNum++);
        Cell labelCell1 = row1.createCell(0);
        labelCell1.setCellValue("Inversión Actual en Stock");
        labelCell1.setCellStyle(dataCellStyle);
        Cell valueCell1 = row1.createCell(1);
        valueCell1.setCellValue(totalCurrentInvestment.doubleValue());
        valueCell1.setCellStyle(currencyStyle);

        Row row2 = sheet.createRow(rowNum++);
        Cell labelCell2 = row2.createCell(0);
        labelCell2.setCellValue("Inversión en Reposición Sugerida");
        labelCell2.setCellStyle(dataCellStyle);
        Cell valueCell2 = row2.createCell(1);
        valueCell2.setCellValue(totalSuggestedInvestment.doubleValue());
        valueCell2.setCellStyle(currencyStyle);

        Row row3 = sheet.createRow(rowNum++);
        Cell labelCell3 = row3.createCell(0);
        labelCell3.setCellValue("Inversión Total Recomendada");
        labelCell3.setCellStyle(dataCellStyle);
        Cell valueCell3 = row3.createCell(1);
        BigDecimal totalRecommended = totalCurrentInvestment.add(totalSuggestedInvestment);
        valueCell3.setCellValue(totalRecommended.doubleValue());
        valueCell3.setCellStyle(currencyStyle);

        // Auto-ajustar columnas
        for (int i = 0; i < 7; i++) {
            sheet.autoSizeColumn(i);
        }
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

    private CellStyle createAlternateRowStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createTransactionStyle(Workbook workbook, String type) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private CellStyle createSectionHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
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

    private CellStyle createDataRightCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    private Font createBoldFont(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        return font;
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

package com.inventario.licoreria.modules.export.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.inventario.licoreria.modules.administrative_costs.service.AdministrativeCostMovementService;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.service.TransactionService;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.users.service.UserService;

@ExtendWith(MockitoExtension.class)
class SalesReportServiceTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private ProductService productService;

    @Mock
    private UserService userService;

    @Mock
    private AdministrativeCostMovementService administrativeCostMovementService;

    @InjectMocks
    private SalesReportService salesReportService;

    @Test
    void generateSalesReport_detailedMovementsIgnoresGainsForEntriesAndAdjustments() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product product = new Product();
        product.setId(10L);
        product.setName("Producto test");
        product.setPrice(new BigDecimal("100"));
        product.setCost(new BigDecimal("60"));
        product.setIsActive(true);
        product.setStore(store);

        Transaction saleTransaction = new Transaction();
        saleTransaction.setId(1L);
        saleTransaction.setProduct(product);
        saleTransaction.setProductId(10L);
        saleTransaction.setType("SALIDA");
        saleTransaction.setReason("VENTA");
        saleTransaction.setQuantity(2);
        saleTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 10, 0));

        Transaction entryTransaction = new Transaction();
        entryTransaction.setId(2L);
        entryTransaction.setProduct(product);
        entryTransaction.setProductId(10L);
        entryTransaction.setType("ENTRADA");
        entryTransaction.setReason("COMPRA");
        entryTransaction.setQuantity(3);
        entryTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 11, 0));

        Transaction adjustmentTransaction = new Transaction();
        adjustmentTransaction.setId(3L);
        adjustmentTransaction.setProduct(product);
        adjustmentTransaction.setProductId(10L);
        adjustmentTransaction.setType("SALIDA");
        adjustmentTransaction.setReason("AJUSTE");
        adjustmentTransaction.setQuantity(1);
        adjustmentTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 12, 0));

        when(transactionService.findAll()).thenReturn(List.of(saleTransaction, entryTransaction, adjustmentTransaction));
        when(productService.findById(10L)).thenReturn(product);

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "SUMMARY");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet detailedSheet = workbook.getSheet("Movimientos Detallados");
            assertNotNull(detailedSheet);

            int saleGainUnitFound = 0;
            int entryGainUnitZero = 0;
            int adjustmentGainUnitZero = 0;

            for (int rowIndex = 0; rowIndex <= detailedSheet.getLastRowNum(); rowIndex++) {
                Row row = detailedSheet.getRow(rowIndex);
                if (row == null) continue;

                Cell typeCell = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                Cell reasonCell = row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                Cell gainUnitCell = row.getCell(10, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                if (typeCell != null && gainUnitCell != null) {
                    String type = getCellText(typeCell);
                    String reason = getCellText(reasonCell);
                    double gainUnit;
                    try {
                        gainUnit = gainUnitCell.getNumericCellValue();
                    } catch (Exception e) {
                        continue;
                    }

                    if ("SALIDA".equals(type) && "VENTA".equals(reason)) {
                        if (gainUnit == 40.0) saleGainUnitFound++;
                    } else if ("ENTRADA".equals(type)) {
                        if (gainUnit == 0.0) entryGainUnitZero++;
                    } else if ("SALIDA".equals(type) && "AJUSTE".equals(reason)) {
                        if (gainUnit == 0.0) adjustmentGainUnitZero++;
                    }
                }
            }

            assertEquals(1, saleGainUnitFound);
            assertEquals(1, entryGainUnitZero);
            assertEquals(1, adjustmentGainUnitZero);
        }
    }

    @Test
    void generateSalesReport_dailyCashFlowSheetShowsSalesCostGrossProfitAndEntriesClearly() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product product = new Product();
        product.setId(10L);
        product.setName("Producto prueba");
        product.setPrice(new BigDecimal("100"));
        product.setCost(new BigDecimal("60"));
        product.setIsActive(true);
        product.setStock(0);
        product.setStore(store);

        Transaction saleTransaction = new Transaction();
        saleTransaction.setId(1L);
        saleTransaction.setProduct(product);
        saleTransaction.setProductId(10L);
        saleTransaction.setType("SALIDA");
        saleTransaction.setQuantity(2);
        saleTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 10, 0));

        Transaction entryTransaction = new Transaction();
        entryTransaction.setId(2L);
        entryTransaction.setProduct(product);
        entryTransaction.setProductId(10L);
        entryTransaction.setType("ENTRADA");
        entryTransaction.setQuantity(1);
        entryTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 11, 0));

        when(transactionService.findAll()).thenReturn(List.of(saleTransaction, entryTransaction));
        when(productService.findById(10L)).thenReturn(product);

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "COMPLETE");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet cashFlowSheet = workbook.getSheet("Flujo Caja Diario");
            assertNotNull(cashFlowSheet);

            Row headerRow = cashFlowSheet.getRow(2);
            assertNotNull(headerRow);
            assertEquals("Día", headerRow.getCell(0).getStringCellValue());
            assertEquals("Cant. Salidas", headerRow.getCell(1).getStringCellValue());
            assertEquals("Cant. Entradas", headerRow.getCell(2).getStringCellValue());
            assertEquals("Ventas", headerRow.getCell(3).getStringCellValue());
            assertEquals("Gasto Entradas", headerRow.getCell(4).getStringCellValue());
            assertEquals("Ganancia Neta", headerRow.getCell(5).getStringCellValue());

            Row dataRow = cashFlowSheet.getRow(3);
            assertNotNull(dataRow);
            assertEquals(LocalDate.of(2024, 1, 15), dataRow.getCell(0).getLocalDateTimeCellValue().toLocalDate());
            assertEquals(1.0, dataRow.getCell(1).getNumericCellValue());
            assertEquals(1.0, dataRow.getCell(2).getNumericCellValue());
            assertEquals(200.0, dataRow.getCell(3).getNumericCellValue());
            assertEquals(60.0, dataRow.getCell(4).getNumericCellValue());
            assertEquals(80.0, dataRow.getCell(5).getNumericCellValue());
        }
    }

    @Test
    void generateSalesReport_execSummaryExcludesInactiveOrWrongStoreProducts() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Store otherStore = new Store();
        otherStore.setId(2L);

        Product activeProduct = new Product();
        activeProduct.setId(10L);
        activeProduct.setName("Activo");
        activeProduct.setPrice(new BigDecimal("100"));
        activeProduct.setCost(new BigDecimal("60"));
        activeProduct.setIsActive(true);
        activeProduct.setStore(store);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(11L);
        inactiveProduct.setName("Inactivo");
        inactiveProduct.setPrice(new BigDecimal("200"));
        inactiveProduct.setCost(new BigDecimal("120"));
        inactiveProduct.setIsActive(false);
        inactiveProduct.setStore(store);

        Product otherStoreProduct = new Product();
        otherStoreProduct.setId(12L);
        otherStoreProduct.setName("Otra Tienda");
        otherStoreProduct.setPrice(new BigDecimal("300"));
        otherStoreProduct.setCost(new BigDecimal("150"));
        otherStoreProduct.setIsActive(true);
        otherStoreProduct.setStore(otherStore);

        Transaction activeTransaction = new Transaction();
        activeTransaction.setId(1L);
        activeTransaction.setProduct(activeProduct);
        activeTransaction.setProductId(10L);
        activeTransaction.setType("SALIDA");
        activeTransaction.setReason("VENTA");
        activeTransaction.setQuantity(2);
        activeTransaction.setDateTime(LocalDateTime.now());

        Transaction inactiveTransaction = new Transaction();
        inactiveTransaction.setId(2L);
        inactiveTransaction.setProduct(inactiveProduct);
        inactiveTransaction.setProductId(11L);
        inactiveTransaction.setType("SALIDA");
        inactiveTransaction.setReason("VENTA");
        inactiveTransaction.setQuantity(1);
        inactiveTransaction.setDateTime(LocalDateTime.now());

        Transaction otherStoreTransaction = new Transaction();
        otherStoreTransaction.setId(3L);
        otherStoreTransaction.setProduct(otherStoreProduct);
        otherStoreTransaction.setProductId(12L);
        otherStoreTransaction.setType("SALIDA");
        otherStoreTransaction.setReason("VENTA");
        otherStoreTransaction.setQuantity(1);
        otherStoreTransaction.setDateTime(LocalDateTime.now());

        when(transactionService.findAll()).thenReturn(List.of(activeTransaction, inactiveTransaction, otherStoreTransaction));
        when(productService.findById(10L)).thenReturn(activeProduct);

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "SUMMARY");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet summarySheet = workbook.getSheet("Resumen Ejecutivo");
            assertNotNull(summarySheet);

            List<String> values = IntStream.range(0, summarySheet.getLastRowNum() + 1)
                .mapToObj(summarySheet::getRow)
                .filter(row -> row != null)
                .flatMap(row -> IntStream.range(0, row.getLastCellNum())
                    .mapToObj(cellIndex -> {
                        var cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        return cell == null ? "" : cell.toString();
                    }))
                .toList();

            assertTrue(values.stream().anyMatch(value -> value.contains("Activo")));
            assertFalse(values.stream().anyMatch(value -> value.contains("Inactivo")));
            assertFalse(values.stream().anyMatch(value -> value.contains("Otra Tienda")));
        }
    }

    @Test
    void generateSalesReport_execSummaryIgnoresAdjustmentTransactions() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product product = new Product();
        product.setId(10L);
        product.setName("Producto ajuste");
        product.setPrice(new BigDecimal("100"));
        product.setCost(new BigDecimal("60"));
        product.setIsActive(true);
        product.setStore(store);

        Transaction saleTransaction = new Transaction();
        saleTransaction.setId(1L);
        saleTransaction.setProduct(product);
        saleTransaction.setProductId(10L);
        saleTransaction.setType("SALIDA");
        saleTransaction.setReason("VENTA");
        saleTransaction.setQuantity(2);
        saleTransaction.setDateTime(LocalDateTime.now());

        Transaction adjustmentOutTransaction = new Transaction();
        adjustmentOutTransaction.setId(2L);
        adjustmentOutTransaction.setProduct(product);
        adjustmentOutTransaction.setProductId(10L);
        adjustmentOutTransaction.setType("SALIDA");
        adjustmentOutTransaction.setReason("AJUSTE");
        adjustmentOutTransaction.setQuantity(1);
        adjustmentOutTransaction.setDateTime(LocalDateTime.now());

        Transaction adjustmentInTransaction = new Transaction();
        adjustmentInTransaction.setId(3L);
        adjustmentInTransaction.setProduct(product);
        adjustmentInTransaction.setProductId(10L);
        adjustmentInTransaction.setType("ENTRADA");
        adjustmentInTransaction.setReason("AJUSTE");
        adjustmentInTransaction.setQuantity(1);
        adjustmentInTransaction.setDateTime(LocalDateTime.now());

        when(transactionService.findAll()).thenReturn(List.of(saleTransaction, adjustmentOutTransaction, adjustmentInTransaction));
        when(productService.findById(10L)).thenReturn(product);

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "SUMMARY");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet summarySheet = workbook.getSheet("Resumen Ejecutivo");
            assertNotNull(summarySheet);

            assertEquals(200.0, getMetricValue(summarySheet, "Total Ingresos:"), 0.001);
            assertEquals(120.0, getMetricValue(summarySheet, "Costo de Venta:"), 0.001);
            assertEquals(80.0, getMetricValue(summarySheet, "Ganancia Bruta:"), 0.001);
        }
    }

    @Test
    void generateSalesReport_execSummaryShowsMetricDefinitionsBox() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product product = new Product();
        product.setId(10L);
        product.setName("Producto prueba");
        product.setPrice(new BigDecimal("100"));
        product.setCost(new BigDecimal("60"));
        product.setIsActive(true);
        product.setStore(store);

        Transaction saleTransaction = new Transaction();
        saleTransaction.setId(1L);
        saleTransaction.setProduct(product);
        saleTransaction.setProductId(10L);
        saleTransaction.setType("SALIDA");
        saleTransaction.setQuantity(2);
        saleTransaction.setDateTime(LocalDateTime.now());

        Transaction entryTransaction = new Transaction();
        entryTransaction.setId(2L);
        entryTransaction.setProduct(product);
        entryTransaction.setProductId(10L);
        entryTransaction.setType("ENTRADA");
        entryTransaction.setQuantity(1);
        entryTransaction.setDateTime(LocalDateTime.now());

        when(transactionService.findAll()).thenReturn(List.of(saleTransaction, entryTransaction));
        when(productService.findById(10L)).thenReturn(product);

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "SUMMARY");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet summarySheet = workbook.getSheet("Resumen Ejecutivo");
            assertNotNull(summarySheet);

            List<String> values = IntStream.range(0, summarySheet.getLastRowNum() + 1)
                .mapToObj(summarySheet::getRow)
                .filter(row -> row != null)
                .flatMap(row -> IntStream.range(0, row.getLastCellNum())
                    .mapToObj(cellIndex -> {
                        var cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        return cell == null ? "" : cell.toString();
                    }))
                .toList();

            boolean hasDefinitionsInSeparateColumn = false;
            for (int rowIndex = 0; rowIndex <= summarySheet.getLastRowNum(); rowIndex++) {
                Row row = summarySheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Cell cell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null && "¿Qué significa cada indicador?".equals(getCellText(cell))) {
                    hasDefinitionsInSeparateColumn = true;
                    break;
                }
            }

            assertTrue(hasDefinitionsInSeparateColumn);
            assertTrue(values.stream().anyMatch(value -> value.contains("Total Invertido")));
            assertTrue(values.stream().anyMatch(value -> value.contains("Total Ingreso")));
            assertTrue(values.stream().anyMatch(value -> value.contains("Costo de Venta")));
            assertTrue(values.stream().anyMatch(value -> value.contains("Ganancia Bruta")));
            assertTrue(values.stream().anyMatch(value -> value.contains("Margen de Ganancia")));
        }
    }

    private double getMetricValue(Sheet sheet, String label) {
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Cell labelCell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (labelCell != null && label.equals(getCellText(labelCell))) {
                Cell valueCell = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                return valueCell != null ? valueCell.getNumericCellValue() : 0.0;
            }
        }
        return 0.0;
    }

    private String getCellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                var cachedType = cell.getCachedFormulaResultType();
                yield switch (cachedType) {
                    case STRING -> cell.getStringCellValue();
                    case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                    case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                    default -> cell.toString();
                };
            }
            default -> cell.toString();
        };
    }

    @Test
    void generateSalesReport_excludesInactiveProductsFromExcel() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product activeProduct = new Product();
        activeProduct.setId(10L);
        activeProduct.setName("Activo");
        activeProduct.setPrice(new BigDecimal("100"));
        activeProduct.setCost(new BigDecimal("60"));
        activeProduct.setIsActive(true);
        activeProduct.setStore(store);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(11L);
        inactiveProduct.setName("Inactivo");
        inactiveProduct.setPrice(new BigDecimal("200"));
        inactiveProduct.setCost(new BigDecimal("120"));
        inactiveProduct.setIsActive(false);
        inactiveProduct.setStore(store);

        Transaction activeTransaction = new Transaction();
        activeTransaction.setId(1L);
        activeTransaction.setProduct(activeProduct);
        activeTransaction.setProductId(10L);
        activeTransaction.setType("SALIDA");
        activeTransaction.setQuantity(2);
        activeTransaction.setDateTime(LocalDateTime.now());

        Transaction inactiveTransaction = new Transaction();
        inactiveTransaction.setId(2L);
        inactiveTransaction.setProduct(inactiveProduct);
        inactiveTransaction.setProductId(11L);
        inactiveTransaction.setType("SALIDA");
        inactiveTransaction.setQuantity(1);
        inactiveTransaction.setDateTime(LocalDateTime.now());

        when(transactionService.findAll()).thenReturn(List.of(activeTransaction, inactiveTransaction));
        when(productService.findById(10L)).thenReturn(activeProduct);

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "SUMMARY");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet detailedSheet = workbook.getSheet("Movimientos Detallados");
            assertNotNull(detailedSheet);

            List<String> values = IntStream.range(0, detailedSheet.getLastRowNum() + 1)
                .mapToObj(detailedSheet::getRow)
                .filter(row -> row != null)
                .flatMap(row -> IntStream.range(0, row.getLastCellNum())
                    .mapToObj(cellIndex -> {
                        var cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        return cell == null ? "" : cell.toString();
                    }))
                .toList();

            assertTrue(values.stream().anyMatch(value -> value.contains("Activo")));
            assertFalse(values.stream().anyMatch(value -> value.contains("Inactivo")));
        }
    }

    @Test
    void generateSalesReport_allPeriodIgnoresFormDatesAndIncludesCompleteStoreHistory() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product product = new Product();
        product.setId(10L);
        product.setName("Producto histórico");
        product.setPrice(new BigDecimal("100"));
        product.setCost(new BigDecimal("60"));
        product.setIsActive(true);
        product.setStore(store);

        Transaction oldSale = new Transaction();
        oldSale.setId(1L);
        oldSale.setProduct(product);
        oldSale.setProductId(10L);
        oldSale.setType("SALIDA");
        oldSale.setReason("VENTA");
        oldSale.setQuantity(2);
        oldSale.setDateTime(LocalDateTime.of(2024, 1, 15, 10, 0));

        Transaction recentSale = new Transaction();
        recentSale.setId(2L);
        recentSale.setProduct(product);
        recentSale.setProductId(10L);
        recentSale.setType("SALIDA");
        recentSale.setReason("VENTA");
        recentSale.setQuantity(1);
        recentSale.setDateTime(LocalDateTime.of(2026, 7, 30, 10, 0));

        when(transactionService.findAll()).thenReturn(List.of(oldSale, recentSale));

        byte[] excelBytes = salesReportService.generateSalesReport(
            1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30), "SUMMARY", "all");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet summarySheet = workbook.getSheet("Resumen Ejecutivo");
            assertNotNull(summarySheet);
            assertEquals(300.0, getMetricValue(summarySheet, "Total Ingresos:"), 0.001);
            assertEquals(180.0, getMetricValue(summarySheet, "Costo de Venta:"), 0.001);
        }
    }

    @Test
    void generateSalesReport_dailyCashFlowExcludesInactiveProductsAndAdjustments() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product activeProduct = new Product();
        activeProduct.setId(10L);
        activeProduct.setName("Producto Activo");
        activeProduct.setPrice(new BigDecimal("100"));
        activeProduct.setCost(new BigDecimal("60"));
        activeProduct.setIsActive(true);
        activeProduct.setStock(100);
        activeProduct.setStore(store);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(11L);
        inactiveProduct.setName("Producto Inactivo");
        inactiveProduct.setPrice(new BigDecimal("200"));
        inactiveProduct.setCost(new BigDecimal("120"));
        inactiveProduct.setIsActive(false);
        inactiveProduct.setStock(50);
        inactiveProduct.setStore(store);

        // SALIDA válida: debe contar
        Transaction saleTransaction = new Transaction();
        saleTransaction.setId(1L);
        saleTransaction.setProduct(activeProduct);
        saleTransaction.setProductId(10L);
        saleTransaction.setType("SALIDA");
        saleTransaction.setReason("VENTA");
        saleTransaction.setQuantity(2);
        saleTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 10, 0));

        // ENTRADA válida: debe contar solo gasto, no ganancia
        Transaction entryTransaction = new Transaction();
        entryTransaction.setId(2L);
        entryTransaction.setProduct(activeProduct);
        entryTransaction.setProductId(10L);
        entryTransaction.setType("ENTRADA");
        entryTransaction.setReason("COMPRA");
        entryTransaction.setQuantity(3);
        entryTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 11, 0));

        // AJUSTE SALIDA: debe ignorarse (no genera ganancia)
        Transaction adjustmentSaleTransaction = new Transaction();
        adjustmentSaleTransaction.setId(3L);
        adjustmentSaleTransaction.setProduct(activeProduct);
        adjustmentSaleTransaction.setProductId(10L);
        adjustmentSaleTransaction.setType("SALIDA");
        adjustmentSaleTransaction.setReason("AJUSTE");
        adjustmentSaleTransaction.setQuantity(1);
        adjustmentSaleTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 12, 0));

        // AJUSTE ENTRADA: debe ignorarse (no genera gasto)
        Transaction adjustmentEntryTransaction = new Transaction();
        adjustmentEntryTransaction.setId(4L);
        adjustmentEntryTransaction.setProduct(activeProduct);
        adjustmentEntryTransaction.setProductId(10L);
        adjustmentEntryTransaction.setType("ENTRADA");
        adjustmentEntryTransaction.setReason("AJUSTE");
        adjustmentEntryTransaction.setQuantity(1);
        adjustmentEntryTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 13, 0));

        // Transacción con producto inactivo: debe ignorarse
        Transaction inactiveProductTransaction = new Transaction();
        inactiveProductTransaction.setId(5L);
        inactiveProductTransaction.setProduct(inactiveProduct);
        inactiveProductTransaction.setProductId(11L);
        inactiveProductTransaction.setType("SALIDA");
        inactiveProductTransaction.setReason("VENTA");
        inactiveProductTransaction.setQuantity(5);
        inactiveProductTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 14, 0));

        when(transactionService.findAll()).thenReturn(List.of(
            saleTransaction, entryTransaction, adjustmentSaleTransaction, 
            adjustmentEntryTransaction, inactiveProductTransaction
        ));

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "COMPLETE");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet dailyCashFlowSheet = workbook.getSheet("Flujo Caja Diario");
            assertNotNull(dailyCashFlowSheet);

            // Buscar la fila del 2024-01-15
            Row dataRow = null;
            for (int i = 0; i <= dailyCashFlowSheet.getLastRowNum(); i++) {
                Row row = dailyCashFlowSheet.getRow(i);
                if (row != null && row.getCell(1) != null && row.getCell(1).getCellType() == CellType.NUMERIC) {
                    // Primera fila con datos numéricos en columna de Cant. Salidas
                    dataRow = row;
                    break;
                }
            }

            assertNotNull(dataRow, "No se encontró la fila de datos en Flujo Caja Diario");

            // Validaciones:
            // - Cant. Salidas = 1 (solo la SALIDA VENTA, ignora AJUSTE e inactivo)
            // - Cant. Entradas = 1 (solo la ENTRADA COMPRA, ignora AJUSTE)
            // - Ventas = 200 (2 unidades * 100 precio = 200)
            // - Gasto Entradas = 180 (3 unidades * 60 costo = 180)
            // - Ganancia Neta = 80 (200 - (2 * 60) = 200 - 120 = 80)

            double cantSalidas = dataRow.getCell(1).getNumericCellValue();
            double cantEntradas = dataRow.getCell(2).getNumericCellValue();
            double ventas = dataRow.getCell(3).getNumericCellValue();
            double gastoEntradas = dataRow.getCell(4).getNumericCellValue();
            double gananciaNeta = dataRow.getCell(5).getNumericCellValue();

            assertEquals(1.0, cantSalidas, "Debe contar solo 1 SALIDA (ignora AJUSTE e inactivo)");
            assertEquals(1.0, cantEntradas, "Debe contar solo 1 ENTRADA (ignora AJUSTE)");
            assertEquals(200.0, ventas, 0.01, "Ventas = 2 * 100");
            assertEquals(180.0, gastoEntradas, 0.01, "Gasto Entradas = 3 * 60");
            assertEquals(80.0, gananciaNeta, 0.01, "Ganancia Neta = 200 - 120");
        }
    }

    @Test
    void generateSalesReport_productAnalysisExcludesInactiveProductsAndAdjustments() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product activeProduct = new Product();
        activeProduct.setId(10L);
        activeProduct.setName("Producto Activo");
        activeProduct.setPrice(new BigDecimal("100"));
        activeProduct.setCost(new BigDecimal("60"));
        activeProduct.setIsActive(true);
        activeProduct.setStock(5);
        activeProduct.setStore(store);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(11L);
        inactiveProduct.setName("Producto Inactivo");
        inactiveProduct.setPrice(new BigDecimal("200"));
        inactiveProduct.setCost(new BigDecimal("120"));
        inactiveProduct.setIsActive(false);
        inactiveProduct.setStock(0);
        inactiveProduct.setStore(store);

        // ENTRADA válida: no genera ganancia
        Transaction entryTransaction = new Transaction();
        entryTransaction.setId(1L);
        entryTransaction.setProduct(activeProduct);
        entryTransaction.setProductId(10L);
        entryTransaction.setType("ENTRADA");
        entryTransaction.setReason("COMPRA");
        entryTransaction.setQuantity(3);
        entryTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 10, 0));

        // SALIDA válida: genera ganancia
        Transaction saleTransaction = new Transaction();
        saleTransaction.setId(2L);
        saleTransaction.setProduct(activeProduct);
        saleTransaction.setProductId(10L);
        saleTransaction.setType("SALIDA");
        saleTransaction.setReason("VENTA");
        saleTransaction.setQuantity(2);
        saleTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 11, 0));

        // AJUSTE ENTRADA: no debe contar
        Transaction adjustmentEntryTransaction = new Transaction();
        adjustmentEntryTransaction.setId(3L);
        adjustmentEntryTransaction.setProduct(activeProduct);
        adjustmentEntryTransaction.setProductId(10L);
        adjustmentEntryTransaction.setType("ENTRADA");
        adjustmentEntryTransaction.setReason("AJUSTE");
        adjustmentEntryTransaction.setQuantity(1);
        adjustmentEntryTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 12, 0));

        // AJUSTE SALIDA: no debe contar en ganancia
        Transaction adjustmentSaleTransaction = new Transaction();
        adjustmentSaleTransaction.setId(4L);
        adjustmentSaleTransaction.setProduct(activeProduct);
        adjustmentSaleTransaction.setProductId(10L);
        adjustmentSaleTransaction.setType("SALIDA");
        adjustmentSaleTransaction.setReason("AJUSTE");
        adjustmentSaleTransaction.setQuantity(1);
        adjustmentSaleTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 13, 0));

        // Transacción con producto inactivo: debe ignorarse
        Transaction inactiveProductTransaction = new Transaction();
        inactiveProductTransaction.setId(5L);
        inactiveProductTransaction.setProduct(inactiveProduct);
        inactiveProductTransaction.setProductId(11L);
        inactiveProductTransaction.setType("SALIDA");
        inactiveProductTransaction.setReason("VENTA");
        inactiveProductTransaction.setQuantity(5);
        inactiveProductTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 14, 0));

        when(transactionService.findAll()).thenReturn(List.of(
            entryTransaction, saleTransaction, adjustmentEntryTransaction, 
            adjustmentSaleTransaction, inactiveProductTransaction
        ));

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "COMPLETE");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet analysisSheet = workbook.getSheet("Análisis por Producto");
            assertNotNull(analysisSheet);

            // Buscar la fila del producto activo
            Row dataRow = null;
            for (int i = 0; i <= analysisSheet.getLastRowNum(); i++) {
                Row row = analysisSheet.getRow(i);
                if (row != null && row.getCell(0) != null) {
                    String cellValue = row.getCell(0).getStringCellValue();
                    if ("Producto Activo".equals(cellValue)) {
                        dataRow = row;
                        break;
                    }
                }
            }

            assertNotNull(dataRow, "No se encontró la fila de Producto Activo");

            // Validaciones esperadas:
            // - Cantidad Entrada = 3 (solo ENTRADA COMPRA, ignora AJUSTE)
            // - Veces Entrada = 1 (1 transacción de ENTRADA)
            // - Cantidad Salida = 2 (solo SALIDA VENTA, ignora AJUSTE)
            // - Veces Salida = 1 (1 transacción de SALIDA)
            // - Costo Invertido = 180 (3 * 60)
            // - Precio Total = 200 (2 * 100, solo SALIDA)
            // - Costo Total = 120 (2 * 60, solo SALIDA)
            // - Ingreso Total = 200 (2 * 100)
            // - Ganancia Total = 80 (200 - 120)

            double cantEntrada = dataRow.getCell(1).getNumericCellValue();
            double vecesEntrada = dataRow.getCell(2).getNumericCellValue();
            double cantSalida = dataRow.getCell(3).getNumericCellValue();
            double vecesSalida = dataRow.getCell(4).getNumericCellValue();
            double costoInvertido = dataRow.getCell(8).getNumericCellValue();
            double precioTotal = dataRow.getCell(9).getNumericCellValue();
            double costoTotal = dataRow.getCell(10).getNumericCellValue();
            double ingresoTotal = dataRow.getCell(11).getNumericCellValue();
            double gananciaTotal = dataRow.getCell(12).getNumericCellValue();

            assertEquals(3.0, cantEntrada, "Cantidad Entrada = 3 (ignora AJUSTE)");
            assertEquals(1.0, vecesEntrada, "Veces Entrada = 1 (1 transacción)");
            assertEquals(2.0, cantSalida, "Cantidad Salida = 2 (ignora AJUSTE)");
            assertEquals(1.0, vecesSalida, "Veces Salida = 1 (1 transacción)");
            assertEquals(180.0, costoInvertido, 0.01, "Costo Invertido = 3 * 60");
            assertEquals(200.0, precioTotal, 0.01, "Precio Total = 2 * 100 (solo SALIDA)");
            assertEquals(120.0, costoTotal, 0.01, "Costo Total = 2 * 60");
            assertEquals(200.0, ingresoTotal, 0.01, "Ingreso Total = 2 * 100");
            assertEquals(80.0, gananciaTotal, 0.01, "Ganancia Total = 200 - 120");

            // Validar que producto inactivo NO aparece
            boolean inactiveProductFound = false;
            for (int i = 0; i <= analysisSheet.getLastRowNum(); i++) {
                Row row = analysisSheet.getRow(i);
                if (row != null && row.getCell(0) != null) {
                    try {
                        if ("Producto Inactivo".equals(row.getCell(0).getStringCellValue())) {
                            inactiveProductFound = true;
                            break;
                        }
                    } catch (Exception e) {
                        // Ignorar
                    }
                }
            }
            assertFalse(inactiveProductFound, "Producto Inactivo no debe aparecer en el análisis");
        }
    }

    @Test
    void generateSalesReport_stockRotationExcludesInactiveProductsAndAdjustments() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product activeProduct = new Product();
        activeProduct.setId(10L);
        activeProduct.setName("Producto Activo");
        activeProduct.setPrice(new BigDecimal("100"));
        activeProduct.setCost(new BigDecimal("60"));
        activeProduct.setIsActive(true);
        activeProduct.setStock(5);
        activeProduct.setStore(store);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(11L);
        inactiveProduct.setName("Producto Inactivo");
        inactiveProduct.setPrice(new BigDecimal("200"));
        inactiveProduct.setCost(new BigDecimal("120"));
        inactiveProduct.setIsActive(false);
        inactiveProduct.setStock(0);
        inactiveProduct.setStore(store);

        // ENTRADA válida: no genera ganancia
        Transaction entryTransaction = new Transaction();
        entryTransaction.setId(1L);
        entryTransaction.setProduct(activeProduct);
        entryTransaction.setProductId(10L);
        entryTransaction.setType("ENTRADA");
        entryTransaction.setReason("COMPRA");
        entryTransaction.setQuantity(3);
        entryTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 10, 0));

        // SALIDA válida: genera ganancia
        Transaction saleTransaction = new Transaction();
        saleTransaction.setId(2L);
        saleTransaction.setProduct(activeProduct);
        saleTransaction.setProductId(10L);
        saleTransaction.setType("SALIDA");
        saleTransaction.setReason("VENTA");
        saleTransaction.setQuantity(2);
        saleTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 11, 0));

        // AJUSTE ENTRADA: no debe contar
        Transaction adjustmentEntryTransaction = new Transaction();
        adjustmentEntryTransaction.setId(3L);
        adjustmentEntryTransaction.setProduct(activeProduct);
        adjustmentEntryTransaction.setProductId(10L);
        adjustmentEntryTransaction.setType("ENTRADA");
        adjustmentEntryTransaction.setReason("AJUSTE");
        adjustmentEntryTransaction.setQuantity(1);
        adjustmentEntryTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 12, 0));

        // AJUSTE SALIDA: no debe contar en ganancia
        Transaction adjustmentSaleTransaction = new Transaction();
        adjustmentSaleTransaction.setId(4L);
        adjustmentSaleTransaction.setProduct(activeProduct);
        adjustmentSaleTransaction.setProductId(10L);
        adjustmentSaleTransaction.setType("SALIDA");
        adjustmentSaleTransaction.setReason("AJUSTE");
        adjustmentSaleTransaction.setQuantity(1);
        adjustmentSaleTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 13, 0));

        // Transacción con producto inactivo: debe ignorarse
        Transaction inactiveProductTransaction = new Transaction();
        inactiveProductTransaction.setId(5L);
        inactiveProductTransaction.setProduct(inactiveProduct);
        inactiveProductTransaction.setProductId(11L);
        inactiveProductTransaction.setType("SALIDA");
        inactiveProductTransaction.setReason("VENTA");
        inactiveProductTransaction.setQuantity(5);
        inactiveProductTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 14, 0));

        when(transactionService.findAll()).thenReturn(List.of(
            entryTransaction, saleTransaction, adjustmentEntryTransaction, 
            adjustmentSaleTransaction, inactiveProductTransaction
        ));

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "COMPLETE");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet rotationSheet = workbook.getSheet("Rotación de Stock");
            assertNotNull(rotationSheet);

            // Buscar la fila del producto activo (después de encabezados)
            Row dataRow = null;
            for (int i = 5; i <= rotationSheet.getLastRowNum(); i++) {
                Row row = rotationSheet.getRow(i);
                if (row != null && row.getCell(0) != null && row.getCell(0).getCellType() == CellType.STRING) {
                    String cellValue = row.getCell(0).getStringCellValue();
                    if ("Producto Activo".equals(cellValue)) {
                        dataRow = row;
                        break;
                    }
                }
            }

            assertNotNull(dataRow, "No se encontró la fila de Producto Activo en Rotación de Stock");

            // Validaciones esperadas (Rotación tiene estructura similar a Análisis por Producto):
            // - Cantidad Entrada = 3 (solo ENTRADA COMPRA, ignora AJUSTE)
            // - Cantidad Salida = 2 (solo SALIDA VENTA, ignora AJUSTE)
            // - Costo Invertido = 180 (3 * 60)
            // - Precio Total = 200 (2 * 100, solo SALIDA - esto es lo que verificamos)
            // - Costo Total = 120 (2 * 60)
            // - Ingreso Total = 200 (2 * 100)
            // - Ganancia Total = 80 (200 - 120)

            double cantEntrada = dataRow.getCell(1).getNumericCellValue();
            double cantSalida = dataRow.getCell(2).getNumericCellValue();
            double costoInvertido = dataRow.getCell(6).getNumericCellValue();
            double precioTotal = dataRow.getCell(7).getNumericCellValue();
            double costoTotal = dataRow.getCell(8).getNumericCellValue();
            double ingresoTotal = dataRow.getCell(9).getNumericCellValue();
            double gananciaTotal = dataRow.getCell(10).getNumericCellValue();

            assertEquals(3.0, cantEntrada, "Cantidad Entrada = 3 (ignora AJUSTE)");
            assertEquals(2.0, cantSalida, "Cantidad Salida = 2 (ignora AJUSTE)");
            assertEquals(180.0, costoInvertido, 0.01, "Costo Invertido = 3 * 60");
            assertEquals(200.0, precioTotal, 0.01, "Precio Total = 2 * 100 (solo SALIDA, no ENTRADA)");
            assertEquals(120.0, costoTotal, 0.01, "Costo Total = 2 * 60");
            assertEquals(200.0, ingresoTotal, 0.01, "Ingreso Total = 2 * 100");
            assertEquals(80.0, gananciaTotal, 0.01, "Ganancia Total = 200 - 120");

            // Validar que producto inactivo NO aparece
            boolean inactiveProductFound = false;
            for (int i = 5; i <= rotationSheet.getLastRowNum(); i++) {
                Row row = rotationSheet.getRow(i);
                if (row != null && row.getCell(0) != null) {
                    try {
                        if ("Producto Inactivo".equals(row.getCell(0).getStringCellValue())) {
                            inactiveProductFound = true;
                            break;
                        }
                    } catch (Exception e) {
                        // Ignorar
                    }
                }
            }
            assertFalse(inactiveProductFound, "Producto Inactivo no debe aparecer en Rotación de Stock");
        }
    }

    @Test
    void generateSalesReport_productSalesAnalysisExcludesInactiveProductsAndAdjustments() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product activeProduct = new Product();
        activeProduct.setId(10L);
        activeProduct.setName("Producto Activo");
        activeProduct.setPrice(new BigDecimal("100"));
        activeProduct.setCost(new BigDecimal("60"));
        activeProduct.setIsActive(true);
        activeProduct.setStock(0);
        activeProduct.setStore(store);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(11L);
        inactiveProduct.setName("Producto Inactivo");
        inactiveProduct.setPrice(new BigDecimal("100"));
        inactiveProduct.setCost(new BigDecimal("60"));
        inactiveProduct.setIsActive(false);
        inactiveProduct.setStock(0);
        inactiveProduct.setStore(store);

        // SALIDA VENTA - activo
        Transaction activeVentaTransaction = new Transaction();
        activeVentaTransaction.setId(1L);
        activeVentaTransaction.setProduct(activeProduct);
        activeVentaTransaction.setProductId(10L);
        activeVentaTransaction.setType("SALIDA");
        activeVentaTransaction.setReason("VENTA");
        activeVentaTransaction.setQuantity(2);
        activeVentaTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 10, 0));

        // SALIDA AJUSTE - activo (debe ser excluido)
        Transaction activeAjusteTransaction = new Transaction();
        activeAjusteTransaction.setId(2L);
        activeAjusteTransaction.setProduct(activeProduct);
        activeAjusteTransaction.setProductId(10L);
        activeAjusteTransaction.setType("SALIDA");
        activeAjusteTransaction.setReason("AJUSTE");
        activeAjusteTransaction.setQuantity(1);
        activeAjusteTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 11, 0));

        // SALIDA VENTA - inactivo (debe ser excluido)
        Transaction inactiveVentaTransaction = new Transaction();
        inactiveVentaTransaction.setId(3L);
        inactiveVentaTransaction.setProduct(inactiveProduct);
        inactiveVentaTransaction.setProductId(11L);
        inactiveVentaTransaction.setType("SALIDA");
        inactiveVentaTransaction.setReason("VENTA");
        inactiveVentaTransaction.setQuantity(1);
        inactiveVentaTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 12, 0));

        when(transactionService.findAll()).thenReturn(List.of(activeVentaTransaction, activeAjusteTransaction, inactiveVentaTransaction));
        when(productService.findAll()).thenReturn(List.of(activeProduct, inactiveProduct));
        when(productService.findById(10L)).thenReturn(activeProduct);

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "COMPLETE");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet analysisSheet = workbook.getSheet("Análisis Ventas Productos");
            assertNotNull(analysisSheet);

            // Buscar fila del producto activo
            Row activeDataRow = null;
            for (int i = 3; i <= analysisSheet.getLastRowNum(); i++) {
                Row row = analysisSheet.getRow(i);
                if (row != null && row.getCell(0) != null) {
                    try {
                        if ("Producto Activo".equals(row.getCell(0).getStringCellValue())) {
                            activeDataRow = row;
                            break;
                        }
                    } catch (Exception e) {
                        // Ignorar
                    }
                }
            }

            assertNotNull(activeDataRow, "Producto Activo debe estar en el análisis de ventas");

            // Validar que solo contó la VENTA, no el AJUSTE (cantidad = 2, no 3)
            double cantidadVendida = activeDataRow.getCell(1).getNumericCellValue();
            double ingresos = activeDataRow.getCell(2).getNumericCellValue();
            double ganancia = activeDataRow.getCell(4).getNumericCellValue();

            assertEquals(2.0, cantidadVendida, "Cantidad Vendida = 2 (solo VENTA, excluye AJUSTE)");
            assertEquals(200.0, ingresos, 0.01, "Ingresos = 2 * 100");
            assertEquals(80.0, ganancia, 0.01, "Ganancia = 200 - (2 * 60)");

            // Validar que producto inactivo NO aparece en sección de ventas
            boolean inactiveProductFoundInSales = false;
            for (int i = 3; i <= analysisSheet.getLastRowNum(); i++) {
                Row row = analysisSheet.getRow(i);
                if (row != null && row.getCell(0) != null) {
                    try {
                        if ("Producto Inactivo".equals(row.getCell(0).getStringCellValue())) {
                            // Revisar si no está en la sección "PRODUCTOS SIN VENTA"
                            for (int j = Math.max(0, i - 5); j < i; j++) {
                                Row prevRow = analysisSheet.getRow(j);
                                if (prevRow != null && prevRow.getCell(0) != null) {
                                    if ("PRODUCTOS SIN VENTA".equals(prevRow.getCell(0).getStringCellValue())) {
                                        // Si no es en la sección sin venta, entonces está mal
                                        inactiveProductFoundInSales = true;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignorar
                    }
                }
            }
            assertFalse(inactiveProductFoundInSales, "Producto Inactivo no debe aparecer en sección de productos vendidos");
        }
    }

    @Test
    void generateSalesReport_analysisByLabelsExcludesInactiveProductsAndAdjustments() throws IOException {
        Store store = new Store();
        store.setId(1L);

        Product activeProduct = new Product();
        activeProduct.setId(10L);
        activeProduct.setName("Producto Activo");
        activeProduct.setPrice(new BigDecimal("100"));
        activeProduct.setCost(new BigDecimal("60"));
        activeProduct.setIsActive(true);
        activeProduct.setStock(0);
        activeProduct.setStore(store);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(11L);
        inactiveProduct.setName("Producto Inactivo");
        inactiveProduct.setPrice(new BigDecimal("100"));
        inactiveProduct.setCost(new BigDecimal("60"));
        inactiveProduct.setIsActive(false);
        inactiveProduct.setStock(0);
        inactiveProduct.setStore(store);

        // ENTRADA COMPRA - activo
        Transaction activeCompraTransaction = new Transaction();
        activeCompraTransaction.setId(1L);
        activeCompraTransaction.setProduct(activeProduct);
        activeCompraTransaction.setProductId(10L);
        activeCompraTransaction.setType("ENTRADA");
        activeCompraTransaction.setReason("COMPRA");
        activeCompraTransaction.setQuantity(3);
        activeCompraTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 10, 0));

        // SALIDA VENTA - activo
        Transaction activeVentaTransaction = new Transaction();
        activeVentaTransaction.setId(2L);
        activeVentaTransaction.setProduct(activeProduct);
        activeVentaTransaction.setProductId(10L);
        activeVentaTransaction.setType("SALIDA");
        activeVentaTransaction.setReason("VENTA");
        activeVentaTransaction.setQuantity(2);
        activeVentaTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 11, 0));

        // ENTRADA AJUSTE - activo (debe ser excluido del conteo)
        Transaction activeAjusteTransaction = new Transaction();
        activeAjusteTransaction.setId(3L);
        activeAjusteTransaction.setProduct(activeProduct);
        activeAjusteTransaction.setProductId(10L);
        activeAjusteTransaction.setType("ENTRADA");
        activeAjusteTransaction.setReason("AJUSTE");
        activeAjusteTransaction.setQuantity(1);
        activeAjusteTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 12, 0));

        // ENTRADA COMPRA - inactivo (debe ser excluido completamente)
        Transaction inactiveCompraTransaction = new Transaction();
        inactiveCompraTransaction.setId(4L);
        inactiveCompraTransaction.setProduct(inactiveProduct);
        inactiveCompraTransaction.setProductId(11L);
        inactiveCompraTransaction.setType("ENTRADA");
        inactiveCompraTransaction.setReason("COMPRA");
        inactiveCompraTransaction.setQuantity(5);
        inactiveCompraTransaction.setDateTime(LocalDateTime.of(2024, 1, 15, 13, 0));

        when(transactionService.findAll()).thenReturn(List.of(activeCompraTransaction, activeVentaTransaction, activeAjusteTransaction, inactiveCompraTransaction));
        when(productService.findAll()).thenReturn(List.of(activeProduct, inactiveProduct));
        when(productService.findById(10L)).thenReturn(activeProduct);

        byte[] excelBytes = salesReportService.generateSalesReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "COMPLETE");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet labelsSheet = workbook.getSheet("Etiquetas");
            assertNotNull(labelsSheet);

            // Buscar fila del producto activo (puede estar en cualquier fila después de los encabezados)
            Row activeDataRow = null;
            for (int i = 0; i <= labelsSheet.getLastRowNum(); i++) {
                Row row = labelsSheet.getRow(i);
                if (row != null && row.getCell(0) != null) {
                    try {
                        String cellValue = row.getCell(0).getStringCellValue();
                        if ("Producto Activo".equals(cellValue)) {
                            activeDataRow = row;
                            break;
                        }
                    } catch (Exception e) {
                        // Ignorar
                    }
                }
            }

            assertNotNull(activeDataRow, "Producto Activo debe estar en análisis por etiquetas");

            // Validar cantidades (debe excluir el AJUSTE)
            // Cantidad Entrada = 3 (solo COMPRA, excluye AJUSTE)
            // Cantidad Salida = 2 (VENTA)
            double cantEntrada = activeDataRow.getCell(1).getNumericCellValue();
            double cantSalida = activeDataRow.getCell(2).getNumericCellValue();

            assertEquals(3.0, cantEntrada, "Cantidad Entrada = 3 (solo COMPRA, excluye AJUSTE)");
            assertEquals(2.0, cantSalida, "Cantidad Salida = 2");

            // Validar que producto inactivo NO aparece en la hoja
            boolean inactiveProductFound = false;
            for (int i = 0; i <= labelsSheet.getLastRowNum(); i++) {
                Row row = labelsSheet.getRow(i);
                if (row != null && row.getCell(0) != null) {
                    try {
                        if ("Producto Inactivo".equals(row.getCell(0).getStringCellValue())) {
                            inactiveProductFound = true;
                            break;
                        }
                    } catch (Exception e) {
                        // Ignorar
                    }
                }
            }
            assertFalse(inactiveProductFound, "Producto Inactivo no debe aparecer en análisis por etiquetas");
        }
    }
}

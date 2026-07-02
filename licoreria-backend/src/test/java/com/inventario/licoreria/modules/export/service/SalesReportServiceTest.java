package com.inventario.licoreria.modules.export.service;

import com.inventario.licoreria.modules.administrative_costs.service.AdministrativeCostMovementService;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.service.TransactionService;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.users.service.UserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

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
}

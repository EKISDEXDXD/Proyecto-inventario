package com.inventario.licoreria.modules.reports.service;

import com.inventario.licoreria.modules.reports.dto.ReportDTO;
import com.inventario.licoreria.modules.reports.model.Report;
import com.inventario.licoreria.modules.reports.repository.ReportRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.util.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportExportService {

    private final ReportRepository reportRepository;
    private static final int PHOTO_WIDTH = 20; // Ancho de la columna de foto
    private static final int PHOTO_HEIGHT_PIXELS = 150; // Alto de la foto en píxeles

    public ReportExportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Exportar reportes a Excel con imágenes
     */
    @Transactional(readOnly = true)
    public byte[] exportReportsToExcel(Long storeId, LocalDate startDate, LocalDate endDate) throws IOException {
        List<Report> reports = reportRepository.findByStoreIdAndReportDateBetweenAndActive(storeId, startDate, endDate, true);
        return generateExcelWithReports(reports);
    }

    /**
     * Exportar todos los reportes de una tienda a Excel
     */
    @Transactional(readOnly = true)
    public byte[] exportAllReportsToExcel(Long storeId) throws IOException {
        List<Report> reports = reportRepository.findByStoreIdAndActive(storeId, true);
        return generateExcelWithReports(reports);
    }

    /**
     * Generar archivo Excel con los reportes
     */
    private byte[] generateExcelWithReports(List<Report> reports) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Reportes");
        
        // Configurar ancho de columnas
        sheet.setColumnWidth(0, 5000);   // ID
        sheet.setColumnWidth(1, 6000);   // Título
        sheet.setColumnWidth(2, 3000);   // Fecha
        sheet.setColumnWidth(3, 20000);  // Descripción
        sheet.setColumnWidth(4, 8000);   // Foto
        sheet.setColumnWidth(5, 4000);   // Usuario
        sheet.setColumnWidth(6, 4000);   // Creado

        // Crear encabezado
        createHeader(sheet);

        // Agregar reportes
        int rowNum = 1;
        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
        
        for (Report report : reports) {
            rowNum = addReportRow(sheet, drawing, report, rowNum);
        }

        // Generar byte array
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        
        return out.toByteArray();
    }

    /**
     * Crear fila de encabezado en el Excel
     */
    private void createHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        Font headerFont = sheet.getWorkbook().createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        String[] headers = {"ID", "Título", "Fecha", "Descripción", "Foto", "Usuario", "Creado"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * Agregar una fila de reporte al Excel
     */
    private int addReportRow(Sheet sheet, XSSFDrawing drawing, Report report, int rowNum) throws IOException {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(PHOTO_HEIGHT_PIXELS);

        // Estilo de celdas
        CellStyle borderStyle = sheet.getWorkbook().createCellStyle();
        borderStyle.setBorderBottom(BorderStyle.THIN);
        borderStyle.setBorderTop(BorderStyle.THIN);
        borderStyle.setBorderLeft(BorderStyle.THIN);
        borderStyle.setBorderRight(BorderStyle.THIN);
        borderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        borderStyle.setWrapText(true);

        CellStyle wrapStyle = sheet.getWorkbook().createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setBorderBottom(BorderStyle.THIN);
        wrapStyle.setBorderTop(BorderStyle.THIN);
        wrapStyle.setBorderLeft(BorderStyle.THIN);
        wrapStyle.setBorderRight(BorderStyle.THIN);

        // ID
        Cell idCell = row.createCell(0);
        idCell.setCellValue(report.getId());
        idCell.setCellStyle(borderStyle);

        // Título
        Cell titleCell = row.createCell(1);
        titleCell.setCellValue(report.getTitle());
        titleCell.setCellStyle(wrapStyle);

        // Fecha
        Cell dateCell = row.createCell(2);
        dateCell.setCellValue(report.getReportDate().toString());
        dateCell.setCellStyle(borderStyle);

        // Descripción
        Cell descCell = row.createCell(3);
        descCell.setCellValue(report.getDescription());
        descCell.setCellStyle(wrapStyle);

        // Foto
        Cell photoCell = row.createCell(4);
        if (report.getPhotoData() != null && report.getPhotoData().length > 0) {
            try {
                // Insertar imagen
                int pictureType = getPictureType(report.getPhotoMimeType());
                int pictureIndex = sheet.getWorkbook().addPicture(report.getPhotoData(), pictureType);
                
                // Crear anchor para la imagen
                ClientAnchor anchor = sheet.getWorkbook().getCreationHelper().createClientAnchor();
                anchor.setCol1(4);
                anchor.setRow1(rowNum);
                anchor.setCol2(5);
                anchor.setRow2(rowNum + 1);
                anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);

                // Añadir imagen
                Picture picture = drawing.createPicture(anchor, pictureIndex);
                picture.resize();
            } catch (Exception e) {
                photoCell.setCellValue("[Foto disponible]");
            }
        } else {
            photoCell.setCellValue("Sin foto");
        }
        photoCell.setCellStyle(borderStyle);

        // Usuario
        Cell userCell = row.createCell(5);
        userCell.setCellValue(report.getUser().getUsername());
        userCell.setCellStyle(borderStyle);

        // Creado
        Cell createdCell = row.createCell(6);
        createdCell.setCellValue(report.getCreatedAt().toString());
        createdCell.setCellStyle(borderStyle);

        return rowNum + 1;
    }

    /**
     * Determinar el tipo de imagen basado en MIME type
     */
    private int getPictureType(String mimeType) {
        if (mimeType == null) {
            return Workbook.PICTURE_TYPE_JPEG;
        }

        return switch (mimeType.toLowerCase()) {
            case "image/png" -> Workbook.PICTURE_TYPE_PNG;
            case "image/jpeg", "image/jpg" -> Workbook.PICTURE_TYPE_JPEG;
            default -> Workbook.PICTURE_TYPE_JPEG;
        };
    }
}

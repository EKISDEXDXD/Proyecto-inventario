package com.inventario.licoreria.modules.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportRequest {
    private String title;
    private String description;
    private LocalDate reportDate;
    // La foto se envía como multipart/form-data
}

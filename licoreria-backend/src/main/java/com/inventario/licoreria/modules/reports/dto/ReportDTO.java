package com.inventario.licoreria.modules.reports.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDTO {
    private Long id;
    private String title;
    private String description;
    private LocalDate reportDate;
    private String color;
    private String photoFileName;
    private String photoMimeType;
    private Long storeId;
    private String storeName;
    private Long userId;
    private String userName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean active;
}

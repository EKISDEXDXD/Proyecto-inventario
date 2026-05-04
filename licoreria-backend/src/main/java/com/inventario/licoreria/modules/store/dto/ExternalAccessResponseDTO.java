package com.inventario.licoreria.modules.store.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExternalAccessResponseDTO {
    private String token;
    private Long storeId;
    private String storeName;
    private String message;
}

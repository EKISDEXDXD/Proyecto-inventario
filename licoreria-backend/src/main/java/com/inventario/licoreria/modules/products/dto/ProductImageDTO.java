package com.inventario.licoreria.modules.products.dto;

import java.time.LocalDateTime;

public class ProductImageDTO {
    private Long id;
    private Long productId;
    private String imagePath;
    private String originalFileName;
    private Long fileSize;
    private Long compressedFileSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProductImageDTO() {
    }

    public ProductImageDTO(Long id, Long productId, String imagePath, String originalFileName, 
                          Long fileSize, Long compressedFileSize, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.productId = productId;
        this.imagePath = imagePath;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.compressedFileSize = compressedFileSize;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getCompressedFileSize() {
        return compressedFileSize;
    }

    public void setCompressedFileSize(Long compressedFileSize) {
        this.compressedFileSize = compressedFileSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

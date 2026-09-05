package com.inventario.licoreria.modules.products.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Ajuste de precio / promo: N salidas (AJUSTE) de productos origen -> 1 entrada (AJUSTE)
 * de un producto destino (un lote nuevo del mismo producto raiz, o un producto nuevo independiente).
 */
public class StockTransformationRequestDTO {

    @NotEmpty(message = "Debe indicar al menos un producto de origen")
    @Valid
    private List<SourceItemDTO> sources;

    @NotNull(message = "Debe indicar el producto destino")
    @Valid
    private TargetDTO target;

    public List<SourceItemDTO> getSources() {
        return sources;
    }

    public void setSources(List<SourceItemDTO> sources) {
        this.sources = sources;
    }

    public TargetDTO getTarget() {
        return target;
    }

    public void setTarget(TargetDTO target) {
        this.target = target;
    }

    public static class SourceItemDTO {
        @NotNull(message = "El ID del producto origen es obligatorio")
        private Long productId;

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a 0")
        private Integer quantity;

        @Valid
        private List<LoteAllocationDTO> loteAllocations;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public List<LoteAllocationDTO> getLoteAllocations() {
            return loteAllocations;
        }

        public void setLoteAllocations(List<LoteAllocationDTO> loteAllocations) {
            this.loteAllocations = loteAllocations;
        }
    }

    public static class LoteAllocationDTO {
        @NotNull(message = "El ID del lote es obligatorio")
        private Long loteId;

        @NotNull(message = "La cantidad del lote es obligatoria")
        @Positive(message = "La cantidad del lote debe ser mayor a 0")
        private Integer quantity;

        public Long getLoteId() {
            return loteId;
        }

        public void setLoteId(Long loteId) {
            this.loteId = loteId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    public static class TargetDTO {
        @NotBlank(message = "El modo del destino es obligatorio (LOTE o PRODUCTO_NUEVO)")
        private String mode;

        // Requerido si mode = LOTE: bajo qué producto raíz se crea el nuevo lote
        private Long parentProductId;

        // Requerido si mode = PRODUCTO_NUEVO: a qué tienda pertenece el producto nuevo
        private Long storeId;

        @NotBlank(message = "El nombre del producto destino es obligatorio")
        private String name;

        private String description;

        @NotNull(message = "El costo es obligatorio")
        @PositiveOrZero(message = "El costo debe ser mayor o igual a cero")
        private BigDecimal cost;

        @NotNull(message = "El precio es obligatorio")
        @PositiveOrZero(message = "El precio debe ser mayor o igual a cero")
        private BigDecimal price;

        @NotNull(message = "La cantidad del producto destino es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a 0")
        private Integer quantity;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Long getParentProductId() {
            return parentProductId;
        }

        public void setParentProductId(Long parentProductId) {
            this.parentProductId = parentProductId;
        }

        public Long getStoreId() {
            return storeId;
        }

        public void setStoreId(Long storeId) {
            this.storeId = storeId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getCost() {
            return cost;
        }

        public void setCost(BigDecimal cost) {
            this.cost = cost;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}

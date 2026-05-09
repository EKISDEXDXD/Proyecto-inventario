package com.inventario.licoreria.modules.products.dto;

import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.Tag;
import java.util.List;

public class ProductGalleryDTO {
    private Long id;
    private String name;
    private String description;
    private String cost;
    private String price;
    private String imagePath;
    private List<TagDTO> tags;

    public ProductGalleryDTO(Product product, String imagePath, List<Tag> tags) {
        this.id = product.getId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.cost = product.getCost() != null ? product.getCost().toString() : "0";
        this.price = product.getPrice() != null ? product.getPrice().toString() : "0";
        this.imagePath = imagePath;
        this.tags = tags.stream()
                .map(tag -> new TagDTO(tag.getId(), tag.getName()))
                .toList();
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCost() { return cost; }
    public String getPrice() { return price; }
    public String getImagePath() { return imagePath; }
    public List<TagDTO> getTags() { return tags; }

    public static class TagDTO {
        private Long id;
        private String name;

        public TagDTO(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
    }
}

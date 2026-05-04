package com.inventario.licoreria.modules.administrative_costs.controller;

import com.inventario.licoreria.modules.administrative_costs.dto.AdministrativeCostDTO;
import com.inventario.licoreria.modules.administrative_costs.model.AdministrativeCost;
import com.inventario.licoreria.modules.administrative_costs.service.AdministrativeCostService;
import com.inventario.licoreria.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/administrative-costs")
public class AdministrativeCostController {

    private final AdministrativeCostService administrativeCostService;
    private final JwtUtil jwtUtil;

    public AdministrativeCostController(
            AdministrativeCostService administrativeCostService,
            JwtUtil jwtUtil) {
        this.administrativeCostService = administrativeCostService;
        this.jwtUtil = jwtUtil;
    }

    private void validateNotExternal(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.isExternalAccess(token)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "Los usuarios externos solo tienen acceso a movimientos, no a costos administrativos.");
            }
        }
    }

    @GetMapping
    public List<AdministrativeCost> getAll(Authentication authentication) {
        return administrativeCostService.findAllByUsername(authentication.getName());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdministrativeCost> getById(@PathVariable @NonNull Long id) {
        return ResponseEntity.ok(administrativeCostService.findById(id));
    }

    @GetMapping("/store/{storeId}")
    public List<AdministrativeCost> getByStore(
            @PathVariable @NonNull Long storeId, 
            Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        return administrativeCostService.findByStoreId(storeId, authentication.getName());
    }

    @PostMapping
    public AdministrativeCost create(@Valid @RequestBody AdministrativeCostDTO dto, Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        return administrativeCostService.create(dto, authentication.getName());
    }

    @PutMapping("/{id}")
    public AdministrativeCost update(@PathVariable @NonNull Long id, @Valid @RequestBody AdministrativeCostDTO dto, Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        return administrativeCostService.update(id, dto, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id, Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        administrativeCostService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

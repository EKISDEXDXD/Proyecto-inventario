package com.inventario.licoreria.modules.reports.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.inventario.licoreria.modules.reports.dto.ReportDTO;
import com.inventario.licoreria.modules.reports.model.Report;
import com.inventario.licoreria.modules.reports.repository.ReportRepository;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.store.repository.StoreRepository;
import com.inventario.licoreria.modules.users.model.User;
import com.inventario.licoreria.modules.users.repository.UserRepository;

@Service
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository, StoreRepository storeRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
    }

    /**
     * Crear un nuevo reporte con foto opcional
     */
    public ReportDTO createReport(Long storeId, Long userId, String title, String description, 
                                 LocalDate reportDate, String color, MultipartFile photoFile) throws IOException {
        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new RuntimeException("Tienda no encontrada"));
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Report report = new Report();
        report.setTitle(title);
        report.setDescription(description);
        report.setReportDate(reportDate);
        report.setColor(color != null ? color : "#4f46e5");
        report.setStore(store);
        report.setUser(user);
        report.setActive(true);

        if (photoFile != null && !photoFile.isEmpty()) {
            report.setPhotoData(photoFile.getBytes());
            report.setPhotoMimeType(photoFile.getContentType());
            report.setPhotoFileName(photoFile.getOriginalFilename());
        }

        Report savedReport = reportRepository.save(report);
        return convertToDTO(savedReport);
    }

    /**
     * Obtener un reporte por ID
     */
    @Transactional(readOnly = true)
    public ReportDTO getReportById(Long reportId) {
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));
        return convertToDTO(report);
    }

    /**
     * Obtener la foto de un reporte
     */
    @Transactional(readOnly = true)
    public byte[] getReportPhoto(Long reportId) {
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));
        return report.getPhotoData();
    }

    /**
     * Obtener reportes de una tienda con paginación
     */
    @Transactional(readOnly = true)
    public Page<ReportDTO> getReportsByStore(Long storeId, Pageable pageable) {
        return reportRepository.findByStoreIdAndActiveOrderByReportDateDesc(storeId, true, pageable)
            .map(this::convertToDTO);
    }

    /**
     * Obtener todos los reportes de una tienda en un rango de fechas
     */
    @Transactional(readOnly = true)
    public List<ReportDTO> getReportsByStoreAndDateRange(Long storeId, LocalDate startDate, LocalDate endDate) {
        return reportRepository.findByStoreIdAndReportDateBetweenAndActive(storeId, startDate, endDate, true)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Obtener todos los reportes activos de una tienda
     */
    @Transactional(readOnly = true)
    public List<ReportDTO> getAllReportsByStore(Long storeId) {
        return reportRepository.findByStoreIdAndActive(storeId, true)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Actualizar un reporte
     */
    public ReportDTO updateReport(Long reportId, String title, String description, 
                                 LocalDate reportDate, String color, MultipartFile photoFile) throws IOException {
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));

        report.setTitle(title);
        report.setDescription(description);
        report.setReportDate(reportDate);
        if (color != null) {
            report.setColor(color);
        }

        if (photoFile != null && !photoFile.isEmpty()) {
            report.setPhotoData(photoFile.getBytes());
            report.setPhotoMimeType(photoFile.getContentType());
            report.setPhotoFileName(photoFile.getOriginalFilename());
        }

        Report updatedReport = reportRepository.save(report);
        return convertToDTO(updatedReport);
    }

    /**
     * Eliminar un reporte (borrado lógico)
     */
    public void deleteReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));
        report.setActive(false);
        reportRepository.save(report);
    }

    /**
     * Convertir entidad Report a DTO
     */
    private ReportDTO convertToDTO(Report report) {
        ReportDTO dto = new ReportDTO();
        dto.setId(report.getId());
        dto.setTitle(report.getTitle());
        dto.setDescription(report.getDescription());
        dto.setReportDate(report.getReportDate());
        dto.setColor(report.getColor());
        dto.setPhotoFileName(report.getPhotoFileName());
        dto.setPhotoMimeType(report.getPhotoMimeType());
        dto.setStoreId(report.getStore().getId());
        dto.setStoreName(report.getStore().getName());
        dto.setUserId(report.getUser().getId());
        dto.setUserName(report.getUser().getUsername());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setUpdatedAt(report.getUpdatedAt());
        dto.setActive(report.getActive());
        return dto;
    }
}

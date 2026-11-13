package com.inventario.licoreria.modules.agenda.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class AgendaBoardDTO {
    private Long id;
    private Long storeId;
    private String pillar;
    private String name;
    private String purpose;
    private Boolean defaultBoard;
    private String calendarType;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private List<AgendaEntryDTO> entries;
}
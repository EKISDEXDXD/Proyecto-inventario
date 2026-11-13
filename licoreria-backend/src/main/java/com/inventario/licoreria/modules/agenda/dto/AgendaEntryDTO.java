package com.inventario.licoreria.modules.agenda.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AgendaEntryDTO {
    private Long id;
    private Long boardId;
    private String title;
    private String category;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Boolean allDay;
    private Boolean weeklyRecurring;
    private String responsible;
    private String description;
    private String status;
    private String color;
}
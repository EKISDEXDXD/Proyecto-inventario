package com.inventario.licoreria.modules.agenda.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inventario.licoreria.modules.agenda.dto.AgendaBoardDTO;
import com.inventario.licoreria.modules.agenda.dto.AgendaEntryDTO;
import com.inventario.licoreria.modules.agenda.model.AgendaBoard;
import com.inventario.licoreria.modules.agenda.model.AgendaEntry;
import com.inventario.licoreria.modules.agenda.repository.AgendaBoardRepository;
import com.inventario.licoreria.modules.agenda.repository.AgendaEntryRepository;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.store.repository.StoreRepository;

@Service
@Transactional
public class AgendaService {

    private static final Set<String> PILLARS = Set.of("WEEKLY", "EVENTS", "ANNUAL");
    private static final Set<String> CALENDAR_TYPES = Set.of("STATIC", "DATED");
    private static final List<DefaultBoard> DEFAULT_BOARDS = List.of(
            new DefaultBoard("WEEKLY", "Semana operativa", "Turnos, horarios, limpieza y entregas de la semana."),
            new DefaultBoard("EVENTS", "Eventos del mes", "Eventos y fechas importantes de la tienda."),
            new DefaultBoard("ANNUAL", "Planificación anual", "Gestiones, contratos y revisiones por trimestre o semestre.")
    );

    private final AgendaBoardRepository boardRepository;
    private final AgendaEntryRepository entryRepository;
    private final StoreRepository storeRepository;

    public AgendaService(AgendaBoardRepository boardRepository, AgendaEntryRepository entryRepository,
                         StoreRepository storeRepository) {
        this.boardRepository = boardRepository;
        this.entryRepository = entryRepository;
        this.storeRepository = storeRepository;
    }

    public List<AgendaBoardDTO> getBoards(Long storeId, LocalDate start, LocalDate end) {
        Store store = findStore(storeId);
        ensureDefaultBoards(store);
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay().minusNanos(1);
        return boardRepository.findByStoreIdOrderByIdAsc(storeId).stream()
                .map(board -> toBoardDTO(board, rangeStart, rangeEnd))
                .toList();
    }

    public AgendaBoardDTO createBoard(Long storeId, AgendaBoardDTO dto, String username) {
        Store store = findStore(storeId);
        requireOwner(store, username);
        AgendaBoard board = new AgendaBoard();
        board.setStore(store);
        board.setPillar(normalizePillar(dto.getPillar()));
        board.setName(validateText(dto.getName(), "El nombre", 100));
        board.setPurpose(validateText(dto.getPurpose(), "El propósito", 500));
        applyCalendarType(board, dto.getCalendarType(), dto.getPeriodStartDate());
        board.setDefaultBoard(false);
        return toCurrentYearBoardDTO(boardRepository.save(board));
    }

    public AgendaBoardDTO updateBoard(Long boardId, AgendaBoardDTO dto, String username) {
        AgendaBoard board = findBoard(boardId);
        requireOwner(board.getStore(), username);
        board.setName(validateText(dto.getName(), "El nombre", 100));
        board.setPurpose(validateText(dto.getPurpose(), "El propósito", 500));
        applyCalendarType(board, dto.getCalendarType(), dto.getPeriodStartDate());
        return toCurrentYearBoardDTO(boardRepository.save(board));
    }

    public void deleteBoard(Long boardId, String username) {
        AgendaBoard board = findBoard(boardId);
        requireOwner(board.getStore(), username);
        if (Boolean.TRUE.equals(board.getDefaultBoard())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los tableros predeterminados no se pueden eliminar");
        }
        entryRepository.deleteAll(entryRepository.findByBoardId(boardId));
        boardRepository.delete(board);
    }

    public AgendaEntryDTO createEntry(Long boardId, AgendaEntryDTO dto, String username) {
        AgendaBoard board = findBoard(boardId);
        requireOwner(board.getStore(), username);
        AgendaEntry entry = new AgendaEntry();
        entry.setBoard(board);
        applyEntry(entry, dto, board);
        return toEntryDTO(entryRepository.save(entry));
    }

    public AgendaEntryDTO updateEntry(Long entryId, AgendaEntryDTO dto, String username) {
        AgendaEntry entry = findEntry(entryId);
        requireOwner(entry.getBoard().getStore(), username);
        applyEntry(entry, dto, entry.getBoard());
        return toEntryDTO(entryRepository.save(entry));
    }

    public void deleteEntry(Long entryId, String username) {
        AgendaEntry entry = findEntry(entryId);
        requireOwner(entry.getBoard().getStore(), username);
        entry.setActive(false);
        entryRepository.save(entry);
    }

    private void ensureDefaultBoards(Store store) {
        boolean changed = false;
        for (DefaultBoard defaults : DEFAULT_BOARDS) {
            if (!boardRepository.existsByStoreIdAndPillarAndDefaultBoardTrue(store.getId(), defaults.pillar())) {
                AgendaBoard board = new AgendaBoard();
                board.setStore(store);
                board.setPillar(defaults.pillar());
                board.setName(defaults.name());
                board.setPurpose(defaults.purpose());
                board.setDefaultBoard(true);
                board.setCalendarType("STATIC");
                boardRepository.save(board);
                changed = true;
            }
        }
        if (changed) {
            boardRepository.flush();
        }
    }

    private AgendaBoardDTO toBoardDTO(AgendaBoard board, LocalDateTime start, LocalDateTime end) {
        AgendaBoardDTO dto = new AgendaBoardDTO();
        dto.setId(board.getId());
        dto.setStoreId(board.getStore().getId());
        dto.setPillar(board.getPillar());
        dto.setName(board.getName());
        dto.setPurpose(board.getPurpose());
        dto.setDefaultBoard(board.getDefaultBoard());
        String calendarType = board.getCalendarType() == null ? "STATIC" : board.getCalendarType();
        dto.setCalendarType(calendarType);
        dto.setPeriodStartDate(board.getPeriodStartDate());
        dto.setPeriodEndDate(board.getPeriodEndDate());
        List<AgendaEntry> entries = "WEEKLY".equals(board.getPillar()) && "STATIC".equals(calendarType)
            ? entryRepository.findByBoardIdAndActiveTrueOrderByStartDateTimeAsc(board.getId())
            : entryRepository.findVisibleEntries(board.getId(), start, end);
        dto.setEntries(entries.stream()
                .map(this::toEntryDTO)
                .toList());
        return dto;
    }

    private AgendaBoardDTO toCurrentYearBoardDTO(AgendaBoard board) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.withDayOfYear(1).atStartOfDay();
        LocalDateTime end = today.withDayOfYear(today.lengthOfYear()).atTime(23, 59, 59);
        return toBoardDTO(board, start, end);
    }

    private AgendaEntryDTO toEntryDTO(AgendaEntry entry) {
        AgendaEntryDTO dto = new AgendaEntryDTO();
        dto.setId(entry.getId());
        dto.setBoardId(entry.getBoard().getId());
        dto.setTitle(entry.getTitle());
        dto.setCategory(entry.getCategory());
        dto.setStartDateTime(entry.getStartDateTime());
        dto.setEndDateTime(entry.getEndDateTime());
        dto.setAllDay(entry.getAllDay());
        dto.setWeeklyRecurring(Boolean.TRUE.equals(entry.getWeeklyRecurring()));
        dto.setResponsible(entry.getResponsible());
        dto.setDescription(entry.getDescription());
        dto.setStatus(entry.getStatus());
        dto.setColor(entry.getColor());
        return dto;
    }

    private void applyEntry(AgendaEntry entry, AgendaEntryDTO dto, AgendaBoard board) {
        entry.setTitle(validateText(dto.getTitle(), "El título", 120));
        entry.setCategory(dto.getCategory() == null || dto.getCategory().isBlank()
                ? "OTHER" : dto.getCategory().trim().toUpperCase(Locale.ROOT));
        if (dto.getStartDateTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de inicio es obligatoria");
        }
        if (dto.getEndDateTime() != null && dto.getEndDateTime().isBefore(dto.getStartDateTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha final no puede ser anterior a la inicial");
        }
        LocalDate entryDate = dto.getStartDateTime().toLocalDate();
        boolean weeklyRecurring = "STATIC".equals(normalizedCalendarType(board)) && "WEEKLY".equals(board.getPillar());
        if ("DATED".equals(normalizedCalendarType(board))
                && (entryDate.isBefore(board.getPeriodStartDate()) || entryDate.isAfter(board.getPeriodEndDate()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La actividad debe estar dentro de la semana del calendario");
        }
        entry.setStartDateTime(dto.getStartDateTime());
        entry.setEndDateTime(dto.getEndDateTime());
        entry.setAllDay(Boolean.TRUE.equals(dto.getAllDay()));
        entry.setWeeklyRecurring(weeklyRecurring);
        entry.setResponsible(limitOptional(dto.getResponsible(), 100));
        entry.setDescription(limitOptional(dto.getDescription(), 2000));
        entry.setStatus(dto.getStatus() == null || dto.getStatus().isBlank()
                ? "PENDING" : dto.getStatus().trim().toUpperCase(Locale.ROOT));
        entry.setColor(limitOptional(dto.getColor(), 20));
        entry.setActive(true);
    }

    private String normalizePillar(String pillar) {
        String normalized = pillar == null ? "" : pillar.trim().toUpperCase(Locale.ROOT);
        if (!PILLARS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pilar de agenda no es válido");
        }
        return normalized;
    }

    private void applyCalendarType(AgendaBoard board, String type, LocalDate periodStartDate) {
        String normalized = type == null || type.isBlank()
                ? "STATIC" : type.trim().toUpperCase(Locale.ROOT);
        if (!CALENDAR_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de calendario no es válido");
        }
        if ("DATED".equals(normalized)) {
            if (!"WEEKLY".equals(board.getPillar())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los calendarios con fecha solo están disponibles en Semana");
            }
            if (periodStartDate == null || periodStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El calendario con fecha debe iniciar un lunes");
            }
            board.setPeriodStartDate(periodStartDate);
            board.setPeriodEndDate(periodStartDate.plusDays(6));
        } else {
            board.setPeriodStartDate(null);
            board.setPeriodEndDate(null);
        }
        board.setCalendarType(normalized);
    }

    private String normalizedCalendarType(AgendaBoard board) {
        return board.getCalendarType() == null ? "STATIC" : board.getCalendarType();
    }

    private String validateText(String value, String label, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " es obligatorio y debe tener máximo " + maxLength + " caracteres");
        }
        return value.trim();
    }

    private String limitOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un campo de texto supera el máximo permitido");
        }
        return value.trim();
    }

    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
    }

    private AgendaBoard findBoard(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tablero no encontrado"));
    }

    private AgendaEntry findEntry(Long entryId) {
        return entryRepository.findById(entryId)
                .filter(AgendaEntry::getActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Actividad no encontrada"));
    }

    private void requireOwner(Store store, String username) {
        if (store.getManager() == null || !store.getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el dueño de la tienda puede editar la agenda");
        }
    }

    private record DefaultBoard(String pillar, String name, String purpose) {}
}
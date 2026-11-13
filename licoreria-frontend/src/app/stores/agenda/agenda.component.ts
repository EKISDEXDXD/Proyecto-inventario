import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, HostListener, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiConfigService } from '../../auth/api-config.service';
import { UserService } from '../../core/user.service';

type AgendaPillar = 'WEEKLY' | 'EVENTS' | 'ANNUAL';
type CalendarType = 'STATIC' | 'DATED';
type AnnualSummaryFilter = 'ALL' | 'PENDING' | 'DONE';

interface AgendaEntry {
  id?: number;
  boardId: number;
  title: string;
  category: string;
  startDateTime: string;
  endDateTime?: string | null;
  allDay: boolean;
  weeklyRecurring?: boolean;
  responsible?: string;
  description?: string;
  status: string;
  color?: string;
}

interface AgendaBoard {
  id: number;
  storeId: number;
  pillar: AgendaPillar;
  name: string;
  purpose: string;
  defaultBoard: boolean;
  calendarType?: CalendarType;
  periodStartDate?: string | null;
  periodEndDate?: string | null;
  entries: AgendaEntry[];
}

interface CalendarDay {
  date: Date;
  inMonth: boolean;
}

@Component({
  selector: 'app-agenda',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.css'
})
export class AgendaComponent implements OnInit {
  readonly pillars: { id: AgendaPillar; label: string; subtitle: string; icon: string }[] = [
    { id: 'WEEKLY', label: 'Semana', subtitle: 'Horarios y operación', icon: 'bx-calendar-week' },
    { id: 'EVENTS', label: 'Eventos', subtitle: 'Vista mensual', icon: 'bx-calendar-event' },
    { id: 'ANNUAL', label: 'Año', subtitle: 'Gestión y seguimiento', icon: 'bx-calendar' }
  ];
  readonly categories = [
    { value: 'STORE_HOURS', label: 'Horario de tienda' },
    { value: 'SHIFT', label: 'Turno de personal' },
    { value: 'DELIVERY', label: 'Entrega de productos' },
    { value: 'CLEANING', label: 'Limpieza' },
    { value: 'EVENT', label: 'Evento' },
    { value: 'CONTRACT', label: 'Contrato' },
    { value: 'REVIEW', label: 'Revisión' },
    { value: 'OTHER', label: 'Otro' }
  ];
  readonly weekDaysLabels = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
  readonly compactWeekDaysLabels = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];
  readonly monthNames = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

  storeId = 0;
  storeName = 'Tienda';
  isOwner = false;
  loading = true;
  saving = false;
  errorMessage = '';
  successMessage = '';
  activePillar: AgendaPillar = 'WEEKLY';
  currentDate = new Date();
  todayDate = new Date();
  selectedAnnualMonth: number | null = null;
  annualSummaryFilter: AnnualSummaryFilter | null = null;
  boards: AgendaBoard[] = [];
  activeBoardId: number | null = null;
  dialog: 'entry' | 'entry-details' | 'board' | null = null;
  editingEntryId: number | null = null;
  editingBoardId: number | null = null;
  entryForm: AgendaEntry = this.emptyEntry();
  selectedEntry: AgendaEntry | null = null;
  entryDate = '';
  selectedEntryDate: Date | null = null;
  entryDateSelected = false;
  isEditingSelectedDate = false;
  entryEndDate = '';
  pendingEntryEndDate = '';
  endDateDialogOpen = false;
  selectedBoardWeek = '';
  showBoardWeekPicker = false;
  entryStartTime = '09:00';
  entryEndTime = '';
  @ViewChild('entryTitle') private entryTitleInput?: ElementRef<HTMLInputElement>;
  @ViewChild('boardName') private boardNameInput?: ElementRef<HTMLInputElement>;
  boardForm = {
    pillar: 'WEEKLY' as AgendaPillar,
    name: '',
    purpose: '',
    calendarType: 'STATIC' as CalendarType,
    periodStartDate: ''
  };

  private readonly apiUrl: string;
  private readonly headers: HttpHeaders;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private apiConfig: ApiConfigService,
    private userService: UserService,
    private cdr: ChangeDetectorRef
  ) {
    this.apiUrl = this.apiConfig.getApiUrl('/api/agenda');
    this.headers = new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('token') || ''}` });
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.storeId = Number(params.get('id'));
      this.activeBoardId = null;
      this.loadStoreAndAgenda();
    });
  }

  get activeBoards(): AgendaBoard[] {
    const weekStart = this.dateOnly(this.startOfWeek(this.currentDate));
    return this.boards.filter(board => {
      if (board.pillar !== this.activePillar) return false;
      if (this.activePillar !== 'WEEKLY') return this.calendarType(board) === 'STATIC';
      return this.calendarType(board) === 'STATIC' || board.periodStartDate === weekStart;
    });
  }

  get orderedActiveBoards(): AgendaBoard[] {
    return [...this.activeBoards].sort((left, right) => {
      const typeOrder = Number(this.calendarType(left) === 'DATED') - Number(this.calendarType(right) === 'DATED');
      return typeOrder || Number(right.defaultBoard) - Number(left.defaultBoard) || left.id - right.id;
    });
  }

  get staticBoards(): AgendaBoard[] {
    return this.activeBoards
      .filter(board => this.calendarType(board) === 'STATIC')
      .sort((left, right) => Number(right.defaultBoard) - Number(left.defaultBoard) || left.id - right.id);
  }

  get datedBoards(): AgendaBoard[] {
    return this.activeBoards
      .filter(board => this.calendarType(board) === 'DATED')
      .sort((left, right) => (left.periodStartDate || '').localeCompare(right.periodStartDate || '') || left.id - right.id);
  }

  get activeBoard(): AgendaBoard | undefined {
    return this.activeBoards.find(board => board.id === this.activeBoardId) || this.activeBoards[0];
  }

  get isStaticWeekly(): boolean {
    return this.activePillar === 'WEEKLY' && this.calendarType(this.activeBoard) === 'STATIC';
  }

  boardPurpose(board: AgendaBoard): string {
    if (board.defaultBoard && board.pillar === 'WEEKLY') {
      return 'Organiza la semana con los calendarios que crees para esta tienda.';
    }
    return board.purpose;
  }

  calendarType(board: AgendaBoard | undefined): CalendarType {
    return board?.calendarType === 'DATED' ? 'DATED' : 'STATIC';
  }

  calendarTypeLabel(board: AgendaBoard): string {
    if (this.calendarType(board) === 'STATIC') {
      return board.defaultBoard ? 'Estático · predeterminado' : 'Estático';
    }
    const endDate = board.periodEndDate ? new Date(`${board.periodEndDate}T12:00:00`) : this.weekDays[6];
    return `Con fecha · hasta ${this.formatDate(endDate, { weekday: 'short', day: 'numeric', month: 'short' })}`;
  }

  get currentWeekLabel(): string {
    const days = this.weekDays;
    return `${this.formatDate(days[0], { day: 'numeric', month: 'short' })} – ${this.formatDate(days[6], { day: 'numeric', month: 'short' })}`;
  }

  get selectedEntryDateLabel(): string {
    return this.selectedEntryDate
      ? new Intl.DateTimeFormat('es', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(this.selectedEntryDate)
      : '';
  }

  updateSelectedEntryDate(value: string): void {
    this.entryDate = value;
    if (!value) return;
    const [year, month, day] = value.split('-').map(Number);
    this.selectedEntryDate = new Date(year, month - 1, day);
    if (this.isStaticWeekly) {
      this.entryForm.startDateTime = `${value}${this.entryForm.startDateTime.slice(10)}`;
    }
  }

  toggleSelectedDateEditor(): void {
    this.isEditingSelectedDate = !this.isEditingSelectedDate;
  }

  get entryEndDateLabel(): string {
    if (!this.entryEndDate) return '';
    const [year, month, day] = this.entryEndDate.split('-').map(Number);
    return new Intl.DateTimeFormat('es', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(year, month - 1, day));
  }

  get boardWeekLabel(): string {
    const monday = new Date(`${this.boardForm.periodStartDate}T12:00:00`);
    const sunday = this.addDays(monday, 6);
    return `${this.formatDate(monday, { day: 'numeric', month: 'short' })} – ${this.formatDate(sunday, { day: 'numeric', month: 'short', year: 'numeric' })}`;
  }

  get entries(): AgendaEntry[] {
    return this.activeBoard?.entries || [];
  }

  get weekDays(): Date[] {
    const monday = this.startOfWeek(this.currentDate);
    return Array.from({ length: 7 }, (_, index) => this.addDays(monday, index));
  }

  get monthDays(): CalendarDay[] {
    const first = new Date(this.currentDate.getFullYear(), this.currentDate.getMonth(), 1);
    const offset = (first.getDay() + 6) % 7;
    const start = this.addDays(first, -offset);
    const daysInMonth = new Date(this.currentDate.getFullYear(), this.currentDate.getMonth() + 1, 0).getDate();
    const cellCount = Math.ceil((offset + daysInMonth) / 7) * 7;
    return Array.from({ length: cellCount }, (_, index) => {
      const date = this.addDays(start, index);
      return { date, inMonth: date.getMonth() === this.currentDate.getMonth() };
    });
  }

  annualMonthDays(month: number): CalendarDay[] {
    const first = new Date(this.currentDate.getFullYear(), month, 1);
    const offset = (first.getDay() + 6) % 7;
    const start = this.addDays(first, -offset);
    const daysInMonth = new Date(this.currentDate.getFullYear(), month + 1, 0).getDate();
    const cellCount = Math.ceil((offset + daysInMonth) / 7) * 7;
    return Array.from({ length: cellCount }, (_, index) => {
      const date = this.addDays(start, index);
      return { date, inMonth: date.getMonth() === month };
    });
  }

  get totalEntries(): number {
    return this.annualEntries.length;
  }

  get pendingEntries(): number {
    return this.annualEntries.filter(entry => entry.status !== 'DONE').length;
  }

  get doneEntries(): number {
    return this.annualEntries.filter(entry => entry.status === 'DONE').length;
  }

  get annualEntries(): AgendaEntry[] {
    return this.entries
      .filter(entry => new Date(entry.startDateTime).getFullYear() === this.currentDate.getFullYear())
      .sort((left, right) => left.startDateTime.localeCompare(right.startDateTime));
  }

  get summaryEntries(): AgendaEntry[] {
    if (this.annualSummaryFilter === 'PENDING') return this.annualEntries.filter(entry => entry.status !== 'DONE');
    if (this.annualSummaryFilter === 'DONE') return this.annualEntries.filter(entry => entry.status === 'DONE');
    return this.annualEntries;
  }

  setAnnualSummaryFilter(filter: AnnualSummaryFilter): void {
    this.annualSummaryFilter = filter;
    this.selectedAnnualMonth = null;
  }

  boardCount(pillar: AgendaPillar): number {
    return this.boards.filter(board => board.pillar === pillar
      && (pillar === 'WEEKLY' || this.calendarType(board) === 'STATIC')).length;
  }

  setPillar(pillar: AgendaPillar): void {
    const previousYear = this.currentDate.getFullYear();
    if (pillar === 'WEEKLY') {
      this.currentDate = new Date();
      this.todayDate = new Date();
    }
    this.activePillar = pillar;
    this.selectedAnnualMonth = null;
    this.annualSummaryFilter = null;
    this.activeBoardId = this.preferredBoard(pillar)?.id ?? this.activeBoards[0]?.id ?? null;
    if (this.activeBoardId !== null) this.persistBoardSelection(pillar, this.activeBoardId);
    this.clearMessages();
    if (this.currentDate.getFullYear() !== previousYear) this.loadAgenda();
  }

  selectBoard(board: AgendaBoard): void {
    this.activeBoardId = board.id;
    this.persistBoardSelection(board.pillar, board.id);
    this.clearMessages();
  }

  shiftPeriod(amount: number): void {
    const previousYear = this.currentDate.getFullYear();
    if (this.activePillar === 'WEEKLY') {
      this.currentDate = new Date(this.currentDate.getFullYear(), this.currentDate.getMonth(), this.currentDate.getDate() + amount * 7);
    } else if (this.activePillar === 'EVENTS') {
      this.currentDate = new Date(this.currentDate.getFullYear(), this.currentDate.getMonth() + amount, 1);
    } else {
      this.currentDate = new Date(this.currentDate.getFullYear() + amount, this.currentDate.getMonth(), 1);
    }
    this.selectedAnnualMonth = null;
    this.annualSummaryFilter = null;
    if (this.currentDate.getFullYear() !== previousYear) {
      this.loadAgenda();
    } else {
      this.reconcileActiveBoard();
    }
  }

  goToToday(): void {
    const previousYear = this.currentDate.getFullYear();
    this.currentDate = new Date();
    this.todayDate = new Date();
    this.selectedAnnualMonth = null;
    this.annualSummaryFilter = null;
    if (this.currentDate.getFullYear() !== previousYear) {
      this.loadAgenda();
    } else {
      this.reconcileActiveBoard();
    }
  }

  showAnnualMonth(month: number): void {
    this.selectedAnnualMonth = month;
    this.annualSummaryFilter = null;
  }

  addAnnualEntry(): void {
    if (this.selectedAnnualMonth === null) return;
    this.openNewEntry(new Date(this.currentDate.getFullYear(), this.selectedAnnualMonth, 1), false);
  }

  openNewEntry(date?: Date, showSelectedDate = Boolean(date)): void {
    if (!this.isOwner || !this.activeBoard) return;
    const targetDate = date || this.currentDate;
    this.selectedEntry = null;
    this.editingEntryId = null;
    this.entryForm = this.emptyEntry(this.activeBoard.id, targetDate);
    this.entryDate = this.dateOnly(targetDate);
    this.selectedEntryDate = showSelectedDate ? targetDate : null;
    this.entryDateSelected = showSelectedDate;
    this.isEditingSelectedDate = false;
    this.entryEndDate = '';
    this.entryStartTime = '09:00';
    this.entryEndTime = '';
    this.dialog = 'entry';
    this.clearMessages();
    this.focusDialogInput();
  }

  viewEntry(entry: AgendaEntry): void {
    this.selectedEntry = entry;
    this.editingEntryId = null;
    this.dialog = 'entry-details';
    this.clearMessages();
  }

  editSelectedEntry(): void {
    if (!this.isOwner || !this.selectedEntry) return;
    const entry = this.selectedEntry;
    this.selectedEntry = null;
    this.editEntry(entry);
  }

  editEntry(entry: AgendaEntry): void {
    if (!this.isOwner || !entry.id) return;
    this.selectedEntry = null;
    this.editingEntryId = entry.id;
    this.selectedEntryDate = null;
    this.entryDateSelected = false;
    this.isEditingSelectedDate = false;
    this.entryForm = { ...entry, weeklyRecurring: Boolean(entry.weeklyRecurring), color: entry.color || '#7566e8' };
    this.entryDate = entry.startDateTime.slice(0, 10);
    this.entryEndDate = entry.endDateTime?.slice(0, 10) || '';
    this.entryStartTime = entry.startDateTime.slice(11, 16) || '09:00';
    this.entryEndTime = entry.endDateTime?.slice(11, 16) || '';
    this.dialog = 'entry';
  }

  openEndDateDialog(): void {
    this.pendingEntryEndDate = this.entryEndDate;
    this.endDateDialogOpen = true;
  }

  applyEndDate(): void {
    if (!this.pendingEntryEndDate) return;
    this.entryEndDate = this.pendingEntryEndDate;
    this.endDateDialogOpen = false;
  }

  removeEndDate(): void {
    this.entryEndDate = '';
    this.pendingEntryEndDate = '';
    this.endDateDialogOpen = false;
  }

  closeEndDateDialog(): void {
    this.endDateDialogOpen = false;
  }

  openBoardDialog(board?: AgendaBoard): void {
    if (!this.isOwner) return;
    this.editingBoardId = board?.id ?? null;
    this.showBoardWeekPicker = false;
    this.boardForm = board
      ? {
          pillar: board.pillar,
          name: board.name,
          purpose: board.purpose,
          calendarType: this.calendarType(board),
          periodStartDate: board.periodStartDate || this.dateOnly(this.startOfWeek(this.currentDate))
        }
      : {
          pillar: this.activePillar,
          name: '',
          purpose: '',
          calendarType: 'STATIC',
          periodStartDate: this.dateOnly(this.startOfWeek(this.currentDate))
        };
    this.selectedBoardWeek = this.toIsoWeekValue(this.boardForm.periodStartDate);
    this.dialog = 'board';
    this.focusDialogInput();
  }

  updateBoardWeek(value: string): void {
    const match = /^(\d{4})-W(\d{2})$/.exec(value);
    if (!match) return;
    const year = Number(match[1]);
    const week = Number(match[2]);
    const monday = this.startOfWeek(new Date(year, 0, 4));
    monday.setDate(monday.getDate() + (week - 1) * 7);
    this.selectedBoardWeek = value;
    this.boardForm.periodStartDate = this.dateOnly(monday);
    this.showBoardWeekPicker = false;
  }

  private toIsoWeekValue(dateValue: string): string {
    const date = new Date(`${dateValue}T12:00:00`);
    const monday = this.startOfWeek(date);
    const thursday = this.addDays(monday, 3);
    const weekYear = thursday.getFullYear();
    const firstMonday = this.startOfWeek(new Date(weekYear, 0, 4));
    const week = Math.round((monday.getTime() - firstMonday.getTime()) / (7 * 24 * 60 * 60 * 1000)) + 1;
    return `${weekYear}-W${String(week).padStart(2, '0')}`;
  }

  onBoardPillarChange(pillar: AgendaPillar): void {
    this.boardForm.pillar = pillar;
    if (pillar !== 'WEEKLY') {
      this.boardForm.calendarType = 'STATIC';
    }
  }

  saveEntry(): void {
    if (!this.isOwner || this.saving || !this.activeBoard) return;
    this.saving = true;
    const baseDate = this.isStaticWeekly ? this.entryForm.startDateTime.slice(0, 10) : this.entryDate;
    const startTime = this.entryForm.allDay ? '00:00' : this.entryStartTime || '09:00';
    const endDate = this.isStaticWeekly ? '' : this.entryEndDate;
    const endTime = this.entryForm.allDay ? '23:59' : this.entryEndTime;
    const payload = {
      ...this.entryForm,
      startDateTime: `${baseDate}T${startTime}`,
      endDateTime: this.isStaticWeekly
        ? (endTime ? `${baseDate}T${endTime}` : null)
        : (endDate ? `${endDate}T${endTime || '23:59'}` : (!this.entryForm.allDay && endTime ? `${baseDate}T${endTime}` : null)),
      weeklyRecurring: this.isStaticWeekly ? true : false
    };
    const request = this.editingEntryId
      ? this.http.put<AgendaEntry>(`${this.apiUrl}/entries/${this.editingEntryId}`, payload, { headers: this.headers })
      : this.http.post<AgendaEntry>(`${this.apiUrl}/boards/${this.activeBoard.id}/entries`, payload, { headers: this.headers });
    request.subscribe({
      next: saved => {
        this.replaceEntry(saved);
        this.closeDialog();
        this.saving = false;
        this.showSuccess(this.editingEntryId ? 'Actividad actualizada.' : 'Actividad agregada a la agenda.');
        this.cdr.detectChanges();
      },
      error: error => this.failSave(this.apiErrorMessage(error, 'No se pudo guardar la actividad.'))
    });
  }

  saveBoard(): void {
    if (!this.isOwner || this.saving) return;
    this.saving = true;
    const payload = {
      ...this.boardForm,
      periodStartDate: this.boardForm.calendarType === 'DATED' ? this.boardForm.periodStartDate : null
    };
    const request = this.editingBoardId
      ? this.http.put<AgendaBoard>(`${this.apiUrl}/boards/${this.editingBoardId}`, payload, { headers: this.headers })
      : this.http.post<AgendaBoard>(`${this.apiUrl}/store/${this.storeId}/boards`, payload, { headers: this.headers });
    request.subscribe({
      next: saved => {
        if (this.editingBoardId) {
          this.boards = this.boards.map(board => board.id === saved.id ? { ...board, ...saved } : board);
        } else {
          this.boards = [...this.boards, { ...saved, entries: saved.entries || [] }];
          this.activePillar = saved.pillar;
          this.activeBoardId = saved.id;
          this.persistBoardSelection(saved.pillar, saved.id);
        }
        this.closeDialog();
        this.saving = false;
        this.showSuccess(this.editingBoardId ? 'Calendario actualizado.' : 'Nuevo calendario creado.');
        this.cdr.detectChanges();
      },
      error: error => this.failSave(this.apiErrorMessage(error, 'No se pudo guardar el calendario.'))
    });
  }

  removeEntry(entry: AgendaEntry): void {
    if (!this.isOwner || !entry.id || !window.confirm(`¿Eliminar “${entry.title}” de la agenda?`)) return;
    this.http.delete<void>(`${this.apiUrl}/entries/${entry.id}`, { headers: this.headers }).subscribe({
      next: () => {
        this.boards = this.boards.map(board => board.id === entry.boardId
          ? { ...board, entries: board.entries.filter(item => item.id !== entry.id) }
          : board);
        this.showSuccess('Actividad eliminada.');
        this.cdr.detectChanges();
      },
      error: () => this.showError('No se pudo eliminar la actividad.')
    });
  }

  removeBoard(board: AgendaBoard): void {
    if (!this.isOwner || board.defaultBoard || !window.confirm(`¿Eliminar el calendario “${board.name}” y sus actividades?`)) return;
    this.http.delete<void>(`${this.apiUrl}/boards/${board.id}`, { headers: this.headers }).subscribe({
      next: () => {
        this.boards = this.boards.filter(item => item.id !== board.id);
        const nextBoard = this.activeBoards[0];
        this.activeBoardId = nextBoard?.id ?? null;
        if (nextBoard) this.persistBoardSelection(nextBoard.pillar, nextBoard.id);
        else localStorage.removeItem(this.boardSelectionKey(board.pillar));
        this.showSuccess('Calendario eliminado.');
        this.cdr.detectChanges();
      },
      error: () => this.showError('No se pudo eliminar el calendario.')
    });
  }

  entriesForDay(date: Date): AgendaEntry[] {
    return this.entries.filter(entry => {
      if (this.isStaticWeekly) {
        return new Date(entry.startDateTime).getDay() === date.getDay();
      }
      return this.sameDate(new Date(entry.startDateTime), date);
    });
  }

  entriesForMonth(month: number): AgendaEntry[] {
    return this.entries.filter(entry => {
      const date = new Date(entry.startDateTime);
      return date.getFullYear() === this.currentDate.getFullYear() && date.getMonth() === month;
    });
  }

  categoryLabel(value: string): string {
    return this.categories.find(category => category.value === value)?.label || value;
  }

  formatTime(value: string | null | undefined): string {
    if (!value) return 'Todo el día';
    return value.slice(11, 16);
  }

  formatEntryDate(value: string): string {
    return this.formatDate(new Date(value), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  }

  formatDate(date: Date, options?: Intl.DateTimeFormatOptions): string {
    return new Intl.DateTimeFormat('es', options || { day: 'numeric', month: 'short' }).format(date);
  }

  get periodTitle(): string {
    if (this.activePillar === 'WEEKLY') {
      if (this.isStaticWeekly) return 'Semana fija · lunes a domingo';
      const days = this.weekDays;
      return `${this.formatDate(days[0], { day: 'numeric', month: 'long' })} – ${this.formatDate(days[6], { day: 'numeric', month: 'long', year: 'numeric' })}`;
    }
    if (this.activePillar === 'EVENTS') {
      return `${this.monthNames[this.currentDate.getMonth()]} ${this.currentDate.getFullYear()}`;
    }
    return `${this.currentDate.getFullYear()}`;
  }

  readonly trackByDate = (_index: number, day: CalendarDay): string => this.dateKey(day.date);

  closeDialog(): void {
    this.dialog = null;
    this.selectedEntry = null;
    this.endDateDialogOpen = false;
    this.saving = false;
  }

  @HostListener('document:keydown', ['$event'])
  closeDialogOnEscape(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.endDateDialogOpen) {
      event.stopPropagation();
      this.closeEndDateDialog();
      return;
    }
    if (event.key === 'Escape' && this.dialog && !this.saving) this.closeDialog();
  }

  backToStore(): void {
    this.router.navigate(['/tienda', this.storeId]);
  }

  private loadStoreAndAgenda(): void {
    if (!this.storeId) return;
    this.loading = true;
    this.isOwner = false;
    this.http.get<any>(this.apiConfig.getApiUrl(`/api/stores/${this.storeId}`), { headers: this.headers }).subscribe({
      next: store => {
        this.storeName = store.name || 'Tienda';
        this.isOwner = store.managerUsername === this.userService.getCurrentUsername();
        this.cdr.detectChanges();
        this.loadAgenda();
      },
      error: () => {
        this.errorMessage = 'No se pudo cargar la información de la tienda.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private loadAgenda(): void {
    if (!this.storeId) return;
    this.loading = true;
    const year = this.currentDate.getFullYear();
    const params = new HttpParams()
      .set('start', `${year}-01-01`)
      .set('end', `${year}-12-31`);
    this.http.get<AgendaBoard[]>(`${this.apiUrl}/store/${this.storeId}`, { headers: this.headers, params }).subscribe({
      next: boards => {
        this.boards = boards;
        const activeBoard = this.activeBoards.find(board => board.id === this.activeBoardId);
        this.activeBoardId = activeBoard?.id ?? this.preferredBoard(this.activePillar)?.id ?? this.activeBoards[0]?.id ?? null;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.errorMessage = 'No se pudo cargar la agenda.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private replaceEntry(saved: AgendaEntry): void {
    this.boards = this.boards.map(board => {
      if (board.id !== saved.boardId) return board;
      const exists = board.entries.some(entry => entry.id === saved.id);
      const entries = exists
        ? board.entries.map(entry => entry.id === saved.id ? saved : entry)
        : [...board.entries, saved];
      return { ...board, entries: entries.sort((a, b) => a.startDateTime.localeCompare(b.startDateTime)) };
    });
  }

  private emptyEntry(boardId = this.activeBoardId || 0, date = this.currentDate): AgendaEntry {
    return {
      boardId,
      title: '',
      category: this.activePillar === 'WEEKLY' ? 'OTHER' : this.activePillar === 'EVENTS' ? 'EVENT' : 'REVIEW',
      startDateTime: this.toLocalDateTime(date, '09:00'),
      endDateTime: '',
      allDay: true,
      weeklyRecurring: this.activePillar === 'WEEKLY' && this.calendarType(this.activeBoard) === 'STATIC',
      responsible: '',
      description: '',
      status: 'PENDING',
      color: '#7566e8'
    };
  }

  private toLocalDateTime(date: Date, time: string): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}T${time}`;
  }

  private startOfWeek(date: Date): Date {
    const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    result.setDate(result.getDate() - ((result.getDay() + 6) % 7));
    return result;
  }

  private addDays(date: Date, amount: number): Date {
    const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    result.setDate(result.getDate() + amount);
    return result;
  }

  sameDate(left: Date, right: Date): boolean {
    return left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth() && left.getDate() === right.getDate();
  }

  private dateKey(date: Date): string {
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
  }

  private dateOnly(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private preferredBoard(pillar: AgendaPillar): AgendaBoard | undefined {
    const savedId = Number(localStorage.getItem(this.boardSelectionKey(pillar)));
    return this.activeBoards.find(board => board.id === savedId);
  }

  private reconcileActiveBoard(): void {
    if (this.activeBoards.some(board => board.id === this.activeBoardId)) return;
    const nextBoard = this.preferredBoard(this.activePillar) || this.activeBoards[0];
    this.activeBoardId = nextBoard?.id ?? null;
    if (nextBoard) this.persistBoardSelection(nextBoard.pillar, nextBoard.id);
  }

  private persistBoardSelection(pillar: AgendaPillar, boardId: number): void {
    localStorage.setItem(this.boardSelectionKey(pillar), String(boardId));
  }

  private boardSelectionKey(pillar: AgendaPillar): string {
    return `agenda-board-${this.storeId}-${pillar}`;
  }

  private focusDialogInput(): void {
    requestAnimationFrame(() => {
      const input = this.dialog === 'entry' ? this.entryTitleInput?.nativeElement : this.boardNameInput?.nativeElement;
      input?.focus({ preventScroll: true });
    });
  }

  private failSave(message: string): void {
    this.saving = false;
    this.showError(message);
    this.cdr.detectChanges();
  }

  private apiErrorMessage(error: any, fallback: string): string {
    return error?.error?.message || error?.error?.detail || fallback;
  }

  private showSuccess(message: string): void {
    this.successMessage = message;
    this.errorMessage = '';
    window.setTimeout(() => this.successMessage = '', 3200);
  }

  private showError(message: string): void {
    this.errorMessage = message;
    this.successMessage = '';
  }

  private clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }
}
package com.attendifyplus.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendifyplus.data.local.entities.*
import com.attendifyplus.data.repositories.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Calendar

class TeacherDashboardViewModel(
    private val teacherRepo: TeacherRepository,
    private val studentRepo: StudentRepository,
    private val subjectClassRepo: SubjectClassRepository,
    private val schoolEventRepo: SchoolEventRepository,
    private val attendanceRepo: AttendanceRepository,
    private val schoolPeriodRepo: SchoolPeriodRepository
) : ViewModel() {

    val allStudents: StateFlow<List<StudentEntity>> = studentRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Suppress("unused") // Used by UI
    val unsyncedCount: StateFlow<Int> = attendanceRepo.unsyncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _adviserDetails = MutableStateFlow<TeacherEntity?>(null)
    @Suppress("unused") // Used by UI
    val adviserDetails: StateFlow<TeacherEntity?> = _adviserDetails.asStateFlow()

    private val _studentCount = MutableStateFlow(0)
    @Suppress("unused") // Used by UI
    val studentCount: StateFlow<Int> = _studentCount.asStateFlow()

    private val _currentTeacherId = MutableStateFlow("T001")
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val subjectClasses: StateFlow<List<SubjectClassEntity>> = _currentTeacherId
        .flatMapLatest { id -> subjectClassRepo.getClassesForTeacher(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _userName = MutableStateFlow("")
    @Suppress("unused") // Used by UI
    val userName: StateFlow<String> = _userName.asStateFlow()

    // Reactive Daily Status
    val dailyStatus: StateFlow<String> = schoolEventRepo.getAllEvents()
        .map { events ->
            val noClassEvent = events.find { event ->
                val eventCal = Calendar.getInstance().apply { timeInMillis = event.date }
                val nowCal = Calendar.getInstance()
                val sameDay = eventCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                              eventCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
                
                sameDay && event.isNoClass
            }
            
            noClassEvent?.title ?: "Class Day"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Class Day")
    
    val schoolPeriod: StateFlow<SchoolPeriodEntity?> = schoolPeriodRepo.periodFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isCalendarConfigured: StateFlow<Boolean> = schoolPeriod.map { it != null && it.schoolYear.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val upcomingEvents: StateFlow<List<SchoolEventEntity>> = schoolEventRepo.getAllEvents()
        .map { events ->
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            events
                .filter { it.date >= today && !it.isNoClass } // Filter out status events
                .sortedBy { it.date }
                .take(2) // Limit to 2 events
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Load details for default teacher T001 immediately upon initialization
        // Moved here to ensure all properties are initialized before usage
        loadAdviserDetails("T001")
    }

    fun loadAdviserDetails(teacherId: String = "T001") {
        _currentTeacherId.value = teacherId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            teacherRepo.getByIdFlow(teacherId).collect { teacher ->
                _adviserDetails.value = teacher
                _userName.value = teacher?.firstName ?: "Teacher"
                val grade = teacher?.advisoryGrade
                val section = teacher?.advisorySection
                if (grade != null && section != null) {
                    _studentCount.value =
                        studentRepo.countByClass(grade, section)
                } else {
                    _studentCount.value = 0
                }
            }
        }
    }

    fun updateDailyStatus(isNoClass: Boolean, reason: String) {
        viewModelScope.launch {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // Get current snapshot of events
            val allEvents = schoolEventRepo.getAllEvents().first()
            
            val todayNoClassEvents = allEvents.filter { event ->
                val eventCal = Calendar.getInstance().apply { timeInMillis = event.date }
                val sameDay = eventCal.get(Calendar.YEAR) == todayStart.get(Calendar.YEAR) &&
                              eventCal.get(Calendar.DAY_OF_YEAR) == todayStart.get(Calendar.DAY_OF_YEAR)
                sameDay && event.isNoClass
            }

            if (isNoClass) {
                // Clear previous conflicting status to avoid duplicates
                todayNoClassEvents.forEach { schoolEventRepo.deleteEvent(it.id) }

                val newEvent = SchoolEventEntity(
                    date = todayStart.timeInMillis,
                    title = reason,
                    description = "Manual Status Update",
                    type = "holiday",
                    isNoClass = true,
                    synced = false
                )
                schoolEventRepo.addEvent(newEvent)
            } else {
                // Revert to Class Day -> Delete No Class events
                todayNoClassEvents.forEach { schoolEventRepo.deleteEvent(it.id) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadAdviserDetails(_currentTeacherId.value)
            delay(1500)
            _isRefreshing.value = false
        }
    }
}

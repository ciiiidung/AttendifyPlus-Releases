package com.attendifyplus.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendifyplus.data.local.entities.AttendanceEntity
import com.attendifyplus.data.local.entities.SchoolEventEntity
import com.attendifyplus.data.local.entities.StudentEntity
import com.attendifyplus.data.local.entities.SubjectClassEntity
import com.attendifyplus.data.local.entities.TeacherEntity
import com.attendifyplus.data.repositories.AttendanceRepository
import com.attendifyplus.data.repositories.SchoolEventRepository
import com.attendifyplus.data.repositories.StudentRepository
import com.attendifyplus.data.repositories.SubjectClassRepository
import com.attendifyplus.data.repositories.TeacherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

data class StudentStats(
    val present: Int = 0,
    val late: Int = 0,
    val absent: Int = 0,
    val lastLog: AttendanceEntity? = null
)

class DashboardViewModel(
    private val attendanceRepo: AttendanceRepository,
    private val teacherRepo: TeacherRepository,
    private val studentRepo: StudentRepository,
    private val subjectClassRepo: SubjectClassRepository,
    private val schoolEventRepo: SchoolEventRepository
) : ViewModel() {
    
    val unsyncedCount: StateFlow<Int> = attendanceRepo.unsyncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    private val _adviserDetails = MutableStateFlow<TeacherEntity?>(null)
    val adviserDetails: StateFlow<TeacherEntity?> = _adviserDetails
    
    private val _studentCount = MutableStateFlow(0)
    val studentCount: StateFlow<Int> = _studentCount

    private val _subjectClasses = MutableStateFlow<List<SubjectClassEntity>>(emptyList())
    val subjectClasses: StateFlow<List<SubjectClassEntity>> = _subjectClasses

    private val _studentStats = MutableStateFlow(StudentStats())
    val studentStats: StateFlow<StudentStats> = _studentStats.asStateFlow()
    
    private val _studentProfile = MutableStateFlow<StudentEntity?>(null)
    val studentProfile: StateFlow<StudentEntity?> = _studentProfile.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()
    
    // Daily Status State
    private val _dailyStatus = MutableStateFlow("Class Day")
    val dailyStatus: StateFlow<String> = _dailyStatus.asStateFlow()

    // Expose all teachers for Admin Dashboard
    val allTeachers: StateFlow<List<TeacherEntity>> = teacherRepo.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin: Total Students Count
    val totalStudentCount: StateFlow<Int> = studentRepo.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var loadJob: Job? = null

    // Start of Day Logic
    private val startOfDay: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    val presentCount = attendanceRepo.getPresentCount(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val lateCount = attendanceRepo.getLateCount(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val absentCount = attendanceRepo.getAbsentCount(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // Observe School Events to automatically update status
        viewModelScope.launch {
            schoolEventRepo.getAllEvents().collect { events ->
                val today = startOfDay
                // Find event for today
                val todaysEvent = events.find { 
                    val eventCal = Calendar.getInstance().apply { timeInMillis = it.date }
                    eventCal.set(Calendar.HOUR_OF_DAY, 0)
                    eventCal.set(Calendar.MINUTE, 0)
                    eventCal.set(Calendar.SECOND, 0)
                    eventCal.set(Calendar.MILLISECOND, 0)
                    eventCal.timeInMillis == today
                }

                if (todaysEvent != null) {
                    var newStatus = when(todaysEvent.type.lowercase()) {
                        "suspension" -> "Suspended"
                        "holiday" -> "Holiday"
                        "activity" -> "Program"
                        else -> "Class Day"
                    }
                    
                    // Append reason/description if available
                    if (!todaysEvent.description.isNullOrBlank()) {
                        newStatus = "$newStatus (${todaysEvent.description})"
                    }
                    
                    // If the status is different, update it and trigger logic
                    if (_dailyStatus.value != newStatus) {
                        _dailyStatus.value = newStatus
                    }
                } else {
                    // Revert to Class Day if no event exists
                    if (_dailyStatus.value != "Class Day") {
                        _dailyStatus.value = "Class Day"
                    }
                }
            }
        }
    }

    fun loadAdviserDetails(teacherId: String = "T001") {
        // Cancel previous subscription if any
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            launch {
                teacherRepo.getByIdFlow(teacherId).collect { teacher ->
                    _adviserDetails.value = teacher
                    _userName.value = teacher?.firstName ?: "Teacher"
                    val grade = teacher?.advisoryGrade
                    val section = teacher?.advisorySection
                    if (grade != null && section != null) {
                        _studentCount.value = studentRepo.countByClass(grade, section)
                    } else {
                        _studentCount.value = 0
                    }
                }
            }
            launch {
                subjectClassRepo.getClassesForTeacher(teacherId).collect { classes ->
                    _subjectClasses.value = classes
                }
            }
        }
    }
    
    fun loadStudentDetails(studentId: String) {
        viewModelScope.launch {
            val student = studentRepo.getById(studentId)
            _studentProfile.value = student
            _userName.value = student?.firstName ?: "Student"

            // Load student's subject classes
            launch {
                if (student != null) {
                    subjectClassRepo.getClassesByGradeAndSection(student.grade, student.section).collect { classes ->
                        _subjectClasses.value = classes
                    }
                }
            }
            
            // Load Stats
            val history = attendanceRepo.getStudentHistory(studentId).first()
            val p = history.count { it.status.equals("present", ignoreCase = true) }
            val l = history.count { it.status.equals("late", ignoreCase = true) }
            val a = history.count { it.status.equals("absent", ignoreCase = true) }
            
            val last = history.maxByOrNull { it.timestamp }
            
            _studentStats.value = StudentStats(p, l, a, last)
        }
    }
    
    fun saveDailyStatus(date: Calendar, status: String) {
        viewModelScope.launch {
            // Normalize Date to Start of Day to ensure consistency
            val cal = date.clone() as Calendar
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val timestamp = cal.timeInMillis
            
            val isToday = timestamp == startOfDay
            
            if (isToday) {
                _dailyStatus.value = status
            }

            // Persistence Logic: Sync with DB
            val events = schoolEventRepo.getAllEvents().first()
            val existingEvent = events.find { it.date == timestamp }

            if (status.contains("Class Day", ignoreCase = true)) {
                 if (existingEvent != null) {
                     schoolEventRepo.deleteEvent(existingEvent.id)
                 }
            } else {
                val type = when {
                    status.contains("Suspended", ignoreCase = true) -> "suspension"
                    status.contains("Holiday", ignoreCase = true) -> "holiday"
                    status.contains("No Class", ignoreCase = true) -> "holiday"
                    else -> "activity"
                }
                val description = if (status.contains("(")) status.substringAfter("(").substringBefore(")") else null
                val title = status.substringBefore("(").trim()
                
                val newEvent = SchoolEventEntity(
                    id = existingEvent?.id ?: 0,
                    date = timestamp,
                    title = title,
                    type = type,
                    description = description,
                    isNoClass = true,
                    synced = false
                )
                schoolEventRepo.addEvent(newEvent)
            }

            // Logic for automatic attendance: Check if status contains keywords
            val isNoClass = status.contains("Suspended", ignoreCase = true) || 
                            status.contains("Holiday", ignoreCase = true) || 
                            status.contains("Cancelled", ignoreCase = true) ||
                            status.contains("No Class", ignoreCase = true)
            
            if (isNoClass && isToday) {
                // Get Students (Advisory Class)
                val adviser = _adviserDetails.value
                val grade = adviser?.advisoryGrade
                val section = adviser?.advisorySection
                if (grade != null && section != null) {
                    try {
                        // Fetch students currently in the advisory class
                        val students = studentRepo.getByClass(grade, section).first()
                        
                        students.forEach { student ->
                            // Avoid duplicates for the same day/timestamp if possible
                            if (!attendanceRepo.exists(student.id, timestamp)) {
                                val attendance = AttendanceEntity(
                                    studentId = student.id,
                                    timestamp = timestamp,
                                    status = "Not Applicable",
                                    synced = false,
                                    type = "homeroom"
                                )
                                attendanceRepo.record(attendance)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
             delay(200)
        }
    }
    
    fun updateTeacherCredentials(username: String, password: String) {
        val currentTeacher = _adviserDetails.value ?: return
        val updatedTeacher = currentTeacher.copy(
            username = username,
            password = password,
            hasChangedCredentials = true
        )
        viewModelScope.launch {
            teacherRepo.insert(updatedTeacher)
            // We rely on the Flow to update _adviserDetails automatically from Repo
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Re-trigger loading (though flows stay active, this ensures we have the latest if something stalled)
            loadAdviserDetails() 
            // The actual sync logic 
            delay(1000)
            _isRefreshing.value = false
        }
    }
}

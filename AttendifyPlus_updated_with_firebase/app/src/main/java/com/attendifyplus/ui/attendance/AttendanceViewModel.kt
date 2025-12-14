package com.attendifyplus.ui.attendance

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendifyplus.data.local.entities.AttendanceEntity
import com.attendifyplus.data.repositories.AttendanceRepository
import com.attendifyplus.data.repositories.SchoolPeriodRepository
import com.attendifyplus.data.repositories.StudentRepository
import com.attendifyplus.data.repositories.SubjectClassRepository
import com.attendifyplus.data.repositories.TeacherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class ScanState {
    object Idle : ScanState()
    object Loading : ScanState()
    data class Success(val studentId: String, val status: String) : ScanState()
    data class Error(val message: String) : ScanState()
}

class AttendanceViewModel(
    private val attendanceRepo: AttendanceRepository,
    private val studentRepo: StudentRepository,
    private val teacherRepo: TeacherRepository,
    private val subjectClassRepo: SubjectClassRepository,
    private val schoolPeriodRepo: SchoolPeriodRepository,
    private val context: Context // Injected context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("attendify_session", Context.MODE_PRIVATE)
    // Fetch the actual logged-in teacher ID. Fallback to T001 only if missing (e.g. dev mode)
    private val teacherId = prefs.getString("user_id", null) ?: "T001"

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    // Now uses the dynamic teacherId
    val subjectClasses = subjectClassRepo.getClassesForTeacher(teacherId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }

    private suspend fun determineAcademicPeriod(timestamp: Long, isJhs: Boolean): String? {
        val period = schoolPeriodRepo.periodFlow.firstOrNull() ?: return null
        return when {
            isJhs -> when (timestamp) {
                in period.q1Start..period.q1End -> "Q1"
                in period.q2Start..period.q2End -> "Q2"
                in period.q3Start..period.q3End -> "Q3"
                in period.q4Start..period.q4End -> "Q4"
                else -> null
            }
            else -> when (timestamp) { // SHS
                in period.shsQ1Start..period.shsQ1End -> "Q1"
                in period.shsQ2Start..period.shsQ2End -> "Q2"
                in period.shsQ3Start..period.shsQ3End -> "Q3"
                in period.shsQ4Start..period.shsQ4End -> "Q4"
                else -> null
            }
        }
    }

    fun recordQr(studentId: String, type: String = "homeroom", subjectName: String? = null) {
        viewModelScope.launch {
            _scanState.value = ScanState.Loading
            try {
                val now = System.currentTimeMillis()
                val student = studentRepo.getById(studentId)
                
                if (student == null) {
                     _scanState.value = ScanState.Error("Student not found")
                     return@launch
                }

                val isJhs = student.grade.toIntOrNull() in 7..10
                val academicPeriod = determineAcademicPeriod(now, isJhs)

                if (academicPeriod == null) {
                    _scanState.value = ScanState.Error("Academic calendar not set for this date.")
                    return@launch
                }

                val status = "present"
                
                val entity = AttendanceEntity(
                    studentId = studentId, 
                    timestamp = now, 
                    status = status,
                    synced = false,
                    type = type,
                    subject = if (type == "subject") subjectName else null,
                    academicPeriod = academicPeriod
                )
                attendanceRepo.record(entity)
                attendanceRepo.triggerSync() // Trigger immediate sync
                _message.value = "Recorded: $studentId ($status)"
                _scanState.value = ScanState.Success(studentId, status)
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun recordManual(
        studentIdentifier: String, 
        status: String, 
        type: String = "homeroom", 
        subjectName: String? = null,
        timestamp: Long = System.currentTimeMillis() 
    ) {
        viewModelScope.launch {
            // Try to resolve student by ID or Name
            var student = studentRepo.getById(studentIdentifier)
            
            if (student == null) {
                // Fallback: Search by Name
                val allStudents = studentRepo.getAllList()
                student = allStudents.find { 
                    val fullName = "${it.firstName} ${it.lastName}"
                    val reverseName = "${it.lastName}, ${it.firstName}"
                    
                    fullName.equals(studentIdentifier, ignoreCase = true) ||
                    reverseName.equals(studentIdentifier, ignoreCase = true) ||
                    it.firstName.equals(studentIdentifier, ignoreCase = true) ||
                    it.lastName.equals(studentIdentifier, ignoreCase = true)
                }
            }

            if (student == null) {
                _message.value = "Error: Student '$studentIdentifier' not found"
                return@launch
            }

            val isJhs = student.grade.toIntOrNull() in 7..10
            val academicPeriod = determineAcademicPeriod(timestamp, isJhs)

            if (academicPeriod == null) {
                _message.value = "Error: Academic calendar not set for this date."
                return@launch
            }

            val entity = AttendanceEntity(
                studentId = student.id, 
                timestamp = timestamp, 
                status = status, 
                synced = false,
                type = type,
                subject = if (type == "subject") subjectName else null,
                academicPeriod = academicPeriod
            )
            attendanceRepo.record(entity)
            attendanceRepo.triggerSync() // Trigger immediate sync
            _message.value = "Recorded manual: ${student.firstName} ($status)"
        }
    }

    fun markAbsentees(
        grade: String, 
        section: String, 
        type: String = "homeroom", 
        subjectName: String? = null
    ) {
        viewModelScope.launch {
            try {
                val todayMidnight = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                // 1. Get all students in this class
                val enrolledStudents = studentRepo.getByClassList(grade, section)
                
                if (enrolledStudents.isEmpty()) {
                    _message.value = "No students enrolled in this class."
                    return@launch
                }

                // 2. Get present/late records for today
                val history = attendanceRepo.getHistoryBySubjectInDateRange(
                    subjectName ?: "", 
                    todayMidnight, 
                    System.currentTimeMillis()
                )
                
                val presentStudentIds = history.map { it.studentId }.toSet()
                
                // 3. Find students who are NOT present/late
                val absentees = enrolledStudents.filter { it.id !in presentStudentIds }
                
                if (absentees.isEmpty()) {
                    _message.value = "All students are present."
                    return@launch
                }

                // 4. Mark them as absent
                val now = System.currentTimeMillis()
                val isJhs = grade.toIntOrNull() in 7..10
                val academicPeriod = determineAcademicPeriod(now, isJhs) ?: "Q1" // Fallback if period not set

                var markedCount = 0
                absentees.forEach { student ->
                    val entity = AttendanceEntity(
                        studentId = student.id, 
                        timestamp = now, 
                        status = "absent", 
                        synced = false,
                        type = type,
                        subject = if (type == "subject") subjectName else null,
                        academicPeriod = academicPeriod
                    )
                    attendanceRepo.record(entity)
                    markedCount++
                }
                attendanceRepo.triggerSync()
                _message.value = "Marked $markedCount students as absent."

            } catch (e: Exception) {
                _message.value = "Error marking absentees: ${e.message}"
            }
        }
    }

    fun exportAttendance(uri: Uri, context: Context, period: String, subjectName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val schoolPeriod = schoolPeriodRepo.periodFlow.firstOrNull()
                if (schoolPeriod == null) {
                    _message.value = "Error: Academic calendar not configured."
                    return@launch
                }
                
                // Determine Date Range
                val (start, end) = when (period) {
                    "Q1" -> schoolPeriod.q1Start to schoolPeriod.q1End
                    "Q2" -> schoolPeriod.q2Start to schoolPeriod.q2End
                    "Q3" -> schoolPeriod.q3Start to schoolPeriod.q3End
                    "Q4" -> schoolPeriod.q4Start to schoolPeriod.q4End
                    "Sem1" -> schoolPeriod.shsQ1Start to schoolPeriod.shsQ2End // SHS Sem 1 (Q1+Q2) - Approximation
                    "Sem2" -> schoolPeriod.shsQ3Start to schoolPeriod.shsQ4End // SHS Sem 2 (Q3+Q4)
                    else -> 0L to System.currentTimeMillis() // "All" or unknown
                }

                // Handle "All" export for Admin
                if (subjectName.startsWith("All_")) {
                    val exportType = subjectName.removePrefix("All_") // JHS or SHS
                    val isJhs = exportType == "JHS"
                    
                    // Fetch students and sort strictly: Grade ASC, Section ASC, LastName ASC, FirstName ASC
                    val allStudents = studentRepo.getAllList()
                        .filter { 
                            val grade = it.grade.toIntOrNull()
                            if (isJhs) grade in 7..10 else (grade == 11 || grade == 12)
                        }
                        .sortedWith(
                            compareBy(
                                { it.grade.toIntOrNull() ?: Int.MAX_VALUE }, // Numeric Sort Grade
                                { it.section },
                                { it.lastName },
                                { it.firstName }
                            )
                        )

                    // Fetch Attendance Data
                    val allAttendance = if (period == "All") {
                         attendanceRepo.getAllHistory()
                    } else {
                         attendanceRepo.getAllHistoryInDateRange(start, end)
                    }
                    
                    val attendanceByStudent = allAttendance.groupBy { it.studentId }

                    val sb = StringBuilder()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    
                    // CSV Header - Well Organized
                    sb.append("Student ID,Last Name,First Name,Grade,Section,Date,Time,Status,Subject,Academic Period\n")

                    if (allStudents.isEmpty()) {
                         withContext(Dispatchers.Main) {
                            _message.value = "No students found for $exportType."
                        }
                        return@launch
                    }

                    allStudents.forEach { student ->
                        val records = attendanceByStudent[student.id] ?: emptyList()
                        
                        if (records.isNotEmpty()) {
                            // Sort records by timestamp
                            records.sortedBy { it.timestamp }.forEach { record ->
                                val date = dateFormat.format(Date(record.timestamp))
                                val time = timeFormat.format(Date(record.timestamp))
                                val subj = (record.subject ?: "Homeroom").replace(",", " ") // Escape commas
                                val lastName = student.lastName.replace(",", " ")
                                val firstName = student.firstName.replace(",", " ")
                                
                                sb.append("${student.id},\"$lastName\",\"$firstName\",${student.grade},\"${student.section}\",$date,$time,${record.status},\"$subj\",${record.academicPeriod}\n")
                            }
                        } else {
                             // Uncomment if you want to include students with no records
                             // val lastName = student.lastName.replace(",", " ")
                             // val firstName = student.firstName.replace(",", " ")
                             // sb.append("${student.id},\"$lastName\",\"$firstName\",${student.grade},\"${student.section}\",,,,No Record,,\n")
                        }
                    }

                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(sb.toString().toByteArray())
                    }

                } else {
                    // Existing logic for single subject (Teacher View)
                    val allSubjects = subjectClassRepo.getClassesForTeacher(teacherId).first() // Use teacherId here too
                    val subject = allSubjects.find { it.subjectName == subjectName }

                    if (subject == null) {
                        _message.value = "Error: Could not find subject details for export."
                        return@launch
                    }
                    
                    if (start == 0L && period != "All") {
                         _message.value = "Error: Invalid period selected."
                         return@launch
                    }

                    // Fetch all students, including archived
                    val allStudents = studentRepo.getAllByClass(subject.gradeLevel, subject.section)
                    val (archived, active) = allStudents.partition { it.isArchived }
                    
                    val attendanceList = if (period == "All") {
                         attendanceRepo.getHistoryBySubjectInDateRange(subjectName, 0L, System.currentTimeMillis())
                    } else {
                        attendanceRepo.getHistoryBySubjectInDateRange(subjectName, start, end)
                    }
                    
                    val attendanceByStudent = attendanceList.groupBy { it.studentId }

                    val sb = StringBuilder()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    
                    // Header
                    sb.append("Student ID,Student Name,Date,Time,Status,Academic Period,Notes\n")

                    // Active Students
                    active.sortedBy { it.lastName }.forEach { student ->
                        val records = attendanceByStudent[student.id] ?: emptyList()
                        val fullName = "${student.firstName} ${student.lastName}".replace(",", " ")
                        
                        if (records.isEmpty()) {
                            sb.append("${student.id},\"$fullName\",,,,No Record,\n")
                        } else {
                            records.sortedBy { it.timestamp }.forEach { record ->
                                val date = dateFormat.format(Date(record.timestamp))
                                val time = timeFormat.format(Date(record.timestamp))
                                sb.append("${student.id},\"$fullName\",$date,$time,${record.status},${record.academicPeriod},\n")
                            }
                        }
                    }
                    
                    // Spacer and header for Archived students
                    if (archived.isNotEmpty()) {
                        sb.append("\n")
                        sb.append("ARCHIVED STUDENTS,,,,,,\n")
                        sb.append("Student ID,Student Name,Date,Time,Status,Academic Period,Notes\n")
                        
                        archived.sortedBy { it.lastName }.forEach { student ->
                            val records = attendanceByStudent[student.id] ?: emptyList()
                            val fullName = "${student.firstName} ${student.lastName}".replace(",", " ")
                            
                            if (records.isEmpty()) {
                                sb.append("${student.id},\"$fullName\",,,,No Record,Archived\n")
                            } else {
                                records.sortedBy { it.timestamp }.forEach { record ->
                                    val date = dateFormat.format(Date(record.timestamp))
                                    val time = timeFormat.format(Date(record.timestamp))
                                    sb.append("${student.id},\"$fullName\",$date,$time,${record.status},${record.academicPeriod},Archived\n")
                                }
                            }
                        }
                    }

                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(sb.toString().toByteArray())
                    }
                }

                withContext(Dispatchers.Main) {
                    _message.value = "Attendance exported successfully to CSV."
                }

            } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                    _message.value = "Export failed: ${e.message}"
                }
            }
        }
    }
}

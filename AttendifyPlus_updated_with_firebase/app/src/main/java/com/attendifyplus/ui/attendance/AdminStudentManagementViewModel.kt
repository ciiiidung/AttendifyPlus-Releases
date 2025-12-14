package com.attendifyplus.ui.attendance

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendifyplus.data.local.entities.StudentEntity
import com.attendifyplus.data.local.entities.TeacherEntity
import com.attendifyplus.data.repositories.StudentRepository
import com.attendifyplus.data.repositories.SubjectClassRepository
import com.attendifyplus.data.repositories.TeacherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Year
import java.util.Locale

data class AdvisoryClassOption(
    val teacherId: String,
    val teacherName: String,
    val grade: String,
    val section: String,
    val track: String? = null
) {
    override fun toString(): String = if (track != null) "$grade - $section ($track) ($teacherName)" else "$grade - $section ($teacherName)"
}

data class StudentCounts(
    val total: Int = 0,
    val jhs: Int = 0,
    val shs: Int = 0
)

class AdminStudentManagementViewModel(
    private val studentRepo: StudentRepository,
    private val classRepo: SubjectClassRepository,
    private val teacherRepo: TeacherRepository,
    private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("school_config", Context.MODE_PRIVATE)

    // Expose raw students for detail screens that do their own filtering
    private val _allStudents = studentRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val allStudents: StateFlow<List<StudentEntity>> = _allStudents

    val allTeachers: StateFlow<List<TeacherEntity>> = teacherRepo.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGrade = MutableStateFlow("All")
    val selectedGrade: StateFlow<String> = _selectedGrade.asStateFlow()

    private val _selectedTrack = MutableStateFlow("All")
    val selectedTrack: StateFlow<String> = _selectedTrack.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    // Persistent Enabled Tracks
    private val _enabledTracks = MutableStateFlow(loadEnabledTracks())
    val enabledTracks: StateFlow<Set<String>> = _enabledTracks.asStateFlow()

    private fun loadEnabledTracks(): Set<String> {
        return prefs.getStringSet("enabled_tracks", setOf("ABM", "HUMSS", "STEM", "GAS", "AFA", "HE", "IA", "ICT", "Arts and Design", "Sports"))?.toSet() ?: emptySet()
    }

    fun clearImportStatus() {
        _importStatus.value = null
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }

    val gradeCounts: StateFlow<Map<String, Int>> = _allStudents.map { list ->
        list.groupBy { it.grade }.mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val studentCounts: StateFlow<StudentCounts> = _allStudents.map { list ->
        StudentCounts(
            total = list.size,
            jhs = list.count { it.grade.toIntOrNull()?.let { g -> g <= 10 } == true },
            shs = list.count { it.grade.toIntOrNull()?.let { g -> g > 10 } == true }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudentCounts())

    val filteredStudents: StateFlow<List<StudentEntity>> = combine(
        _allStudents, _selectedGrade, _selectedTrack, _searchQuery, allTeachers
    ) { students, grade, track, query, teachers ->
        var filtered = students

        // 1. Text Search (Global)
        if (query.isNotBlank()) {
            filtered = filtered.filter { 
                it.firstName.contains(query, ignoreCase = true) || 
                it.lastName.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true)
            }
        }

        // 2. Grade Filter
        val gradeFiltered = if (grade == "All") filtered else filtered.filter { it.grade == grade }
        
        // 3. Track Filter (for SHS)
        if ((grade == "11" || grade == "12") && track != "All") {
            // Find advisers/sections that match the selected track OR contain the track (for combined)
            val validSections = teachers.filter { 
                it.advisoryGrade == grade && 
                (it.advisoryTrack == track || it.advisoryTrack?.contains(track) == true)
            }.mapNotNull { it.advisorySection }.toSet()
            
            gradeFiltered.filter { student ->
                student.section in validSections
            }
        } else {
            gradeFiltered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val advisoryClasses: StateFlow<List<AdvisoryClassOption>> = teacherRepo.getAllFlow()
        .map { teachers ->
            teachers.filter { 
                !it.advisoryGrade.isNullOrBlank() && !it.advisorySection.isNullOrBlank() 
            }.map { teacher ->
                AdvisoryClassOption(
                    teacherId = teacher.id,
                    teacherName = "${teacher.firstName} ${teacher.lastName}",
                    grade = teacher.advisoryGrade!!,
                    section = teacher.advisorySection!!,
                    track = teacher.advisoryTrack
                )
            }.sortedBy { it.grade.toIntOrNull() ?: 999 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectGrade(grade: String) {
        _selectedGrade.value = grade
        if (grade != "11" && grade != "12") {
             _selectedTrack.value = "All"
        }
    }

    fun selectTrack(track: String) {
        _selectedTrack.value = track
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
    
    fun generateStudentId(): String {
        val year = Year.now().value % 100
        val random = (1000..9999).random()
        return String.format(Locale.US, "%02d-%04d", year, random)
    }
    
    fun addStudent(id: String, firstName: String, lastName: String, advisoryClass: AdvisoryClassOption) {
        viewModelScope.launch {
            val newStudent = StudentEntity(
                id = id, 
                firstName = firstName, 
                lastName = lastName, 
                grade = advisoryClass.grade, 
                section = advisoryClass.section,
                username = firstName
            )
            studentRepo.insert(newStudent)
        }
    }

    fun updateStudent(student: StudentEntity) {
        viewModelScope.launch {
            studentRepo.update(student)
        }
    }

    fun archiveStudent(student: StudentEntity) {
        viewModelScope.launch {
            studentRepo.archive(student.id)
        }
    }

    fun toggleTrack(track: String, enabled: Boolean) {
        val current = _enabledTracks.value.toMutableSet()
        if (enabled) current.add(track) else current.remove(track)
        _enabledTracks.value = current
        prefs.edit().putStringSet("enabled_tracks", current).apply()
    }

    fun assignAdviser(teacher: TeacherEntity, grade: String, section: String, track: String? = null) {
        viewModelScope.launch {
            val teachers = teacherRepo.getAllFlow().first()
            val existingAdviser = teachers.find { 
                it.advisoryGrade == grade && it.advisorySection == section && it.id != teacher.id 
            }
            
            if (existingAdviser != null) {
                val cleared = existingAdviser.copy(
                    advisoryGrade = null, 
                    advisorySection = null, 
                    advisoryTrack = null,
                    role = "subject"
                )
                teacherRepo.update(cleared)
            }

            val updated = teacher.copy(
                advisoryGrade = grade, 
                advisorySection = section, 
                advisoryTrack = track,
                role = "adviser"
            )
            teacherRepo.update(updated)
        }
    }
    
    fun updateSection(
        oldAdviserId: String,
        grade: String,
        oldSectionName: String,
        newAdviser: TeacherEntity,
        newSectionName: String,
        newTrack: String?
    ) {
        viewModelScope.launch {
            if (oldAdviserId != newAdviser.id) {
                val teachers = teacherRepo.getAllFlow().first()
                val oldAdviser = teachers.find { it.id == oldAdviserId }
                oldAdviser?.let {
                    val cleared = it.copy(
                        advisoryGrade = null, 
                        advisorySection = null, 
                        advisoryTrack = null,
                        role = "subject"
                    )
                    teacherRepo.update(cleared)
                }
            }

            val updatedAdviser = newAdviser.copy(
                advisoryGrade = grade,
                advisorySection = newSectionName,
                advisoryTrack = newTrack,
                role = "adviser"
            )
            teacherRepo.update(updatedAdviser)

            if (oldSectionName != newSectionName) {
                val students = _allStudents.value
                val studentsInOldSection = students.filter { it.grade == grade && it.section == oldSectionName }
                
                studentsInOldSection.forEach { student ->
                    studentRepo.update(student.copy(section = newSectionName))
                }
            }
        }
    }
    
    fun removeAdviser(teacher: TeacherEntity) {
        viewModelScope.launch {
            val updated = teacher.copy(
                advisoryGrade = null, 
                advisorySection = null, 
                advisoryTrack = null,
                role = "subject"
            )
            teacherRepo.update(updated)
        }
    }

    // Function to export arbitrary list of students to CSV (Used by Admin Dashboard quick action)
    fun exportStudentsCsv(context: Context, students: List<StudentEntity>) {
        if (students.isEmpty()) {
             _exportStatus.value = "Error: No students to export."
             return
        }
        
        val csvHeader = "Student ID,First Name,Last Name,Grade,Section,Username\n"
        val csvBody = students.sortedWith(compareBy({ it.grade.toIntOrNull() ?: 99 }, { it.section }, { it.lastName }))
            .joinToString("\n") { 
                "${it.id},${it.firstName},${it.lastName},${it.grade},${it.section},${it.username ?: it.id}" 
            }
        val csvContent = csvHeader + csvBody
        
        try {
            val filename = "students_export_${System.currentTimeMillis()}.csv"
            val file = java.io.File(context.cacheDir, filename)
            file.writeText(csvContent)
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, 
                "${context.packageName}.provider", 
                file
            )
            
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = android.content.Intent.createChooser(intent, "Export Student List")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            
            _exportStatus.value = "Success: CSV created and shared."
        } catch (e: Exception) {
            e.printStackTrace()
            _exportStatus.value = "Export Failed: ${e.message}"
        }
    }

    fun exportStudents(uri: Uri, context: Context, filter: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allStudents = _allStudents.value
                val studentsToExport = when {
                    filter == "All_JHS" -> allStudents.filter { it.grade.toIntOrNull()?.let { g -> g <= 10 } == true }
                    filter == "All_SHS" -> allStudents.filter { it.grade.toIntOrNull()?.let { g -> g > 10 } == true }
                    else -> {
                        val parts = filter.split("-", limit = 2)
                        if (parts.size == 2) {
                            val grade = parts[0]
                            val section = parts[1]
                            allStudents.filter { it.grade == grade && it.section == section }
                        } else {
                            emptyList()
                        }
                    }
                }

                if (studentsToExport.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _exportStatus.value = "Error: No students found to export."
                    }
                    return@launch
                }

                val csvHeader = "Student ID,First Name,Last Name,Grade,Section,Username\n"
                val csvBody = studentsToExport.sortedWith(compareBy({ it.grade.toIntOrNull() ?: 99 }, { it.section }, { it.lastName }))
                    .joinToString("\n") {
                        "${it.id},${it.firstName},${it.lastName},${it.grade},${it.section},${it.username ?: it.id}"
                    }
                val csvContent = csvHeader + csvBody

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }
                
                withContext(Dispatchers.Main) {
                    _exportStatus.value = "Success: Exported ${studentsToExport.size} students."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                 withContext(Dispatchers.Main) {
                    _exportStatus.value = "Export Failed: ${e.message}"
                }
            }
        }
    }

    fun importStudentsFromCsv(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _importStatus.value = "Importing..."
            }
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val studentsToInsert = mutableListOf<StudentEntity>()
                val usedRandoms = mutableSetOf<Int>()

                reader.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val trimmedLine = line.trim()
                        if (index == 0 && (trimmedLine.contains("First Name", ignoreCase = true) || trimmedLine.contains("Grade", ignoreCase = true))) {
                            return@forEachIndexed
                        }
                        if (trimmedLine.isBlank()) return@forEachIndexed

                        val tokens = trimmedLine.split(",")
                        if (tokens.size >= 4) {
                            val firstName = tokens[0].trim()
                            val lastName = tokens[1].trim()
                            val grade = tokens[2].trim()
                            val section = tokens[3].trim()

                            if (firstName.isNotBlank() && lastName.isNotBlank() && grade.isNotBlank() && section.isNotBlank()) {
                                var randomPart = (1000..9999).random()
                                while (usedRandoms.contains(randomPart)) {
                                    randomPart = (1000..9999).random()
                                }
                                usedRandoms.add(randomPart)
                                
                                val year = Year.now().value % 100
                                val newId = String.format(Locale.US, "%02d-%04d", year, randomPart)

                                val student = StudentEntity(
                                    id = newId,
                                    firstName = firstName,
                                    lastName = lastName,
                                    grade = grade,
                                    section = section,
                                    username = firstName
                                )
                                studentsToInsert.add(student)
                            }
                        }
                    }
                }

                if (studentsToInsert.isNotEmpty()) {
                    studentRepo.insertAll(studentsToInsert)
                    withContext(Dispatchers.Main) {
                         _importStatus.value = "Success: Imported ${studentsToInsert.size} students."
                    }
                } else {
                    withContext(Dispatchers.Main) {
                         _importStatus.value = "No valid student records found in CSV."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _importStatus.value = "Import Failed: ${e.message}"
                }
            }
        }
    }
}

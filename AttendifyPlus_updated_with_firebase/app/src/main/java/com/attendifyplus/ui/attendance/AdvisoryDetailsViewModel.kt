package com.attendifyplus.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendifyplus.data.local.entities.TeacherEntity
import com.attendifyplus.data.repositories.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdvisoryDetailsViewModel(
    private val teacherRepo: TeacherRepository,
    private val teacherId: String = "T001" // TODO: Get from Session
) : ViewModel() {

    private val _teacher = MutableStateFlow<TeacherEntity?>(null)
    val teacher: StateFlow<TeacherEntity?> = _teacher

    init {
        viewModelScope.launch {
            teacherRepo.getByIdFlow(teacherId).collect { 
                _teacher.value = it
            }
        }
    }

    fun saveDetails(grade: String, section: String, startTime: String?) {
        val currentTeacher = _teacher.value
        
        val updatedTeacher = if (currentTeacher != null) {
            currentTeacher.copy(
                advisoryGrade = grade,
                advisorySection = section,
                advisoryStartTime = startTime,
                role = "adviser" // Always set to adviser when they have advisory class
            )
        } else {
            // This case should ideally not happen if the UI is driven by an existing teacher.
            // But as a fallback, create a new entity. This needs more details for a real user.
            TeacherEntity(
                id = teacherId,
                username = "new.teacher",
                password = "123456", // Default password
                firstName = "New",
                lastName = "Teacher",
                email = null,
                role = "adviser",
                advisoryGrade = grade,
                advisorySection = section,
                advisoryStartTime = startTime
            )
        }
        
        viewModelScope.launch {
            teacherRepo.insert(updatedTeacher)
        }
    }

    fun deleteClass() {
        _teacher.value?.let {
            val updated = it.copy(
                advisoryGrade = null,
                advisorySection = null,
                advisoryStartTime = null,
                // Revert role to subject teacher if they no longer advise a class
                role = "subject" 
            )
            viewModelScope.launch {
                teacherRepo.insert(updated)
            }
        }
    }
}

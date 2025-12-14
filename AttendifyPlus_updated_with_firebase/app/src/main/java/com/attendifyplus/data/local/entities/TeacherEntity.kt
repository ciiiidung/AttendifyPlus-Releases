package com.attendifyplus.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "teachers",
    indices = [Index(value = ["username"], unique = true)]
)
data class TeacherEntity(
    @PrimaryKey val id: String = "",
    val username: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    var role: String = "teacher", // admin, teacher, student
    var department: String? = null, // JHS or SHS, only for advisers
    var advisoryGrade: String? = null,
    var advisorySection: String? = null,
    var advisoryStartTime: String? = null,
    var advisoryTrack: String? = null,
    var hasChangedCredentials: Boolean = false
)

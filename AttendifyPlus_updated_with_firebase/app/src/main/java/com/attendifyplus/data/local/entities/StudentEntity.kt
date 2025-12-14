package com.attendifyplus.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val grade: String = "",
    val section: String = "",
    // Feature 2 & 3: Credential Management
    val username: String? = null, // Nullable for backward compatibility, defaults to ID if null
    val password: String? = null, // Nullable, defaults to "123456" if null
    val hasChangedCredentials: Boolean = false,
    // New Feature: Archive
    val isArchived: Boolean = false
)

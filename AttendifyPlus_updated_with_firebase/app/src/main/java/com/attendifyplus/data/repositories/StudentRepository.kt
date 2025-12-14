package com.attendifyplus.data.repositories

import com.attendifyplus.data.local.dao.StudentDao
import com.attendifyplus.data.local.entities.StudentEntity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class StudentRepository(private val dao: StudentDao) {
    
    private val dbRef = FirebaseDatabase.getInstance().getReference("students")

    fun getAll(): Flow<List<StudentEntity>> = dao.getAll()
    // Helper to get List synchronously (suspending)
    suspend fun getAllList(): List<StudentEntity> = dao.getAll().first()
    
    suspend fun getById(id: String): StudentEntity? {
        val local = dao.getById(id)
        if (local != null) return local
        
        try {
            val snapshot = dbRef.child(id).get().await()
            if (snapshot.exists()) {
                val remote = snapshot.getValue(StudentEntity::class.java)
                if (remote != null) {
                    dao.insert(remote)
                    return remote
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch student by ID from Firebase")
        }
        return null
    }

    suspend fun getByUsername(username: String): StudentEntity? {
        val local = dao.getByUsername(username)
        if (local != null) return local
        
        // Fallback to Firebase
        try {
             val snapshot = dbRef.orderByChild("username").equalTo(username).get().await()
             if (snapshot.exists()) {
                 for (child in snapshot.children) {
                     val remote = child.getValue(StudentEntity::class.java)
                     if (remote != null) {
                         dao.insert(remote)
                         return remote
                     }
                 }
             }
        } catch (e: Exception) {
             Timber.e(e, "Failed to fetch student by username from Firebase")
        }
        return null
    }

    suspend fun findByLogin(login: String): StudentEntity? {
        val local = dao.findByLogin(login)
        if (local != null) return local

        // Fallback to Firebase.
        
        // 1. Try by ID First (Wrap in its own try-catch to prevent invalid path errors)
        try {
            // Firebase keys cannot contain '.', '#', '$', '[', or ']'.
            // If login contains these, it's definitely not an ID key, so we skip to username check.
            if (!login.contains(".") && !login.contains("#") && !login.contains("$") && !login.contains("[") && !login.contains("]")) {
                val idSnapshot = dbRef.child(login).get().await()
                if (idSnapshot.exists()) {
                    val remote = idSnapshot.getValue(StudentEntity::class.java)
                    if (remote != null) {
                        dao.insert(remote)
                        return remote
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Lookup by ID failed or invalid format, proceeding to username check.")
        }

        // 2. Try by Username
        try {
            val userSnapshot = dbRef.orderByChild("username").equalTo(login).get().await()
            if (userSnapshot.exists()) {
                 for (child in userSnapshot.children) {
                     val remote = child.getValue(StudentEntity::class.java)
                     if (remote != null) {
                         dao.insert(remote)
                         return remote
                     }
                 }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to find student by login (username search) from Firebase")
        }

        return null
    }

    fun getByClass(grade: String, section: String): Flow<List<StudentEntity>> = dao.getByClass(grade, section)
    
    // New method for synchronous list fetching
    suspend fun getByClassList(grade: String, section: String): List<StudentEntity> = dao.getByClassList(grade, section)

    // Get all students in a class, including archived
    suspend fun getAllByClass(grade: String, section: String): List<StudentEntity> = dao.getAllByClass(grade, section)

    // Archive methods
    fun getArchivedStudents(): Flow<List<StudentEntity>> = dao.getArchivedStudents()
    
    suspend fun archive(id: String) {
        dao.archive(id)
        try {
            dbRef.child(id).child("isArchived").setValue(true).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to archive student in Firebase")
        }
    }

    suspend fun restore(id: String) {
        dao.restore(id)
        try {
            dbRef.child(id).child("isArchived").setValue(false).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore student in Firebase")
        }
    }

    suspend fun insert(student: StudentEntity) {
        dao.insert(student)
        try {
            dbRef.child(student.id).setValue(student).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert student to Firebase")
        }
    }

    suspend fun insertAll(students: List<StudentEntity>) {
        dao.insertAll(students)
        try {
            val updates = students.associate { "/${it.id}" to it }
            dbRef.updateChildren(updates).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to bulk insert students to Firebase")
        }
    }

    suspend fun update(student: StudentEntity) {
        dao.update(student)
         try {
            dbRef.child(student.id).setValue(student).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update student in Firebase")
        }
    }

    suspend fun updateCredentials(studentId: String, username: String, password: String) {
        dao.updateCredentials(studentId, username, password)
        try {
            val updates = mapOf(
                "username" to username,
                "password" to password,
                "hasChangedCredentials" to true
            )
            dbRef.child(studentId).updateChildren(updates).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update student credentials in Firebase")
        }
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        try {
            dbRef.child(id).removeValue().await()
        } catch (e: Exception) {
             Timber.e(e, "Failed to delete student from Firebase")
        }
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        // Optional: clear remote
        // dbRef.removeValue().await()
    }

    suspend fun countByClass(grade: String, section: String) = dao.countByClass(grade, section)
}

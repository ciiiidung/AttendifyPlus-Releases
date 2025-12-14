package com.attendifyplus.data.repositories

import com.attendifyplus.data.local.dao.TeacherDao
import com.attendifyplus.data.local.entities.TeacherEntity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class TeacherRepository(private val dao: TeacherDao) {
    
    private val dbRef = FirebaseDatabase.getInstance().getReference("teachers")

    suspend fun getById(id: String): TeacherEntity? {
        val local = dao.getById(id)
        if (local != null) return local
        
        // Fetch from Firebase if not local
        try {
            val snapshot = dbRef.child(id).get().await()
            if (snapshot.exists()) {
                val remote = snapshot.getValue(TeacherEntity::class.java)
                if (remote != null) {
                    dao.insert(remote)
                    return remote
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch teacher from Firebase")
        }
        return null
    }

    fun getByIdFlow(id: String): Flow<TeacherEntity?> = dao.getByIdFlow(id)

    suspend fun getByUsername(username: String): TeacherEntity? {
        val local = dao.getByUsername(username)
        if (local != null) return local

        // Fetch from Firebase
        try {
            val snapshot = dbRef.orderByChild("username").equalTo(username).get().await()
            if (snapshot.exists()) {
                // There should be only one match due to unique constraint, but we take first
                for (child in snapshot.children) {
                    val remote = child.getValue(TeacherEntity::class.java)
                    if (remote != null) {
                        dao.insert(remote)
                        return remote
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch teacher by username from Firebase")
        }
        return null
    }

    suspend fun insert(teacher: TeacherEntity) {
        dao.insert(teacher)
        try {
            dbRef.child(teacher.id).setValue(teacher).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync teacher to Firebase")
        }
    }

    suspend fun getAll() = dao.getAll()
    
    fun getAllFlow() = dao.getAllFlow()

    suspend fun update(teacher: TeacherEntity) {
        dao.update(teacher)
        try {
            dbRef.child(teacher.id).setValue(teacher).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update teacher in Firebase")
        }
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        try {
            dbRef.child(id).removeValue().await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete teacher from Firebase")
        }
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        // We probably don't want to wipe remote DB on local delete all unless specified
        // dbRef.removeValue().await() 
    }

    suspend fun updateAdvisoryDetails(teacherId: String, grade: String?, section: String?, startTime: String?) {
        dao.updateAdvisoryDetails(teacherId, grade, section, startTime)
        // Also update remote. We need to fetch, update object, push back.
        // Or partial update map
        try {
            val updates = mapOf(
                "advisoryGrade" to grade,
                "advisorySection" to section,
                "advisoryStartTime" to startTime,
                "role" to if (grade != null) "adviser" else "subject"
            )
            dbRef.child(teacherId).updateChildren(updates).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update advisory details in Firebase")
        }
    }

    suspend fun updateCredentials(teacherId: String, username: String, password: String) {
        dao.updateCredentials(teacherId, username, password)
        try {
             val updates = mapOf(
                "username" to username,
                "password" to password,
                "hasChangedCredentials" to true
            )
            dbRef.child(teacherId).updateChildren(updates).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update credentials in Firebase")
        }
    }
}

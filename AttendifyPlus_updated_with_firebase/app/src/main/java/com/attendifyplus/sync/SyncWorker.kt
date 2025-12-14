package com.attendifyplus.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attendifyplus.data.local.entities.AttendanceEntity
import com.attendifyplus.data.local.entities.SchoolPeriodEntity
import com.attendifyplus.data.local.entities.StudentEntity
import com.attendifyplus.data.local.entities.TeacherEntity
import com.attendifyplus.data.repositories.AttendanceRepository
import com.attendifyplus.data.repositories.SchoolPeriodRepository
import com.attendifyplus.data.repositories.StudentRepository
import com.attendifyplus.data.repositories.TeacherRepository
import com.attendifyplus.util.NotificationHelper
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Two-way sync with Firebase Realtime Database.
 * Supports Teachers, Students, Attendance, and Config.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params), KoinComponent {
    
    private val attendanceRepo: AttendanceRepository by inject()
    private val studentRepo: StudentRepository by inject()
    private val teacherRepo: TeacherRepository by inject()
    private val periodRepo: SchoolPeriodRepository by inject()
    private val notificationHelper: NotificationHelper by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        notificationHelper.showSyncNotification("Sync Started", "Synchronizing data...")
        try {
            val db = FirebaseDatabase.getInstance().reference
            val attendanceRef = db.child("attendance")
            val studentsRef = db.child("students")
            val teachersRef = db.child("teachers")
            val configRef = db.child("config")

            // --- 1. SYNC TEACHERS ---
            // Push all local teachers to Remote (Force Push)
            val localTeachers = teacherRepo.getAll()
            if (localTeachers.isNotEmpty()) {
                val teachersMap = localTeachers.associate { it.id to it }
                teachersRef.updateChildren(teachersMap).await()
            }
            
            // Pull Remote Teachers
            val teachersSnap = teachersRef.get().await()
            if (teachersSnap.exists()) {
                 for (child in teachersSnap.children) {
                     val remote = child.getValue(TeacherEntity::class.java)
                     if (remote != null) {
                         teacherRepo.insert(remote)
                     }
                 }
            }

            // --- 2. SYNC STUDENTS ---
            // Push all local students to Remote (Force Push)
            val localStudents = studentRepo.getAllList()
            if (localStudents.isNotEmpty()) {
                 val studentsMap = localStudents.associate { it.id to it }
                 studentsRef.updateChildren(studentsMap).await()
            }

            // Pull Remote Students
            val studentsSnap = studentsRef.get().await()
            if (studentsSnap.exists()) {
                val remoteStudents = mutableListOf<StudentEntity>()
                for (child in studentsSnap.children) {
                    val remote = child.getValue(StudentEntity::class.java)
                    if (remote != null) {
                        remoteStudents.add(remote)
                    }
                }
                if (remoteStudents.isNotEmpty()) {
                    studentRepo.insertAll(remoteStudents)
                }
            }

            // --- 3. SYNC ATTENDANCE ---
            // Push Unsynced Local
            val unsynced = attendanceRepo.getUnsynced()
            for (local in unsynced) {
                // We use push() for attendance to avoid overwriting issues with multiple devices
                // Ideally, we should check duplication.
                val map = mapOf(
                    "studentId" to local.studentId,
                    "timestamp" to local.timestamp,
                    "status" to local.status,
                    "type" to local.type,
                    "subject" to (local.subject ?: ""),
                    "updatedAt" to System.currentTimeMillis(),
                    "deviceId" to android.os.Build.MODEL
                )
                // Use a composite key or just push
                attendanceRef.push().setValue(map).await()
                attendanceRepo.markSynced(listOf(local.id))
            }

            // Pull Remote Attendance
            val snapshot = attendanceRef.get().await()
            if (snapshot.exists()) {
                var pulledCount = 0
                for (child in snapshot.children) {
                    val remote = child.value as? Map<String, Any> ?: continue
                    val studentId = remote["studentId"] as? String ?: continue
                    val timestamp = (remote["timestamp"] as? Number)?.toLong() ?: continue
                    val status = remote["status"] as? String ?: "present"
                    val type = remote["type"] as? String ?: "homeroom"
                    val subjectRaw = remote["subject"] as? String ?: ""
                    val subject = if (subjectRaw.isBlank()) null else subjectRaw
                    
                    if (!attendanceRepo.exists(studentId, timestamp)) {
                        attendanceRepo.record(
                            AttendanceEntity(
                                studentId = studentId,
                                timestamp = timestamp,
                                status = status,
                                type = type,
                                subject = subject,
                                synced = true 
                            )
                        )
                        pulledCount++
                    }
                }
                Timber.d("Synced $pulledCount attendance records from remote.")
            }

            // --- 4. SYNC SCHOOL PERIOD CONFIG ---
            val localPeriod = periodRepo.getPeriod()
            
            // Push Local if exists
            if (localPeriod != null) {
                val periodMap = mapOf(
                    "schoolYear" to localPeriod.schoolYear,
                    "q1Start" to localPeriod.q1Start, "q1End" to localPeriod.q1End,
                    "q2Start" to localPeriod.q2Start, "q2End" to localPeriod.q2End,
                    "q3Start" to localPeriod.q3Start, "q3End" to localPeriod.q3End,
                    "q4Start" to localPeriod.q4Start, "q4End" to localPeriod.q4End,
                    "updatedAt" to System.currentTimeMillis()
                )
                configRef.child("schoolPeriod").setValue(periodMap).await()
            }

            // Pull Remote Config
            val remoteConfigSnap = configRef.child("schoolPeriod").get().await()
            if (remoteConfigSnap.exists()) {
                val remote = remoteConfigSnap.value as? Map<String, Any>
                if (remote != null) {
                    val year = remote["schoolYear"] as? String ?: ""
                    val q1s = (remote["q1Start"] as? Number)?.toLong() ?: 0L
                    val q1e = (remote["q1End"] as? Number)?.toLong() ?: 0L
                    val q2s = (remote["q2Start"] as? Number)?.toLong() ?: 0L
                    val q2e = (remote["q2End"] as? Number)?.toLong() ?: 0L
                    val q3s = (remote["q3Start"] as? Number)?.toLong() ?: 0L
                    val q3e = (remote["q3End"] as? Number)?.toLong() ?: 0L
                    val q4s = (remote["q4Start"] as? Number)?.toLong() ?: 0L
                    val q4e = (remote["q4End"] as? Number)?.toLong() ?: 0L
                    
                    val newEntity = SchoolPeriodEntity(
                        id = 1,
                        schoolYear = year,
                        q1Start = q1s, q1End = q1e,
                        q2Start = q2s, q2End = q2e,
                        q3Start = q3s, q3End = q3e,
                        q4Start = q4s, q4End = q4e,
                        synced = true
                    )
                    periodRepo.insert(newEntity)
                }
            }

            // Update last sync timestamp
            attendanceRepo.updateLastSyncTimestamp()

            notificationHelper.showSyncNotification("Sync Completed", "All data is up to date.")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e)
            notificationHelper.showSyncNotification("Sync Failed", "Could not sync data. Check connection.", isError = true)
            Result.retry()
        }
    }
}

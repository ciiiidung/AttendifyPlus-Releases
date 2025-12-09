package com.attendifyplus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.attendifyplus.data.local.dao.AttendanceDao
import com.attendifyplus.data.local.dao.SchoolCalendarConfigDao
import com.attendifyplus.data.local.dao.SchoolEventDao
import com.attendifyplus.data.local.dao.SchoolPeriodDao
import com.attendifyplus.data.local.dao.StudentDao
import com.attendifyplus.data.local.dao.SubjectClassDao
import com.attendifyplus.data.local.dao.TeacherDao
import com.attendifyplus.data.local.dao.AdminSubjectDao
import com.attendifyplus.data.local.entities.AttendanceEntity
import com.attendifyplus.data.local.entities.SchoolCalendarConfigEntity
import com.attendifyplus.data.local.entities.SchoolEventEntity
import com.attendifyplus.data.local.entities.SchoolPeriodEntity
import com.attendifyplus.data.local.entities.StudentEntity
import com.attendifyplus.data.local.entities.SubjectClassEntity
import com.attendifyplus.data.local.entities.TeacherEntity
import com.attendifyplus.data.local.entities.AdminSubjectEntity

@Database(
    entities = [
        StudentEntity::class, 
        AttendanceEntity::class, 
        TeacherEntity::class, 
        SubjectClassEntity::class,
        SchoolEventEntity::class,
        SchoolPeriodEntity::class,
        SchoolCalendarConfigEntity::class,
        AdminSubjectEntity::class
    ], 
    version = 22, 
    exportSchema = false
)
abstract class AttendifyDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun teacherDao(): TeacherDao
    abstract fun subjectClassDao(): SubjectClassDao
    abstract fun schoolEventDao(): SchoolEventDao
    abstract fun schoolPeriodDao(): SchoolPeriodDao
    abstract fun schoolCalendarConfigDao(): SchoolCalendarConfigDao
    abstract fun adminSubjectDao(): AdminSubjectDao
}

package com.attendifyplus.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.attendifyplus.data.local.entities.TeacherEntity
import com.attendifyplus.ui.theme.Dimens
import com.attendifyplus.ui.theme.PillShape
import com.attendifyplus.ui.theme.PrimaryBlue
import com.attendifyplus.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DailyStatusCard(
    selectedDate: Calendar,
    onDateSelected: (Calendar) -> Unit,
    dailyStatus: String,
    dailyStatusOptions: List<String>,
    onSave: (Calendar, String) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()) }
    
    var isEditing by remember { mutableStateOf(false) }
    var currentSelection by remember { mutableStateOf(dailyStatus) }
    
    // Sync currentSelection with dailyStatus when not editing or when dailyStatus updates
    LaunchedEffect(dailyStatus) {
        if (!isEditing) {
            currentSelection = dailyStatus
        }
    }

    // "Reason" Dialog State
    var showReasonDialog by remember { mutableStateOf(false) }
    var reasonText by remember { mutableStateOf("") }

    if (showReasonDialog) {
        Dialog(onDismissRequest = { showReasonDialog = false }) {
            Card(
                shape = RoundedCornerShape(Dimens.CornerRadiusLarge),
                elevation = 8.dp,
                modifier = Modifier.padding(Dimens.PaddingMedium),
                backgroundColor = Color.White
            ) {
                Column(modifier = Modifier.padding(Dimens.PaddingMedium)) {
                    Text(
                        text = "Reason Required",
                        style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(Dimens.PaddingMedium))
                    
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Enter Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(Dimens.PaddingLarge))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showReasonDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(Modifier.width(Dimens.PaddingSmall))
                        Button(
                            onClick = { 
                                if (reasonText.isNotBlank()) {
                                    currentSelection = "$currentSelection ($reasonText)"
                                    showReasonDialog = false
                                    reasonText = ""
                                }
                            },
                            enabled = reasonText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryBlue)
                        ) {
                            Text("Confirm", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Status Color Logic
    val statusColor = when {
        dailyStatus.contains("Class Day", ignoreCase = true) -> SuccessGreen
        dailyStatus.contains("Suspended", ignoreCase = true) -> Color.Red
        dailyStatus.contains("Cancelled", ignoreCase = true) -> Color.Red
        dailyStatus.contains("No Class", ignoreCase = true) -> Color.Red
        dailyStatus.contains("Holiday", ignoreCase = true) -> PrimaryBlue
        else -> Color.Gray
    }

    Card(
        shape = RoundedCornerShape(Dimens.CornerRadiusLarge),
        elevation = Dimens.CardElevation,
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Schedule Status",
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold, color = PrimaryBlue)
                )
                if (!isEditing) {
                    IconButton(onClick = { 
                        isEditing = true 
                        currentSelection = dailyStatus
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Status", tint = PrimaryBlue)
                    }
                }
            }
            
            Spacer(Modifier.height(Dimens.PaddingSmall))

            // Date Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val year = selectedDate.get(Calendar.YEAR)
                        val month = selectedDate.get(Calendar.MONTH)
                        val day = selectedDate.get(Calendar.DAY_OF_MONTH)
                        DatePickerDialog(context, { _, selectedYear, selectedMonth, selectedDay ->
                            val newCalendar = Calendar.getInstance()
                            newCalendar.set(selectedYear, selectedMonth, selectedDay)
                            onDateSelected(newCalendar)
                        }, year, month, day).show()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormatter.format(selectedDate.time),
                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.width(Dimens.PaddingSmall))
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            }
            
            Spacer(Modifier.height(Dimens.PaddingMedium))

            if (isEditing) {
                var showStatusMenu by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showStatusMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.CornerRadiusMedium),
                        border = BorderStroke(1.dp, Color.LightGray),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = currentSelection, style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        dailyStatusOptions.forEach { status ->
                            DropdownMenuItem(onClick = {
                                showStatusMenu = false
                                currentSelection = status
                                if (status == "No Class" || status == "Suspended") {
                                    showReasonDialog = true
                                }
                            }) {
                                Text(status)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.PaddingMedium))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = { isEditing = false },
                        shape = PillShape
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(Dimens.PaddingSmall))
                    Button(
                        onClick = {
                            onSave(selectedDate, currentSelection)
                            isEditing = false
                        },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryBlue)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            } else {
                // View Mode - Colored Text
                Text(
                    text = dailyStatus,
                    style = MaterialTheme.typography.h5.copy(fontWeight = FontWeight.Bold, color = statusColor),
                    modifier = Modifier.padding(vertical = Dimens.PaddingSmall)
                )
            }
        }
    }
}

@Composable
fun CreatedClassCard(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    onEditSettings: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(Dimens.CornerRadiusExtraLarge),
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.CreatedClassCardHeight)
            .clickable(onClick = onClick),
        backgroundColor = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.PaddingMedium) // Should be close to 12.dp, using Medium (16) or custom
            ) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.LightGray)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(onClick = {
                        showMenu = false
                        onEditSettings()
                    }) {
                        Text("Edit / Delete Class")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.PaddingLarge),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = color.copy(alpha = 0.08f),
                    modifier = Modifier.size(Dimens.ClassIconSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(Dimens.IconSizeLarge)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

                Text(
                    text = title,
                    style = MaterialTheme.typography.h5.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF2D3436)
                )

                Spacer(modifier = Modifier.height(Dimens.PaddingTiny))

                Surface(
                    shape = CircleShape,
                    color = Color.LightGray.copy(alpha = 0.2f),
                    modifier = Modifier.padding(top = Dimens.PaddingTiny)
                ) {
                    Text(
                        text = "$count Students Enrolled",
                        style = MaterialTheme.typography.caption.copy(
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = Dimens.PaddingTiny),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TeacherTab(title: String, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (selected) PrimaryBlue else Color.Transparent
    val contentColor = if (selected) Color.White else Color.Gray
    val border = if (selected) null else BorderStroke(1.dp, Color.LightGray)

    Surface(
        shape = PillShape,
        color = backgroundColor,
        border = border,
        modifier = Modifier.height(32.dp).clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = Dimens.PaddingMedium)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = contentColor)
            )
        }
    }
}

@Composable
fun TeacherFullWidthCard(teacher: TeacherEntity, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Dimens.CornerRadiusMedium),
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.PaddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if(teacher.department == "SHS") Color(0xFFFF9F43).copy(alpha = 0.1f) else PrimaryBlue.copy(alpha = 0.1f),
                modifier = Modifier.size(Dimens.TeacherImageSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = teacher.firstName.take(1).uppercase(),
                        style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.Bold, 
                            color = if(teacher.department == "SHS") Color(0xFFFF9F43) else PrimaryBlue
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${teacher.firstName} ${teacher.lastName}",
                        style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.width(Dimens.PaddingSmall))
                    // Badge for JHS/SHS
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (teacher.department == "SHS") Color(0xFFFF9F43).copy(alpha = 0.2f) else PrimaryBlue.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = teacher.department ?: "",
                            style = MaterialTheme.typography.caption.copy(
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold,
                                color = if (teacher.department == "SHS") Color(0xFFE58E26) else PrimaryBlue
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = Dimens.PaddingMicro)
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.PaddingTiny))
                Text(
                    text = teacher.email ?: "",
                    style = MaterialTheme.typography.body2.copy(color = Color.Gray)
                )
                if (!teacher.advisoryGrade.isNullOrBlank()) {
                    Spacer(Modifier.height(Dimens.PaddingMicro))
                    Text(
                        text = "Adviser: ${teacher.advisoryGrade} - ${teacher.advisorySection}",
                        style = MaterialTheme.typography.caption.copy(color = if(teacher.department == "SHS") Color(0xFFE58E26) else PrimaryBlue, fontWeight = FontWeight.Medium)
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun TeacherCompactCard(teacher: TeacherEntity) {
    // Kept for backward compatibility or other uses, but Dashboard now uses TeacherFullWidthCard
    TeacherFullWidthCard(teacher = teacher, onClick = {})
}

@Composable
fun CompactSubjectCard(
    subjectName: String,
    gradeSection: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(Dimens.CornerRadiusLarge),
        elevation = 2.dp,
        backgroundColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.CompactCardHeight)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.PaddingMedium)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryBlue.copy(alpha = 0.1f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = subjectName.take(1).uppercase(),
                            style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
            }
            
            Column {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = gradeSection,
                    style = MaterialTheme.typography.caption.copy(color = Color.Gray)
                )
            }
        }
    }
}

@Composable
fun EmptySubjectState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.School, // Need to ensure this icon is available or imported
            contentDescription = null,
            tint = Color.LightGray,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(Dimens.PaddingMedium))
        Text(
            text = "No subjects created yet",
            style = MaterialTheme.typography.h6.copy(color = Color.Gray)
        )
        Spacer(Modifier.height(Dimens.PaddingLarge))
        Button(
            onClick = onCreate,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryBlue),
            modifier = Modifier.height(Dimens.ButtonHeight)
        ) {
            Text("Create First Subject", color = Color.White)
        }
    }
}

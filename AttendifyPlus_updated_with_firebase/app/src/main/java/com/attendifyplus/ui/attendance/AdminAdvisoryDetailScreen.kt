package com.attendifyplus.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.attendifyplus.data.local.entities.StudentEntity
import com.attendifyplus.ui.theme.PillShape
import com.attendifyplus.ui.theme.PrimaryBlue
import com.attendifyplus.ui.theme.SecondaryTeal
import org.koin.androidx.compose.getViewModel
import java.net.URLDecoder

@Composable
fun AdminAdvisoryDetailScreen(
    navController: NavController,
    grade: String,
    section: String,
    viewModel: AdminStudentManagementViewModel = getViewModel()
) {
    SetSystemBarIcons(useDarkIcons = false) // White icons for dark header
    
    // Decode URL-encoded section name
    val decodedSection = remember(section) { URLDecoder.decode(section, "UTF-8") }

    // Use allStudents directly and filter locally to avoid modifying global VM state
    val allStudents by viewModel.allStudents.collectAsState()
    val advisoryClasses by viewModel.advisoryClasses.collectAsState()
    
    // Local Search Query
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter for this specific section + Search
    val classStudents = remember(allStudents, decodedSection, searchQuery) {
        allStudents.filter { student -> 
            student.grade == grade && 
            student.section == decodedSection &&
            (searchQuery.isBlank() || 
             student.firstName.contains(searchQuery, ignoreCase = true) || 
             student.lastName.contains(searchQuery, ignoreCase = true) ||
             student.id.contains(searchQuery, ignoreCase = true))
        }
    }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }
    
    // Helper to get advisory class info
    val currentAdvisoryClass = advisoryClasses.find { it.grade == grade && it.section == decodedSection }
    val availableAdvisoryClasses = if (currentAdvisoryClass != null) listOf(currentAdvisoryClass) else emptyList()
    val adviserName = currentAdvisoryClass?.teacherName ?: "Unassigned"
    val trackName = currentAdvisoryClass?.track

    val headerGradient = Brush.horizontalGradient(
        colors = listOf(PrimaryBlue, SecondaryTeal)
    )

    if (showAddDialog) {
        AddStudentPremiumDialog(
            advisoryClasses = availableAdvisoryClasses,
            onDismiss = { showAddDialog = false },
            onSave = { id, first, last, advisory ->
                viewModel.addStudent(id, first, last, advisory)
                showAddDialog = false
            },
            onGenerateId = { viewModel.generateStudentId() },
            initialStudent = null,
            forcedAdvisoryClass = currentAdvisoryClass
        )
    }

    if (showEditDialog && studentToEdit != null) {
        AddStudentPremiumDialog(
            advisoryClasses = advisoryClasses, 
            onDismiss = { 
                showEditDialog = false 
                studentToEdit = null
            },
            onSave = { id, first, last, advisory ->
                val updated = studentToEdit!!.copy(
                    id = id, 
                    firstName = first, 
                    lastName = last, 
                    grade = advisory.grade, 
                    section = advisory.section
                )
                viewModel.updateStudent(updated)
                showEditDialog = false
                studentToEdit = null
            },
            onGenerateId = { studentToEdit!!.id },
            initialStudent = studentToEdit
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                backgroundColor = SecondaryTeal,
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Student")
            }
        },
        backgroundColor = Color.Transparent // Make scaffold transparent so box background shows
    ) { padding ->
        // Main Container with Gradient Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(headerGradient) // Applies gradient to entire screen
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // Premium Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Background is handled by parent Box
                        .statusBarsPadding()
                        .padding(24.dp)
                ) {
                    Column {
                        // Top Navigation Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // Class Info
                        Text(
                            text = decodedSection,
                            style = MaterialTheme.typography.h4.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Grade $grade",
                                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (trackName != null) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = trackName,
                                        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Adviser Info Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Adviser: $adviserName",
                                style = MaterialTheme.typography.body2.copy(color = Color.White.copy(alpha = 0.9f))
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))

                        // Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = classStudents.size.toString(),
                                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = "Total Students",
                                    style = MaterialTheme.typography.caption.copy(color = Color.White.copy(alpha = 0.7f))
                                )
                            }
                        }
                    }
                }
                
                // Content Body (Search + List)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colors.background,
                    elevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search student...", color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                backgroundColor = MaterialTheme.colors.surface,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                                textColor = MaterialTheme.colors.onSurface
                            ),
                            singleLine = true,
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } }
                            } else null
                        )

                        if (classStudents.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colors.onBackground.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "No matches found" else "No students enrolled", 
                                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f), 
                                        style = MaterialTheme.typography.body1
                                    )
                                    if (searchQuery.isEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Tap + to add a student", 
                                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f), 
                                            style = MaterialTheme.typography.caption
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(classStudents) { student ->
                                    StudentListItemPremium(
                                        student = student,
                                        onEdit = { 
                                            studentToEdit = it
                                            showEditDialog = true
                                        },
                                        onDelete = { viewModel.archiveStudent(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

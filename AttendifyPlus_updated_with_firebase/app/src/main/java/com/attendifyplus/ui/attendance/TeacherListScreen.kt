package com.attendifyplus.ui.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendifyplus.data.local.entities.TeacherEntity
import com.attendifyplus.ui.components.TeacherTab
import com.attendifyplus.ui.theme.PillShape
import com.attendifyplus.ui.theme.PrimaryBlue
import org.koin.androidx.compose.getViewModel
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TeacherListScreen(
    viewModel: TeacherListViewModel = getViewModel(),
    onBack: () -> Unit,
    onTeacherClick: (String) -> Unit = {},
    onSheetVisibilityChange: (Boolean) -> Unit = {}
) {
    val teachers by viewModel.teachers.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Sorting State
    var sortOption by remember { mutableStateOf("Name") }
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    // Filter State
    var selectedTab by remember { mutableStateOf("All") }
    
    val sortedTeachers = remember(teachers, sortOption, selectedTab) {
        val filtered = when(selectedTab) {
             "Advisers" -> teachers.filter { it.role == "adviser" }
             "Subject" -> teachers.filter { it.role == "subject" }
             else -> teachers
        }

        when (sortOption) {
            "Name" -> filtered.sortedBy { it.firstName }
            "Role" -> filtered.sortedBy { it.role }
            else -> filtered
        }
    }
    
    // Bottom Sheet State
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )

    // Notify parent about sheet visibility
    LaunchedEffect(sheetState.isVisible) {
        onSheetVisibilityChange(sheetState.isVisible)
    }
    
    // Intercept Back Press to close sheet if open
    BackHandler(enabled = sheetState.isVisible) {
        scope.launch { sheetState.hide() }
    }
    
    var teacherToEdit by remember { mutableStateOf<TeacherEntity?>(null) }
    var sheetTrigger by remember { mutableStateOf(0) }

    // Confirmation Dialog State
    var showDeleteDialog by remember { mutableStateOf(false) }
    var teacherToDeleteEntity by remember { mutableStateOf<TeacherEntity?>(null) }


    fun openSheet(teacher: TeacherEntity?) {
        teacherToEdit = teacher
        sheetTrigger++
        scope.launch { sheetState.show() }
    }
    
    fun closeSheet() {
        scope.launch { sheetState.hide() }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetBackgroundColor = MaterialTheme.colors.surface,
        sheetElevation = 16.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        sheetContent = {
            Box(modifier = Modifier.imePadding().navigationBarsPadding()) {
                AddEditTeacherSheet(
                    teacherToEdit = teacherToEdit,
                    trigger = sheetTrigger,
                    onSave = { teacher ->
                        if (teacherToEdit == null) {
                            viewModel.addTeacher(teacher)
                        } else {
                            viewModel.updateTeacher(teacher)
                        }
                        closeSheet()
                    },
                    onDismiss = { closeSheet() }
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding() // Applied status bar padding
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colors.onSurface)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Teachers Directory",
                            style = MaterialTheme.typography.h5.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                        )
                    }
                    
                    // Sort Button
                    Box {
                        IconButton(onClick = { isSortMenuExpanded = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colors.onSurface)
                        }
                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false }
                        ) {
                            listOf("Name", "Role").forEach { option ->
                                DropdownMenuItem(onClick = {
                                    sortOption = option
                                    isSortMenuExpanded = false
                                }) {
                                    Text("Sort by $option")
                                }
                            }
                        }
                    }
                }

                 // Tabs
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TeacherTab(title = "All", selected = selectedTab == "All") { selectedTab = "All" }
                    TeacherTab(title = "Advisers", selected = selectedTab == "Advisers") { selectedTab = "Advisers" }
                    TeacherTab(title = "Subject", selected = selectedTab == "Subject") { selectedTab = "Subject" }
                }


                // Add Button
                Button(
                    onClick = { openSheet(null) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryBlue),
                    elevation = ButtonDefaults.elevation(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Add New Teacher", style = MaterialTheme.typography.button.copy(fontSize = 16.sp), color = Color.White)
                }

                Spacer(Modifier.height(16.dp))

                // List
                if (sortedTeachers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No teachers found", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(sortedTeachers) { teacher ->
                            TeacherCard(
                                teacher = teacher,
                                onClick = { onTeacherClick(teacher.id) },
                                onEdit = { openSheet(teacher) },
                                onDelete = { 
                                    teacherToDeleteEntity = teacher
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && teacherToDeleteEntity != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                teacherToDeleteEntity = null
            },
            title = { Text(text = "Confirm Deletion", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface) },
            text = { Text("Are you sure you want to remove ${teacherToDeleteEntity?.firstName} ${teacherToDeleteEntity?.lastName}?", color = MaterialTheme.colors.onSurface) },
            confirmButton = {
                TextButton(
                    onClick = {
                        teacherToDeleteEntity?.let { viewModel.deleteTeacher(it.id) }
                        showDeleteDialog = false
                        teacherToDeleteEntity = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteDialog = false
                        teacherToDeleteEntity = null
                    }
                ) {
                    Text("Cancel", color = MaterialTheme.colors.onSurface)
                }
            },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = MaterialTheme.colors.surface
        )
    }
}

@Composable
fun TeacherCard(
    teacher: TeacherEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = PrimaryBlue.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = teacher.firstName.take(1).uppercase(),
                        style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${teacher.firstName} ${teacher.lastName}",
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                )
                
                Text(
                    text = "User: ${teacher.username} | Role: ${teacher.role.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.caption.copy(color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                )
                if (!teacher.advisoryGrade.isNullOrBlank()) {
                     Text(
                        text = "Advisory: ${teacher.advisoryGrade} - ${teacher.advisorySection}",
                        style = MaterialTheme.typography.caption.copy(color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            
            Row {
                 IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun AddEditTeacherSheet(
    teacherToEdit: TeacherEntity?,
    trigger: Int,
    onSave: (TeacherEntity) -> Unit,
    onDismiss: () -> Unit
) {
    // Force recomposition when teacherToEdit changes by using it as a key for remember
    val key = teacherToEdit?.id ?: "new_${trigger}"
    
    // Initialize ID: Use existing ID if editing, otherwise generate a new T-UUID
    val initialId = remember(key) { 
        teacherToEdit?.id ?: "T-${UUID.randomUUID().toString().take(6).uppercase()}"
    }
    
    // We use a mutable state for ID just in case, but usually it's fixed for edit
    val teacherId = remember(key) { initialId }
    
    var username by remember(key) { mutableStateOf(teacherToEdit?.username ?: "") }
    var password by remember(key) { mutableStateOf(teacherToEdit?.password ?: "123456") }
    var firstName by remember(key) { mutableStateOf(teacherToEdit?.firstName ?: "") }
    var lastName by remember(key) { mutableStateOf(teacherToEdit?.lastName ?: "") }
    var email by remember(key) { mutableStateOf(teacherToEdit?.email ?: "") }
    var role by remember(key) { mutableStateOf(teacherToEdit?.role ?: "subject") }
    var department by remember(key) { mutableStateOf(teacherToEdit?.department ?: "JHS") }
    var advisoryGrade by remember(key) { mutableStateOf(teacherToEdit?.advisoryGrade ?: "") }
    var advisorySection by remember { mutableStateOf(teacherToEdit?.advisorySection ?: "") }
    
    var isError by remember { mutableStateOf(false) }
    var isUsernameModified by remember(key) { mutableStateOf(teacherToEdit != null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp)
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        )
        
        Spacer(Modifier.height(24.dp))

        Text(
            text = if (teacherToEdit == null) "Add New Teacher" else "Edit Teacher Details",
            style = MaterialTheme.typography.h5.copy(fontWeight = FontWeight.Bold, color = PrimaryBlue)
        )
        
        Spacer(Modifier.height(24.dp))
        
        // ID & Username
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = teacherId,
                onValueChange = {},
                label = { Text("ID (Auto)") },
                modifier = Modifier.weight(0.4f),
                enabled = false,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    disabledTextColor = MaterialTheme.colors.onSurface,
                    disabledLabelColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            )
            OutlinedTextField(
                value = username,
                onValueChange = { 
                    username = it
                    isError = false
                    isUsernameModified = true
                },
                label = { Text("Username") },
                modifier = Modifier.weight(0.6f),
                singleLine = true,
                isError = isError && username.isBlank(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colors.onSurface
                )
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Name
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { 
                    firstName = it
                    isError = false
                    if (!isUsernameModified) {
                        username = it
                    }
                },
                label = { Text("First Name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = isError && firstName.isBlank(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colors.onSurface
                )
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { 
                    lastName = it
                    isError = false
                },
                label = { Text("Last Name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = isError && lastName.isBlank(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colors.onSurface
                )
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = MaterialTheme.colors.onSurface
            )
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = MaterialTheme.colors.onSurface
            )
        )
        Text(
            "Default is 123456. Teacher can change this later.",
            style = MaterialTheme.typography.caption.copy(color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)),
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Spacer(Modifier.height(24.dp))
        Divider(color = Color.LightGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(16.dp))
        
        Text("Role Assignment", style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = role == "subject", 
                onClick = { role = "subject" },
                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue, unselectedColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            )
            Text("Subject Teacher", modifier = Modifier.clickable { role = "subject" }, color = MaterialTheme.colors.onSurface)
            Spacer(Modifier.width(24.dp))
            RadioButton(
                selected = role == "adviser", 
                onClick = { role = "adviser" },
                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue, unselectedColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            )
            Text("Adviser", modifier = Modifier.clickable { role = "adviser" }, color = MaterialTheme.colors.onSurface)
        }
        
        // Conditionally show department and advisory details for Advisers
        if (role == "adviser") {
            Spacer(Modifier.height(24.dp))
            
            Text("Advisory Class Details", style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface))
            Spacer(Modifier.height(16.dp))
            
            // Department for Advisory Class
            Text("Department", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)))
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DepartmentOption("JHS (Gr. 7-10)", department == "JHS") { department = "JHS" }
                DepartmentOption("SHS (Gr. 11-12)", department == "SHS") { department = "SHS" }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Grade and Section
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = advisoryGrade,
                    onValueChange = { 
                        advisoryGrade = it
                        isError = false 
                    },
                    label = { Text("Grade Level") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = isError && advisoryGrade.isBlank(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface)
                )
                OutlinedTextField(
                    value = advisorySection,
                    onValueChange = { 
                        advisorySection = it
                        isError = false 
                    },
                    label = { Text("Section") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = isError && advisorySection.isBlank(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface)
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        
        if (isError) {
             val errorMsg = if (role == "adviser" && (advisoryGrade.isBlank() || advisorySection.isBlank())) 
                "Please fill in all Advisory Class details."
            else 
                "Please fill in all required fields (Username, Name)."
            
            Text(
                text = errorMsg,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Button(
            onClick = {
                val isAdviserInfoMissing = role == "adviser" && (advisoryGrade.isBlank() || advisorySection.isBlank())
                if (teacherId.isNotBlank() && username.isNotBlank() && firstName.isNotBlank() && lastName.isNotBlank() && !isAdviserInfoMissing) {
                    onSave(
                        TeacherEntity(
                            id = teacherId,
                            username = username,
                            password = password.ifBlank { "123456" },
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            role = role,
                            department = if (role == "adviser") department else null, // Set department only for advisers
                            advisoryGrade = if (role == "adviser") advisoryGrade else null,
                            advisorySection = if (role == "adviser") advisorySection else null
                        )
                    )
                } else {
                    isError = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryBlue)
        ) {
            Text("Save Teacher", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        Spacer(Modifier.height(16.dp))

        // Added Cancel button inside the sheet
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = PillShape,
            border = BorderStroke(1.dp, Color.LightGray),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun DepartmentOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) PrimaryBlue else Color.LightGray
    val backgroundColor = if (selected) PrimaryBlue.copy(alpha = 0.1f) else MaterialTheme.colors.surface
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        color = backgroundColor,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null, // Handled by parent
                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue, unselectedColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.body2.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colors.onSurface))
        }
    }
}

package com.attendifyplus.ui.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.attendifyplus.ui.theme.PillShape
import com.attendifyplus.ui.theme.PrimaryBlue
import com.attendifyplus.ui.theme.SecondaryTeal
import org.koin.androidx.compose.getViewModel

@Composable
fun TeacherDetailScreen(
    navController: NavController,
    teacherId: String,
    viewModel: TeacherDetailViewModel = getViewModel()
) {
    val teacher by viewModel.teacher.collectAsState()
    val subjects by viewModel.subjects.collectAsState()

    var showEditProfile by remember { mutableStateOf(false) }
    var showAdvisoryDialog by remember { mutableStateOf(false) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(teacherId) {
        viewModel.loadTeacher(teacherId)
    }

    if (teacher == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val t = teacher!!

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            TopAppBar(
                title = { Text(t.firstName + " " + t.lastName, color = MaterialTheme.colors.onSurface) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colors.onSurface)
                    }
                },
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 4.dp
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Profile Section
                item {
                    Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, backgroundColor = MaterialTheme.colors.surface) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Profile", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                                Row {
                                    IconButton(onClick = { showResetPasswordDialog = true }) {
                                        Icon(Icons.Default.LockReset, contentDescription = "Reset Password", tint = Color.Red.copy(alpha = 0.7f))
                                    }
                                    IconButton(onClick = { showEditProfile = true }) {
                                        Icon(Icons.Default.Edit, null, tint = PrimaryBlue)
                                    }
                                }
                            }
                            Divider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
                            Text("Username: ${t.username}", color = MaterialTheme.colors.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text("Name: ${t.firstName} ${t.lastName}", color = MaterialTheme.colors.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text("Email: ${t.email}", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            Spacer(Modifier.height(4.dp))
                            Text("Role: ${t.role.uppercase()}", color = SecondaryTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. Advisory Section
                item {
                    Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, backgroundColor = MaterialTheme.colors.surface) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Advisory Class", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                                TextButton(onClick = { showAdvisoryDialog = true }) {
                                    Text(if (t.advisoryGrade.isNullOrBlank()) "Assign" else "Edit")
                                }
                            }
                            Divider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
                            if (t.advisoryGrade.isNullOrBlank()) {
                                Text("No advisory class assigned.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), style = MaterialTheme.typography.body2)
                            } else {
                                Text("${t.advisoryGrade} - ${t.advisorySection}", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                                if (t.advisoryStartTime != null) {
                                    Text("Schedule: ${t.advisoryStartTime}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface)
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.removeAdvisory() },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red.copy(alpha = 0.1f)),
                                    elevation = ButtonDefaults.elevation(0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Remove Assignment", color = Color.Red, style = MaterialTheme.typography.caption)
                                }
                            }
                        }
                    }
                }

                // 3. Subject Classes Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subject Classes", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                        Button(
                            onClick = { showSubjectDialog = true },
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryBlue),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Class", color = Color.White)
                        }
                    }
                }

                if (subjects.isEmpty()) {
                    item {
                        Text("No subject classes assigned.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    items(subjects) { subject ->
                        Card(shape = RoundedCornerShape(8.dp), elevation = 1.dp, backgroundColor = MaterialTheme.colors.surface) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(subject.subjectName, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                                    Text("${subject.gradeLevel} - ${subject.section}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                                }
                                IconButton(onClick = { viewModel.deleteSubject(subject) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showEditProfile) {
        EditProfileDialog(
            username = t.username,
            firstName = t.firstName,
            lastName = t.lastName,
            email = t.email ?: "",
            onDismiss = { showEditProfile = false },
            onSave = { u, f, l, e ->
                viewModel.updateProfile(u, f, l, e)
                showEditProfile = false
            }
        )
    }

    if (showResetPasswordDialog) {
        ResetPasswordDialog(
            onDismiss = { showResetPasswordDialog = false },
            onSave = { newPass ->
                viewModel.resetPassword(newPass)
                showResetPasswordDialog = false
            }
        )
    }

    if (showAdvisoryDialog) {
        AdvisoryDialog(
            initialGrade = t.advisoryGrade ?: "",
            initialSection = t.advisorySection ?: "",
            onDismiss = { showAdvisoryDialog = false },
            onSave = { g, s ->
                viewModel.updateAdvisory(g, s)
                showAdvisoryDialog = false
            }
        )
    }

    if (showSubjectDialog) {
        AddSubjectDialog(
            onDismiss = { showSubjectDialog = false },
            onAdd = { name, grade, section, sched ->
                viewModel.addSubject(name, grade, section, sched)
                showSubjectDialog = false
            }
        )
    }
}

@Composable
fun EditProfileDialog(username: String, firstName: String, lastName: String, email: String, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var u by remember { mutableStateOf(username) }
    var f by remember { mutableStateOf(firstName) }
    var l by remember { mutableStateOf(lastName) }
    var e by remember { mutableStateOf(email) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp), backgroundColor = MaterialTheme.colors.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Edit Profile", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = u, 
                    onValueChange = { u = it }, 
                    label = { Text("Username") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = f, 
                    onValueChange = { f = it }, 
                    label = { Text("First Name") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = l, 
                    onValueChange = { l = it }, 
                    label = { Text("Last Name") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = e, 
                    onValueChange = { e = it }, 
                    label = { Text("Email") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onSave(u, f, l, e) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            }
        }
    }
}

@Composable
fun ResetPasswordDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp), backgroundColor = MaterialTheme.colors.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Reset Password", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = Color.Red)
                Spacer(Modifier.height(8.dp))
                Text("Enter a new password for this teacher.", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colors.onSurface
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (password.isNotBlank()) onSave(password) },
                        enabled = password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                    ) {
                        Text("Reset", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AdvisoryDialog(initialGrade: String, initialSection: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var g by remember { mutableStateOf(initialGrade) }
    var s by remember { mutableStateOf(initialSection) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            elevation = 24.dp,
            backgroundColor = MaterialTheme.colors.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header with Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(PrimaryBlue.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "Advisory Class Assignment",
                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Text(
                    text = "Assign a designated class for this teacher to manage and advise.",
                    style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.Center),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 24.dp)
                )

                // Grade Level Input
                Text("Grade Level", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = g, 
                    onValueChange = { g = it; error = null }, 
                    placeholder = { Text("e.g. Grade 10") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                        backgroundColor = MaterialTheme.colors.background
                    ),
                    singleLine = true
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Section Input
                Text("Section Name", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = s, 
                    onValueChange = { s = it; error = null }, 
                    placeholder = { Text("e.g. Newton") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                     colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                        backgroundColor = MaterialTheme.colors.background
                    ),
                    singleLine = true
                )

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Color.Red, style = MaterialTheme.typography.caption)
                }

                Spacer(Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = PillShape,
                        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
                    ) {
                        Text("Cancel", color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Button(
                        onClick = { 
                            if (g.isBlank() || s.isBlank()) {
                                error = "Both Grade and Section are required."
                            } else {
                                onSave(g, s)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryBlue)
                    ) {
                        Text("Assign Class", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AddSubjectDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var sched by remember { mutableStateOf("8:00 - 9:00 AM") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp), backgroundColor = MaterialTheme.colors.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Add Subject Class", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Subject Name") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = grade, 
                        onValueChange = { grade = it }, 
                        label = { Text("Grade") }, 
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = MaterialTheme.colors.onSurface
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = section, 
                        onValueChange = { section = it }, 
                        label = { Text("Section") }, 
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = MaterialTheme.colors.onSurface
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sched, 
                    onValueChange = { sched = it }, 
                    label = { Text("Schedule") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onAdd(name, grade, section, sched) }, modifier = Modifier.fillMaxWidth()) { Text("Add Class") }
            }
        }
    }
}

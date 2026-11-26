package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ExamRepository
import com.example.data.LocalePreferences
import com.example.data.OmrAiService
import com.example.data.attemptAutoSignIn
import com.example.data.authStateFlow
import com.example.data.signOut
import com.example.firebase.configureAppCheck
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.stringsFor
import com.example.model.ScannedByRole
import com.example.model.Submission
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExamsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.theme.ShikkhaScanTheme
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.ScanViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class AppTab(val testTag: String) {
    HOME("nav_tab_home"),
    SCAN("nav_tab_scan"),
    EXAMS("nav_tab_exams"),
    RESULTS("nav_tab_results")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must configure App Check with launch intent before setContent, ViewModels, or Firebase calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val localePrefs = remember(context) { LocalePreferences(context.applicationContext) }
            val language by localePrefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.BENGALI)
            val strings = remember(language) { stringsFor(language) }

            val toggleLanguage: () -> Unit = {
                val next = if (language == AppLanguage.BENGALI) AppLanguage.ENGLISH else AppLanguage.BENGALI
                scope.launch { localePrefs.setLanguage(next) }
            }

            ShikkhaScanTheme {
                AppRoot(
                    language = language,
                    strings = strings,
                    onToggleLanguage = toggleLanguage
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    language: AppLanguage,
    strings: AppStrings,
    onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            language = language,
            strings = strings,
            onToggleLanguage = onToggleLanguage,
            onAuthSuccess = {}
        )
    } else {
        key(user.uid) {
            AuthenticatedShell(
                user = user,
                language = language,
                strings = strings,
                onToggleLanguage = onToggleLanguage,
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedShell(
    user: FirebaseUser,
    language: AppLanguage,
    strings: AppStrings,
    onToggleLanguage: () -> Unit,
    onSignOut: () -> Unit
) {
    val uid = user.uid
    val displayName = user.displayName.orEmpty().ifBlank { user.email.orEmpty() }

    val dashboardViewModel: DashboardViewModel = viewModel(
        key = "dash_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                DashboardViewModel(ExamRepository(db), uid)
            }
        }
    )

    val scanViewModel: ScanViewModel = viewModel(
        key = "scan_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ScanViewModel(ExamRepository(db), OmrAiService(), displayName)
            }
        }
    )

    val teacherExamsState by dashboardViewModel.teacherExamsState.collectAsStateWithLifecycle()
    val mySubmissionsState by dashboardViewModel.mySubmissionsState.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var triggerCreateExam by rememberSaveable { mutableStateOf(false) }
    var inspectedSubmission by remember { mutableStateOf<Submission?>(null) }

    if (currentTab != AppTab.HOME && inspectedSubmission == null) {
        BackHandler { currentTab = AppTab.HOME }
    }

    val navItems = listOf(
        Triple(AppTab.HOME, strings.navHome, Pair(Icons.Filled.Home, Icons.Outlined.Home)),
        Triple(AppTab.SCAN, strings.navScan, Pair(Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner)),
        Triple(AppTab.EXAMS, strings.navExams, Pair(Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment)),
        Triple(AppTab.RESULTS, strings.navResults, Pair(Icons.Filled.BarChart, Icons.Outlined.BarChart))
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        val screenContent: @Composable (Modifier) -> Unit = { contentModifier ->
            Box(modifier = contentModifier.fillMaxSize()) {
                when (currentTab) {
                    AppTab.HOME -> {
                        HomeScreen(
                            userDisplayName = displayName,
                            language = language,
                            strings = strings,
                            examsState = teacherExamsState,
                            submissionsState = mySubmissionsState,
                            onToggleLanguage = onToggleLanguage,
                            onSignOut = onSignOut,
                            onNavigateToScan = { currentTab = AppTab.SCAN },
                            onFindExamByCode = { code ->
                                scanViewModel.setScanningRole(ScannedByRole.STUDENT)
                                scanViewModel.updateExamCodeInput(code)
                                scanViewModel.lookupExamByCode(code)
                                currentTab = AppTab.SCAN
                            },
                            onOpenCreateExam = {
                                triggerCreateExam = true
                                currentTab = AppTab.EXAMS
                            },
                            onNavigateToResults = {
                                inspectedSubmission = null
                                currentTab = AppTab.RESULTS
                            },
                            onOpenSubmission = { submission ->
                                inspectedSubmission = submission
                                currentTab = AppTab.RESULTS
                            }
                        )
                    }

                    AppTab.SCAN -> {
                        ScanScreen(
                            scanViewModel = scanViewModel,
                            teacherExamsState = teacherExamsState,
                            strings = strings,
                            onDoneToHome = { currentTab = AppTab.RESULTS }
                        )
                    }

                    AppTab.EXAMS -> {
                        ExamsScreen(
                            currentUserId = uid,
                            teacherName = displayName,
                            dashboardViewModel = dashboardViewModel,
                            strings = strings,
                            showCreateTrigger = triggerCreateExam,
                            onConsumeCreateTrigger = { triggerCreateExam = false },
                            onScanForExam = { exam ->
                                scanViewModel.selectExamDirectly(exam, ScannedByRole.TEACHER)
                                currentTab = AppTab.SCAN
                            },
                            onOpenSubmission = { submission ->
                                inspectedSubmission = submission
                                currentTab = AppTab.RESULTS
                            }
                        )
                    }

                    AppTab.RESULTS -> {
                        ResultsScreen(
                            dashboardViewModel = dashboardViewModel,
                            selectedSubmission = inspectedSubmission,
                            strings = strings,
                            onSelectSubmission = { inspectedSubmission = it }
                        )
                    }
                }
            }
        }

        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                NavigationRail {
                    navItems.forEach { (tab, label, icons) ->
                        val selected = currentTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (tab != AppTab.RESULTS) inspectedSubmission = null
                                currentTab = tab
                            },
                            icon = {
                                NavTabIcon(
                                    icon = if (selected) icons.first else icons.second,
                                    label = label
                                )
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
                screenContent(Modifier.weight(1f))
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar {
                        navItems.forEach { (tab, label, icons) ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab != AppTab.RESULTS) inspectedSubmission = null
                                    currentTab = tab
                                },
                                icon = {
                                    NavTabIcon(
                                        icon = if (selected) icons.first else icons.second,
                                        label = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                screenContent(Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
private fun NavTabIcon(icon: ImageVector, label: String) {
    Icon(imageVector = icon, contentDescription = label)
}

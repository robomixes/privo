// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.privateai.camera.MainActivity
import com.privateai.camera.ui.calibrate.CalibrationWizardScreen
import com.privateai.camera.ui.calibrate.isWizardComplete
import com.privateai.camera.ui.calibrate.markWizardComplete
import com.privateai.camera.ui.camera.CameraScreen
import com.privateai.camera.ui.camera.CaptureScreen
import com.privateai.camera.ui.health.HealthScreen
import com.privateai.camera.ui.home.HomeScreen
import com.privateai.camera.ui.assistant.AssistantScreen
import com.privateai.camera.ui.home.PrivoraBottomTabs
import com.privateai.camera.ui.insights.InsightsScreen
import com.privateai.camera.ui.notes.NotesScreen
import com.privateai.camera.ui.onboarding.OnboardingScreen
import com.privateai.camera.ui.onboarding.completeOnboardingQuick
import com.privateai.camera.ui.onboarding.isOnboardingComplete
import com.privateai.camera.ui.passwords.PasswordHintsScreen
import com.privateai.camera.ui.qrscanner.QrScannerScreen
import com.privateai.camera.ui.reminders.RemindersScreen
import com.privateai.camera.ui.scanner.ScannerScreen
import com.privateai.camera.ui.settings.BackupScreen
import com.privateai.camera.ui.settings.ChangePinScreen
import com.privateai.camera.ui.settings.DuressSetupScreen
import com.privateai.camera.ui.settings.FeatureToggleManager
import com.privateai.camera.ui.settings.HomeLayout
import com.privateai.camera.ui.settings.SettingsScreen
import com.privateai.camera.ui.tools.UnitConverterScreen
import com.privateai.camera.ui.totp.TotpListScreen
import com.privateai.camera.ui.translate.TranslateScreen
import com.privateai.camera.ui.vault.VaultScreen

/** Top-level routes where the persistent bottom tab bar is shown (Tabs layout only). */
private val TAB_VISIBLE_ROUTES = setOf(
    "home", "vault", "notes", "insights", "health", "reminders", "passwords", "contacts", "tools", "qrscanner", "translate", "detect", "scan"
)

@Composable
fun PrivateAICameraApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    // First-run flow: onboarding → calibration wizard → home. The wizard is also
    // re-runnable from Settings; in that case PrivateAICameraApp doesn't pick it
    // as the start destination — Settings navigates explicitly to "calibrate".
    val startDest = when {
        !isOnboardingComplete(context) -> "onboarding"
        !isWizardComplete(context) -> "calibrate"
        else -> "home"
    }

    // Navigate to widget-requested destination after the NavHost is ready
    LaunchedEffect(Unit) {
        val route = MainActivity.consumePendingRoute()
        if (route != null && isOnboardingComplete(context)) {
            navController.navigate(route)
        }
    }

    // Safe back navigation — prevents white screen on double-tap
    val safeBack: () -> Unit = {
        if (navController.currentBackStackEntry != null && navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    // Persistent tab bar logic — only when Tabs layout is selected + current route is top-level
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("?")
    val layoutMode = FeatureToggleManager.getHomeLayout(context)
    // Hide the persistent tab bar while the soft keyboard is open. Otherwise it sits between
    // the active text field's toolbar (e.g. Notes editor's format bar) and the keyboard,
    // leaving an ~80dp dead-strip that pushes the toolbar visually into the middle of the screen.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showTabs = layoutMode == HomeLayout.TABS && currentRoute in TAB_VISIBLE_ROUTES && !imeVisible

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // Don't consume system insets — inner feature screens have their own Scaffolds with TopAppBars
            // that already handle status bar insets. Only the bottom bar inset is relevant here.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTabs) {
                    PrivoraBottomTabs(
                        currentRoute = currentRoute,
                        onFeatureClick = { route ->
                            // Single-top navigation — don't stack duplicate entries of top-level routes
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo("home") { saveState = true }
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
        // Apply only the bottom inset (from the bottom bar) to the NavHost,
        // so inner screens' own top bars still render flush to the status bar.
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable("onboarding") { backStackEntry ->
                val importSummary = backStackEntry.savedStateHandle.get<String>("import_summary")
                OnboardingScreen(
                    onComplete = {
                        // After initial setup, route to the calibration wizard
                        // (skipped on subsequent launches once it's been completed).
                        val next = if (isWizardComplete(context)) "home" else "calibrate"
                        navController.navigate(next) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onImportBackup = {
                        navController.navigate("backup/onboarding")
                    },
                    importSummary = importSummary,
                    onCompleteWithSummary = { summary ->
                        // If the wizard already ran (e.g. user re-imported a backup
                        // that pre-included the wizard_completed flag), skip it.
                        val target = if (isWizardComplete(context))
                            "home?summary=${android.net.Uri.encode(summary)}"
                        else
                            "calibrate"
                        navController.navigate(target) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("calibrate") {
                CalibrationWizardScreen(
                    onFinish = {
                        navController.navigate("home") {
                            popUpTo("calibrate") { inclusive = true }
                        }
                    },
                    onSetDuressPin = { navController.navigate("duress_setup") }
                )
            }
            composable("home") {
                HomeScreen(
                    onFeatureClick = { route -> navController.navigate(route) },
                    onSettingsClick = { navController.navigate("settings") },
                    onAssistantClick = { navController.navigate("assistant") }
                )
            }
            composable(
                "home?summary={summary}",
                arguments = listOf(navArgument("summary") { type = NavType.StringType; defaultValue = "" })
            ) { backStackEntry ->
                val summary = backStackEntry.arguments?.getString("summary") ?: ""
                HomeScreen(
                    onFeatureClick = { route -> navController.navigate(route) },
                    onSettingsClick = { navController.navigate("settings") },
                    onAssistantClick = { navController.navigate("assistant") },
                    importSummary = summary.ifEmpty { null }
                )
            }
            composable("assistant") {
                AssistantScreen(
                    onBack = safeBack,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(
                "assistant?seed={seed}&docId={docId}",
                arguments = listOf(
                    navArgument("seed") { defaultValue = ""; type = NavType.StringType },
                    navArgument("docId") { defaultValue = ""; type = NavType.StringType }
                )
            ) { backStackEntry ->
                val seed = backStackEntry.arguments?.getString("seed")?.ifBlank { null }
                val docId = backStackEntry.arguments?.getString("docId")?.ifBlank { null }
                AssistantScreen(
                    onBack = safeBack,
                    onNavigate = { route -> navController.navigate(route) },
                    seedPrompt = seed,
                    attachedDocId = docId
                )
            }
            composable(
                "notes?openNoteId={openNoteId}",
                arguments = listOf(navArgument("openNoteId") { defaultValue = ""; type = NavType.StringType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("openNoteId")?.ifBlank { null }
                NotesScreen(onBack = safeBack, openNoteId = noteId)
            }
            composable("camera") {
                CaptureScreen(
                    onBack = safeBack,
                    onPhotoTap = { navController.navigate("vault") }
                )
            }
            composable("detect") {
                CameraScreen(onBack = safeBack)
            }
            composable("scan") {
                ScannerScreen(onBack = safeBack)
            }
            composable("qrscanner") {
                QrScannerScreen(
                    onBack = safeBack,
                    onOtpAuthScanned = { uri ->
                        // Any scanned otpauth:// URI — regardless of where the user
                        // entered the scanner — routes into the Authenticator add screen
                        // pre-populated with the parsed entry.
                        navController.navigate("totp?uri=${android.net.Uri.encode(uri)}") {
                            popUpTo("qrscanner") { inclusive = true }
                        }
                    }
                )
            }
            composable(
                "qrscanner?otpOnly={otpOnly}",
                arguments = listOf(navArgument("otpOnly") { defaultValue = "false"; type = NavType.StringType })
            ) { backStackEntry ->
                val otpOnly = backStackEntry.arguments?.getString("otpOnly") == "true"
                QrScannerScreen(
                    onBack = safeBack,
                    onOtpAuthScanned = { uri ->
                        navController.navigate("totp?uri=${android.net.Uri.encode(uri)}") {
                            popUpTo("qrscanner?otpOnly={otpOnly}") { inclusive = true }
                        }
                    },
                    otpAuthOnly = otpOnly
                )
            }
            composable("translate") {
                TranslateScreen(onBack = safeBack)
            }
            composable("vault") {
                VaultScreen(
                    onBack = safeBack,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("vault?search={query}",
                arguments = listOf(androidx.navigation.navArgument("query") { defaultValue = ""; type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                VaultScreen(
                    onBack = safeBack,
                    initialSearchQuery = query,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("vault?openPhotoId={photoId}",
                arguments = listOf(androidx.navigation.navArgument("photoId") { defaultValue = ""; type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val photoId = backStackEntry.arguments?.getString("photoId")?.ifBlank { null }
                VaultScreen(
                    onBack = safeBack,
                    initialOpenPhotoId = photoId,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("notes") {
                NotesScreen(onBack = safeBack)
            }
            composable("notes?personId={personId}",
                arguments = listOf(androidx.navigation.navArgument("personId") { defaultValue = ""; type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId")?.ifBlank { null }
                NotesScreen(onBack = safeBack, filterPersonId = personId)
            }
            composable("insights") {
                InsightsScreen(onBack = safeBack)
            }
            composable("insights?personId={personId}",
                arguments = listOf(
                    androidx.navigation.navArgument("personId") { defaultValue = ""; type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId")?.ifBlank { null }
                InsightsScreen(onBack = safeBack, filterPersonId = personId)
            }
            composable("health") {
                HealthScreen(
                    onBack = safeBack,
                    onNavigateToPeople = { navController.navigate("contacts") }
                )
            }
            composable("health?personId={personId}&tab={tab}",
                arguments = listOf(
                    androidx.navigation.navArgument("personId") { defaultValue = ""; type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("tab") { defaultValue = ""; type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId")?.ifBlank { null }
                val tab = backStackEntry.arguments?.getString("tab") ?: ""
                val tabIndex = when (tab) { "meds", "medications" -> 1; "cycle" -> 2; else -> 0 }
                HealthScreen(
                    onBack = safeBack,
                    initialTab = tabIndex,
                    filterPersonId = personId,
                    onNavigateToPeople = { navController.navigate("contacts") }
                )
            }
            composable("reminders") {
                RemindersScreen(onBack = safeBack)
            }
            composable("passwords") {
                PasswordHintsScreen(onBack = safeBack)
            }
            composable(
                "totp?uri={uri}",
                arguments = listOf(navArgument("uri") { defaultValue = ""; type = NavType.StringType })
            ) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri")?.ifBlank { null }
                TotpListScreen(
                    onBack = safeBack,
                    onScanQr = { navController.navigate("qrscanner?otpOnly=true") },
                    seedFromUri = uri
                )
            }
            composable("totp") {
                TotpListScreen(
                    onBack = safeBack,
                    onScanQr = { navController.navigate("qrscanner?otpOnly=true") }
                )
            }
            composable("tools") {
                UnitConverterScreen(onBack = safeBack)
            }
            composable("contacts") {
                com.privateai.camera.ui.contacts.ContactsScreen(
                    onBack = safeBack,
                    onNavigateToInsights = { personId ->
                        // Contacts → "view their health" — the legacy callback name
                        // says "Insights" but the destination is now the Health
                        // feature (Vitals/Meds/Cycle live there).
                        navController.navigate("health?personId=${personId ?: ""}&tab=")
                    },
                    onNavigateToNotes = { personId ->
                        navController.navigate("notes?personId=${personId ?: ""}")
                    },
                    onNavigateToVault = { query ->
                        navController.navigate("vault?search=${query ?: ""}")
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = safeBack,
                    onBackupClick = { navController.navigate("backup") },
                    onDuressClick = { navController.navigate("duress_setup") },
                    onChangePinClick = { navController.navigate("change_pin") },
                    onRerunWizardClick = { navController.navigate("calibrate") },
                    onOcrLanguagesClick = { navController.navigate("ocr_languages") }
                )
            }
            composable("ocr_languages") {
                com.privateai.camera.ui.settings.OcrLanguagesScreen(onBack = safeBack)
            }
            composable("duress_setup") {
                DuressSetupScreen(onBack = safeBack)
            }
            composable("change_pin") {
                ChangePinScreen(onBack = safeBack)
            }
            composable("backup") {
                BackupScreen(onBack = safeBack)
            }
            composable("backup/onboarding") {
                BackupScreen(
                    onBack = safeBack,
                    onImportComplete = { summary ->
                        // Go back to onboarding to complete auth mode setup
                        // Store summary to show later on home
                        navController.previousBackStackEntry?.savedStateHandle?.set("import_summary", summary)
                        navController.popBackStack() // back to onboarding step 2 (auth choice)
                    }
                )
            }
        }
        } // end NavHost Scaffold content lambda
    }
}

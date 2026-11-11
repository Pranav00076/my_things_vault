package com.example.invoicevault.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.invoicevault.InvoiceVaultApp
import com.example.invoicevault.ui.HomeDashboard
import com.example.invoicevault.ui.screens.*

@Composable
fun AppNavigation(app: InvoiceVaultApp) {
    val navController = rememberNavController()
    val invoiceRepo = app.invoiceRepository
    val productRepo = app.productRepository

    NavHost(
        navController = navController,
        startDestination = NavRoutes.INVOICE_DASHBOARD
    ) {
        // 1. Invoice Dashboard Screen
        composable(NavRoutes.INVOICE_DASHBOARD) {
            InvoiceDashboardScreen(
                invoiceRepository = invoiceRepo,
                productRepository = productRepo,
                onNavigateToUpload = { navController.navigate(NavRoutes.UPLOAD_INVOICE) },
                onNavigateToHistory = { navController.navigate(NavRoutes.INVOICE_HISTORY) },
                onNavigateToProducts = { navController.navigate(NavRoutes.PRODUCTS_CATALOG) },
                onNavigateToInvoiceDetails = { id -> navController.navigate(NavRoutes.invoiceDetails(id)) }
            )
        }

        // 2. Upload Invoice Screen
        composable(NavRoutes.UPLOAD_INVOICE) {
            UploadInvoiceScreen(
                productRepository = productRepo,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCameraScanner = { navController.navigate(NavRoutes.CAMERA_SCANNER) },
                onDocumentReadyForPreview = { path ->
                    navController.navigate(NavRoutes.invoicePreview(path))
                }
            )
        }

        // 3. Camera Scanner Screen
        composable(NavRoutes.CAMERA_SCANNER) {
            CameraScannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onCapturedSuccess = { path ->
                    navController.navigate(NavRoutes.invoicePreview(path)) {
                        popUpTo(NavRoutes.UPLOAD_INVOICE)
                    }
                }
            )
        }

        // 4. Invoice Preview Screen
        composable(
            route = NavRoutes.INVOICE_PREVIEW,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath").orEmpty()
            val filePath = Uri.decode(encodedPath)

            InvoicePreviewScreen(
                filePath = filePath,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOcrReview = { path ->
                    navController.navigate(NavRoutes.ocrReview(path))
                },
                onNavigateToManualEntry = { path ->
                    navController.navigate(NavRoutes.ocrReview(path))
                }
            )
        }

        // 5. OCR Extraction Review Screen
        composable(
            route = NavRoutes.OCR_REVIEW,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath").orEmpty()
            val filePath = Uri.decode(encodedPath)

            OcrReviewScreen(
                filePath = filePath,
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = { invoiceId ->
                    navController.navigate(NavRoutes.invoiceDetails(invoiceId)) {
                        popUpTo(NavRoutes.INVOICE_DASHBOARD)
                    }
                }
            )
        }

        // 6. Edit Invoice Details Screen
        composable(
            route = NavRoutes.EDIT_INVOICE,
            arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
        ) { backStackEntry ->
            val invoiceId = backStackEntry.arguments?.getLong("invoiceId") ?: 0L

            EditInvoiceScreen(
                invoiceId = invoiceId,
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() },
                onDeletedSuccess = {
                    navController.popBackStack(NavRoutes.INVOICE_DASHBOARD, inclusive = false)
                }
            )
        }

        // 7. Invoice History Screen
        composable(NavRoutes.INVOICE_HISTORY) {
            InvoiceHistoryScreen(
                invoiceRepository = invoiceRepo,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInvoiceDetails = { id ->
                    navController.navigate(NavRoutes.invoiceDetails(id))
                },
                onNavigateToUpload = { navController.navigate(NavRoutes.UPLOAD_INVOICE) }
            )
        }

        // 8. Invoice Details Screen
        composable(
            route = NavRoutes.INVOICE_DETAILS,
            arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
        ) { backStackEntry ->
            val invoiceId = backStackEntry.arguments?.getLong("invoiceId") ?: 0L

            InvoiceDetailsScreen(
                invoiceId = invoiceId,
                invoiceRepository = invoiceRepo,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(NavRoutes.editInvoice(id)) },
                onDeletedSuccess = {
                    navController.popBackStack(NavRoutes.INVOICE_DASHBOARD, inclusive = false)
                }
            )
        }

        // 9. Member 1 Products Catalog Screen
        composable(NavRoutes.PRODUCTS_CATALOG) {
            HomeDashboard(repository = productRepo)
        }
    }
}


package com.example.invoicevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.example.invoicevault.ui.navigation.AppNavigation
import com.example.invoicevault.ui.theme.InvoiceVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as InvoiceVaultApp
        setContent {
            InvoiceVaultTheme {
                Surface {
                    AppNavigation(app = app)
                }
            }
        }
    }
}

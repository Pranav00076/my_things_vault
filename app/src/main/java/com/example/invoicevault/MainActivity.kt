package com.example.invoicevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.invoicevault.ui.HomeDashboard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as InvoiceVaultApp).productRepository
        setContent {
            MaterialTheme {
                Surface { HomeDashboard(repository = repository) }
            }
        }
    }
}

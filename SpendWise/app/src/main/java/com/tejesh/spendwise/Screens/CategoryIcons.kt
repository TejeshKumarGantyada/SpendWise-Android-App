package com.tejesh.spendwise.Screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

// This central helper function provides a consistent icon for each category name.
@Composable
fun getIconForCategory(category: String): ImageVector {
    return when (category.lowercase()) {
        "food" -> Icons.Default.Fastfood
        "travel" -> Icons.Default.Commute
        "entertainment" -> Icons.Default.Movie
        "shopping" -> Icons.Default.ShoppingCart
        "rent" -> Icons.Default.Home
        "bills" -> Icons.Default.Receipt
        "health" -> Icons.Default.LocalHospital
        "salary" -> Icons.Default.MonetizationOn
        "freelance" -> Icons.Default.Work
        "gift" -> Icons.Default.CardGiftcard
        else -> Icons.Default.Category
    }
}

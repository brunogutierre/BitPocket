package io.github.brunogutierre.bitpocket.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.screenHorizontalPadding

// Temporary screens for the navigation shell; replaced by the feature PRs.

@Composable
fun PlaceholderScreen(
    title: String,
    content: @Composable () -> Unit = {},
) {
    Scaffold { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = screenHorizontalPadding()),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            content()
        }
    }
}

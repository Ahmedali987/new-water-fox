/*
 * Copyright (c) 2025 DuckDuckGo
 * ... license header ...
 */

package com.duckduckgo.app.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R.drawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ViewLogsScreen(
    onBack: () -> Unit,
) {
    val logs = AutomationLog.logs.value
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuckDuckGoTheme.colors.backgrounds.background),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuckDuckGoTheme.colors.backgrounds.surface)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(drawable.ic_arrow_left_24),
                    contentDescription = stringResource(R.string.automation_back),
                    tint = DuckDuckGoTheme.colors.icons.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "View Logs",
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${logs.size} entries",
                    color = DuckDuckGoTheme.colors.text.secondary,
                    fontSize = 13.sp,
                )
            }
            if (logs.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(
                        painter = painterResource(drawable.ic_fire_24),
                        contentDescription = "Clear all",
                        tint = DuckDuckGoTheme.colors.icons.primary,
                    )
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No logs yet.\nFill a form to see it here.",
                    color = DuckDuckGoTheme.colors.text.secondary,
                    fontSize = 15.sp,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                logs.forEach { log ->
                    val isExpanded = expandedId == log.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = DuckDuckGoTheme.colors.backgrounds.surface,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                expandedId = if (isExpanded) null else log.id
                            }
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(drawable.ic_globe_24),
                                contentDescription = null,
                                tint = DuckDuckGoTheme.colors.icons.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.website,
                                    color = DuckDuckGoTheme.colors.text.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${log.fieldsFilled} fields · ${log.groupName}",
                                    color = DuckDuckGoTheme.colors.text.secondary,
                                    fontSize = 12.sp,
                                )
                            }
                            IconButton(onClick = { AutomationLog.delete(log.id) }) {
                                Icon(
                                    painter = painterResource(drawable.ic_fire_24),
                                    contentDescription = "Delete",
                                    tint = DuckDuckGoTheme.colors.text.destructive,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = dateFormat.format(Date(log.timestamp)),
                            color = DuckDuckGoTheme.colors.text.secondary,
                            fontSize = 11.sp,
                        )

                        if (isExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Data used:",
                                color = DuckDuckGoTheme.colors.text.secondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            log.entriesUsed.forEach { e ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                ) {
                                    Text(
                                        text = e.fieldType,
                                        color = DuckDuckGoTheme.colors.text.secondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(90.dp),
                                    )
                                    Text(
                                        text = e.value,
                                        color = DuckDuckGoTheme.colors.text.primary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    if (confirmClear) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                .clickable { confirmClear = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(
                        color = DuckDuckGoTheme.colors.backgrounds.surface,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { }
                    .padding(20.dp),
            ) {
                Text(
                    text = "Clear all logs?",
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    Text(
                        text = "Cancel",
                        color = DuckDuckGoTheme.colors.brand.accentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { confirmClear = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Clear",
                        color = DuckDuckGoTheme.colors.text.destructive,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                AutomationLog.clearAll()
                                confirmClear = false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
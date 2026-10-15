/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun AddDataChoiceScreen(
    onBack: () -> Unit,
    onPasteClick: () -> Unit,
    onUploadClick: () -> Unit,  // ← change this line only
    onGenerateClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuckDuckGoTheme.colors.backgrounds.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuckDuckGoTheme.colors.backgrounds.surface)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(drawable.ic_arrow_left_24),
                    contentDescription = stringResource(R.string.automation_back),
                    tint = DuckDuckGoTheme.colors.icons.primary,
                )
            }
            Text(
                text = stringResource(R.string.automation_add_data_title),
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.automation_add_data_subtitle),
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = DuckDuckGoTheme.colors.backgrounds.surface,
                        shape = RoundedCornerShape(8.dp),
                    ),
            ) {
                ChoiceRow(
                    iconRes = drawable.ic_document_download_24,
                    title = stringResource(R.string.automation_paste_manually),
                    desc = stringResource(R.string.automation_paste_manually_desc),
                    onClick = onPasteClick,
                )
                Divider(
                    color = DuckDuckGoTheme.colors.backgrounds.background,
                    thickness = 1.dp,
                )
                ChoiceRow(
                    iconRes = drawable.ic_downloads_24,
                    title = stringResource(R.string.automation_upload_txt),
                    desc = stringResource(R.string.automation_upload_txt_desc),
                    onClick = onUploadClick,
                )
                Divider(
                    color = DuckDuckGoTheme.colors.backgrounds.background,
                    thickness = 1.dp,
                )
                ChoiceRow(
                    iconRes = drawable.ic_automation_24,
                    title = stringResource(R.string.automation_generate_form_data),
                    desc = stringResource(R.string.automation_generate_form_data_desc),
                    onClick = onGenerateClick,
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    iconRes: Int,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = DuckDuckGoTheme.colors.icons.secondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 12.sp,
            )
        }
    }
}
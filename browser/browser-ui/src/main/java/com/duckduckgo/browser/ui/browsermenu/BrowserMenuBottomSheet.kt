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

package com.duckduckgo.browser.ui.browsermenu

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.coroutineScope
import com.bumptech.glide.Glide
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.menu.TopInContextSection
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.databinding.BottomSheetBrowserMenuBinding
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.ui.view.MenuActionButtonView
import com.duckduckgo.common.ui.view.MenuItemView
import com.duckduckgo.common.ui.view.MenuItemViewSize
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.mobile.android.R.drawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

@SuppressLint("NoBottomSheetDialog")
class BrowserMenuBottomSheet(
    private val context: Context,
    private val faviconManager: FaviconManager,
    private val onDismissListener: () -> Unit,
    private val onMenuItemClickListener: () -> Unit,
    private val topInContextSections: Collection<TopInContextSection> = emptyList(),
    private val currentUrl: Uri? = null,
) : BottomSheetDialog(
    context,
    com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_BottomSheetDialog_EdgeToEdge,
) {
    private val binding = BottomSheetBrowserMenuBinding.inflate(LayoutInflater.from(context))

    private val edgeToEdgeHandler = EdgeToEdgeHandler()

    init {
        setContentView(binding.root)
        binding.root.applyBottomSystemBarInsetPadding()

        binding.includeVpnMenuItem.vpnMenuItem
            .findViewById<MenuItemView>(R.id.menuItemView)
            .setSize(MenuItemViewSize.MEDIUM)

        addTopInContextSections()

        setOnShowListener { dialogInterface ->
            (dialogInterface as BottomSheetDialog).setRoundCorners()
            edgeToEdgeHandler.applyNavigationBarScrim(
                binding.root,
                context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface),
            )

            behavior.apply {
                isDraggable = true
                isHideable = true
                peekHeight = computePeekHeight()
                state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        setOnCancelListener {
            performDismiss()
        }

        automationMenuItem.setOnClickListener {
            val intent = Intent(ACTION_AUTOMATION).apply {
                setPackage(context.packageName)
            }
            context.startActivity(intent)
            dismiss()
        }

        toolsMenuItem.setOnClickListener {
            val intent = Intent(ACTION_TOOLS).apply {
                setPackage(context.packageName)
            }
            context.startActivity(intent)
            dismiss()
        }

        translateMenuItem.setOnClickListener {
            val intent = Intent(ACTION_TRANSLATE).apply {
                setPackage(context.packageName)
            }
            context.startActivity(intent)
            dismiss()
        }

        // Full-width Settings item at the bottom triggers the same action as
        // the (now hidden) action-row Settings button, which is wired externally.
        settingsFullMenuItem.setOnClickListener {
            settingsMenuItem.performClick()
            dismiss()
        }
    }

    private val menuItemsContainer: LinearLayout
        get() = binding.menuItemsContainer

    private val menuActionItemsContainer: LinearLayout
        get() = binding.menuActionItemsContainer

    val backMenuItem: MenuActionButtonView
        get() = binding.backMenuItem

    val forwardMenuItem: MenuActionButtonView
        get() = binding.forwardMenuItem

    val newTabMenuItem: MenuActionButtonView
        get() = binding.newTabMenuItem

    val newDuckChatMenuItem: MenuActionButtonView
        get() = binding.newDuckChatMenuItem

    val settingsMenuItem: MenuActionButtonView
        get() = binding.settingsMenuItem

    val refreshActionMenuItem: MenuActionButtonView
        get() = binding.refreshActionMenuItem

    val defaultBrowserMenuItem: View
        get() = binding.includeDefaultBrowserMenuItem.defaultBrowserMenuItem

    val printPageMenuItem: MenuItemView
        get() = binding.printPageMenuItem

    val downloadPdfMenuItem: MenuItemView
        get() = binding.downloadPdfMenuItem

    val vpnMenuItem: View
        get() = binding.includeVpnMenuItem.vpnMenuItem

    val bookmarksMenuItem: MenuItemView
        get() = binding.bookmarksMenuItem

    val addBookmarksMenuItem: MenuItemView
        get() = binding.addBookmarksMenuItem

    val fireproofWebsiteMenuItem: MenuItemView
        get() = binding.fireproofWebsiteMenuItem

    val findInPageMenuItem: MenuItemView
        get() = binding.findInPageMenuItem

    val autofillMenuItem: MenuItemView
        get() = binding.autofillMenuItem

    val createAliasMenuItem: MenuItemView
        get() = binding.createAliasMenuItem

    val downloadsMenuItem: MenuItemView
        get() = binding.downloadsMenuItem

    val automationMenuItem: MenuItemView
        get() = binding.automationMenuItem

    val toolsMenuItem: MenuItemView
        get() = binding.toolsMenuItem

    val translateMenuItem: MenuItemView
        get() = binding.translateMenuItem

    val settingsFullMenuItem: MenuItemView
        get() = binding.settingsFullMenuItem

    val sharePageMenuItem: MenuItemView
        get() = binding.sharePageMenuItem

    val addToHomeMenuItem: MenuItemView
        get() = binding.addToHomeMenuItem

    val privacyProtectionMenuItem: MenuItemView
        get() = binding.privacyProtectionMenuItem

    val changeBrowserModeMenuItem: MenuItemView
        get() = binding.changeBrowsingModeMenuItem

    val refreshMenuItem: MenuItemView
        get() = binding.refreshMenuItem

    val openInAppMenuItem: MenuItemView
        get() = binding.openInAppMenuItem

    val openInDdgBrowserMenuItem: MenuItemView
        get() = binding.openInDdgBrowserMenuItem

    val runningInDdgBrowserMenuItem: MenuItemView
        get() = binding.runningInDdgBrowserMenuItem

    val brokenSiteMenuItem: MenuItemView
        get() = binding.reportBrokenSiteMenuItem

    val fireMenuItem: MenuItemView
        get() = binding.fireMenuItem

    fun render(viewState: BrowserMenuViewState) {
        hideAllMenuItems()
        showCommonItems()
        when (viewState) {
            is BrowserMenuViewState.Browser -> renderBrowserMenu(viewState)
            is BrowserMenuViewState.NewTabPage -> renderNewTabPageMenu(viewState)
            is BrowserMenuViewState.CustomTabs -> renderCustomTabsMenu(viewState)
            is BrowserMenuViewState.DuckAi -> renderBrowserMenu(null)
        }
    }

    /**
     * Kept for backward compatibility. The Duck.ai section is no longer rendered.
     */
    @Deprecated("Duck.ai section removed from menu")
    fun placeDuckAiSection(atTop: Boolean) {
        // no-op
    }

    private fun addTopInContextSections() {
        val url = currentUrl ?: return
        topInContextSections.forEach { section ->
            binding.topInContextSection.addView(section.getView(url, context) { onContributedItemClicked() })
        }
        binding.topInContextSection.viewTreeObserver.addOnGlobalLayoutListener {
            val shouldShow = binding.topInContextSection.isVisible && binding.topInContextSection.children.any { it.isVisible }
            if (binding.topInContextSectionDivider.isVisible != shouldShow) {
                binding.topInContextSectionDivider.isVisible = shouldShow
            }
        }
    }

    private fun onContributedItemClicked() {
        onMenuItemClickListener()
        dismiss()
    }

    fun onMenuItemClicked(view: View, onClick: () -> Unit) {
        view.setOnClickListener {
            onMenuItemClickListener()
            onClick()
            dismiss()
        }
    }

    fun onMenuItemLongClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnLongClickListener {
            onClick()
            dismiss()
            true
        }
    }

    private fun hideAllMenuItems() {
        menuItemsContainer.children.forEach { menuItem ->
            menuItem.gone()
        }
    }

    /**
     * The action row (Back / Forward / New Tab / Duck.ai / Settings) stays hidden
     * since we're not rendering it any more.
     */
    private fun showCommonItems() {
        menuActionItemsContainer.isVisible = false
    }

    /**
     * Top-level menu shown on real browsing pages.
     *
     * Layout:
     *   Bookmarks
     *   Passwords
     *   Downloads
     *   Translate
     *   ─────
     *   Automation
     *   Tools
     *   ─────
     *   Settings
     */
    private fun renderBrowserMenu(viewState: BrowserMenuViewState.Browser?) {
        binding.topInContextSection.isVisible = false

        // Top group
        bookmarksMenuItem.isVisible = true
        autofillMenuItem.isVisible = true
        downloadsMenuItem.isVisible = true
        downloadsMenuItem.showDotIndicator = viewState?.showDownloadDot ?: false
        translateMenuItem.isVisible = true

        // Middle group
        automationMenuItem.isVisible = true
        toolsMenuItem.isVisible = true

        // Bottom group
        settingsFullMenuItem.isVisible = true

        // Dividers
        binding.librarySectionDivider.isVisible = true
        binding.privacyToolsSectionDivider.isVisible = true
        binding.utilitiesSectionDivider.isVisible = true

        if (viewState != null) {
            renderPageContextHeader(viewState.pageContextHeader)
        }
    }

    private fun renderNewTabPageMenu(viewState: BrowserMenuViewState.NewTabPage) {
        binding.menuHeader.root.isVisible = false
        binding.topInContextSection.isVisible = false

        bookmarksMenuItem.isVisible = true
        autofillMenuItem.isVisible = true
        downloadsMenuItem.isVisible = true
        downloadsMenuItem.showDotIndicator = viewState.showDownloadDot
        translateMenuItem.isVisible = true

        automationMenuItem.isVisible = true
        toolsMenuItem.isVisible = true

        settingsFullMenuItem.isVisible = true

        binding.librarySectionDivider.isVisible = true
        binding.privacyToolsSectionDivider.isVisible = true
        binding.utilitiesSectionDivider.isVisible = true

        renderPageContextHeader(viewState.pageContextHeader)
    }

    private fun renderCustomTabsMenu(viewState: BrowserMenuViewState.CustomTabs) {
        binding.topInContextSection.isVisible = false
        binding.menuHeader.root.isVisible = false

        // Custom Tabs is an external/browser-less experience — show only Settings
        settingsFullMenuItem.isVisible = true
        binding.utilitiesSectionDivider.isVisible = true

        renderPageContextHeader(viewState.pageContextHeader)
    }

    private fun renderPageContextHeader(pageContextHeaderState: PageContextHeaderState) {
        when (pageContextHeaderState) {
            is PageContextHeaderState.Visible -> {
                binding.menuHeader.root.isVisible = true
                binding.menuHeader.headerTitle.isVisible = !pageContextHeaderState.title.isNullOrBlank()
                binding.menuHeader.headerTitle.text = pageContextHeaderState.title
                binding.menuHeader.headerShortUrl.isVisible = true
                binding.menuHeader.headerShortUrl.text = pageContextHeaderState.shortUrl
                val serpLogoUrl = pageContextHeaderState.serpLogoUrl
                if (serpLogoUrl != null) {
                    Glide.with(binding.menuHeader.headerFavicon)
                        .asBitmap()
                        .load(serpLogoUrl)
                        .placeholder(drawable.ic_dax_icon)
                        .error(drawable.ic_dax_icon)
                        .into(binding.menuHeader.headerFavicon)
                } else if (pageContextHeaderState.isDuckDuckGo) {
                    binding.menuHeader.headerFavicon.setImageResource(drawable.ic_dax_icon)
                } else {
                    lifecycle.coroutineScope.launch {
                        faviconManager.loadToViewFromLocalWithPlaceholder(
                            tabId = pageContextHeaderState.tabId,
                            url = pageContextHeaderState.shortUrl,
                            view = binding.menuHeader.headerFavicon,
                        )
                    }
                }
                binding.menuHeader.headerCloseButton.setOnClickListener { performDismiss() }
            }
            is PageContextHeaderState.DuckAi -> {
                binding.menuHeader.root.isVisible = true
                binding.menuHeader.headerTitle.isVisible = !pageContextHeaderState.title.isNullOrBlank()
                binding.menuHeader.headerTitle.text = pageContextHeaderState.title
                binding.menuHeader.headerShortUrl.isVisible = true
                binding.menuHeader.headerShortUrl.text = context.getString(R.string.browserMenuDuckChat)
                binding.menuHeader.headerFavicon.setImageResource(drawable.ic_duck_ai_color_24)
                binding.menuHeader.headerCloseButton.setOnClickListener { performDismiss() }
            }
            is PageContextHeaderState.Error -> {
                binding.menuHeader.root.isVisible = true
                binding.menuHeader.headerTitle.isVisible = false
                binding.menuHeader.headerShortUrl.isVisible = true
                binding.menuHeader.headerShortUrl.text = pageContextHeaderState.shortUrl
                binding.menuHeader.headerFavicon.setImageResource(drawable.ic_globe_24)
                binding.menuHeader.headerCloseButton.setOnClickListener { performDismiss() }
            }
            PageContextHeaderState.Hidden -> {
                binding.menuHeader.root.isVisible = false
            }
        }
    }

    private fun renderVpnMenu(viewState: VpnMenuState) {
        // VPN menu item stays hidden — no longer shown in the menu
        vpnMenuItem.isVisible = false
    }

    internal fun computePeekHeight(): Int {
        return context.resources.displayMetrics.heightPixels * PEEK_HEIGHT_PERCENT / 100
    }

    private fun performDismiss() {
        onDismissListener.invoke()
        dismiss()
    }

    companion object {
        internal const val PEEK_HEIGHT_PERCENT = 90
        const val ACTION_AUTOMATION = "com.duckduckgo.app.action.OPEN_AUTOMATION"
        const val ACTION_TOOLS = "com.duckduckgo.app.action.OPEN_TOOLS"
        const val ACTION_TRANSLATE = "com.duckduckgo.app.action.OPEN_TRANSLATE"
    }
}
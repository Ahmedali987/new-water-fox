/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.Editable
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.transition.doOnEnd
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.automation.AutomationEngine
import com.duckduckgo.app.automation.RotationMode
import com.duckduckgo.app.automation.SiteProfileRepository
import com.duckduckgo.app.automation.VaultRepository
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.api.OmnibarRepository
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.databinding.IncludeNewCustomTabToolbarBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.ItemPressedListener
import com.duckduckgo.app.browser.omnibar.Omnibar.LogoClickListener
import com.duckduckgo.app.browser.omnibar.Omnibar.NativeInputLaunchListener
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarTextState
import com.duckduckgo.app.browser.omnibar.Omnibar.TextListener
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.LaunchNativeInput
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.MoveCaretToFront
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.StartAdBlockingAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.StartCookiesAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.StartTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.EnabledState
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.EasterEggLogo
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.PrivacyShield
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.addressbar.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.addressbar.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.animations.addressbar.TrackersAnimatorListener
import com.duckduckgo.app.browser.omnibar.animations.omnibaranimation.OmnibarAnimationManager
import com.duckduckgo.app.browser.omnibar.model.Decoration
import com.duckduckgo.app.browser.omnibar.model.Decoration.ChangeCustomTabTitle
import com.duckduckgo.app.browser.omnibar.model.Decoration.DisableVoiceSearch
import com.duckduckgo.app.browser.omnibar.model.Decoration.HighlightOmnibarItem
import com.duckduckgo.app.browser.omnibar.model.Decoration.LaunchAdBlockingAnimation
import com.duckduckgo.app.browser.omnibar.model.Decoration.LaunchCookiesAnimation
import com.duckduckgo.app.browser.omnibar.model.Decoration.LaunchTrackersAnimation
import com.duckduckgo.app.browser.omnibar.model.Decoration.Mode
import com.duckduckgo.app.browser.omnibar.model.Decoration.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.model.Decoration.QueueCookiesAnimation
import com.duckduckgo.app.browser.omnibar.model.StateChange
import com.duckduckgo.app.browser.progressbar.PageLoadProgressBar
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.app.global.view.renderIfChanged
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.browser.ui.PulseAnimation
import com.duckduckgo.browser.ui.tabs.TabSwitcherButton
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.replaceTextChangedListener
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.serp.logos.api.SerpEasterEggLogoAnimator
import com.duckduckgo.serp.logos.api.SerpLogos
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.app.global.model.PrivacyShield as PrivacyShieldState
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class OmnibarLayout @JvmOverloads constructor(
    context: Context,
    override val omnibarType: OmnibarType,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle),
    OmnibarView,
    OmnibarBehaviour,
    TrackersAnimatorListener {

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
    ) : this(context, OmnibarType.SINGLE_TOP, attrs, defStyle)

    data class TransitionState(
        val showClearButton: Boolean,
        val showVoiceSearch: Boolean,
        val showTabsMenu: Boolean,
        val showFireIcon: Boolean,
        val showBrowserMenu: Boolean,
        val showBrowserMenuHighlight: Boolean,
        val showChatMenu: Boolean,
        val showSpacer: Boolean,
        val showDuckSidebar: Boolean,
        val showDuckBack: Boolean,
        val isDuckAiMode: Boolean,
        val isNativeInputEnabled: Boolean,
        val isNativeChatInputEnabled: Boolean,
    )

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var duckChat: DuckChat

    @Inject
    lateinit var duckAiFeatureState: DuckAiFeatureState

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var omnibarAnimationManager: OmnibarAnimationManager

    @Inject
    lateinit var serpLogos: SerpLogos

    @Inject
    lateinit var appBrandDesignUpdateToggles: AppBrandDesignUpdateToggles

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var omnibarRepository: OmnibarRepository

    @Inject
    lateinit var clipboardInteractor: ClipboardInteractor

    @Inject
    lateinit var browserMode: BrowserMode

    private var previousTransitionState: TransitionState? = null
    private var lastAppliedShowContextualSheetIcon: Boolean? = null
    private val detectedFieldsObserver = Observer<Int> { count ->
        val showAutomationIcon = count > 0
        aiChatMenu?.isVisible = showAutomationIcon
        aiChatDivider.isVisible = (voiceSearchButton.isVisible || clearTextButton.isVisible) && showAutomationIcon
    }

    private val lifecycleOwner: LifecycleOwner by lazy {
        requireNotNull(findViewTreeLifecycleOwner())
    }

    private val pulseAnimation: PulseAnimation by lazy {
        PulseAnimation(lifecycleOwner)
    }

    private var omnibarTextListener: TextListener? = null
    private var omnibarItemPressedListener: ItemPressedListener? = null
    private var omnibarNativeInputLaunchListener: NativeInputLaunchListener? = null
    private var omnibarLogoClickedListener: LogoClickListener? = null

    private var decoration: Decoration? = null
    private var lastViewMode: Mode? = null
    private var stateBuffer: MutableList<StateChange> = mutableListOf()
    private var customTabToolbarColor: Int = 0
    private var lastAnimatedLogoUrl: String? = null
    private var easterEggLogoAnimator: ObjectAnimator? = null
    private val playEasterEggLogoWiggle = Runnable {
        easterEggLogoAnimator = SerpEasterEggLogoAnimator.playWiggle(daxIcon)
    }

    private val omnibarCardShadow: MaterialCardView by lazy { findViewById(R.id.omniBarContainerShadow) }
    private val omnibarCardView: MaterialCardView by lazy { findViewById(R.id.omniBarContainer) }
    private val iconsContainer: View by lazy { findViewById(R.id.iconsContainer) }
    private val shieldIconPulseAnimationContainer: View by lazy { findViewById(R.id.shieldIconPulseAnimationContainer) }
    private val omniBarContentContainer: View by lazy { findViewById(R.id.omniBarContentContainer) }
    private val backIcon: ImageView by lazy { findViewById(R.id.backIcon) }
    private val customTabToolbarContainerWrapper: ViewGroup by lazy { findViewById(R.id.customTabToolbarContainerWrapper) }
    private val leadingIconContainer: View by lazy { findViewById(R.id.omnibarIconContainer) }
    private val duckAIHeader: View by lazy { findViewById(R.id.duckAIHeader) }
    private val duckAIFreePill: View by lazy { findViewById(R.id.duckAIFreePill) }
    private val duckAISidebar: View by lazy { findViewById(R.id.duckAiSidebar) }
    private val duckAIBack: View by lazy { findViewById(R.id.duckAiBack) }

    private var isFindInPageVisible = false
    private val findInPageLayoutVisibilityChangeListener =
        OnGlobalLayoutListener {
            val isVisible = findInPage.findInPageContainer.isVisible
            if (isFindInPageVisible != isVisible) {
                isFindInPageVisible = isVisible
                if (isVisible) {
                    onFindInPageShown()
                } else {
                    onFindInPageHidden()
                }
            }
        }

    private val omnibarCardMarginTop by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginTop)
    }

    private val omnibarCardMarginBottom by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginBottom)
    }

    private val shieldIconBoxSize by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.toolbarIcon)
    }

    private val rebrandAddressBarRadius by lazy {
        resources.getDimension(CommonR.dimen.rebrandInputRadius)
    }

    private val legacyAddressBarRadius by lazy {
        resources.getDimension(CommonR.dimen.largeShapeCornerRadius)
    }

    private var focusAnimator: ValueAnimator? = null

    init {
        inflate(context, R.layout.view_omnibar, this)

        AndroidSupportInjection.inject(this)

        renderPosition()

        applyAddressBarRebrandRadius(
            appBrandDesignUpdateToggles.addressBar().isEnabled(),
            rebrandAddressBarRadius,
            legacyAddressBarRadius,
            omnibarCardShadow,
            omnibarCardView,
        )

        omnibarCardShadow.addBottomShadow()
    }

    override val findInPage: IncludeFindInPageBinding by lazy {
        IncludeFindInPageBinding.bind(findViewById(R.id.findInPage))
    }
    override val omnibarTextInput: KeyboardAwareEditText by lazy { findViewById(R.id.omnibarTextInput) }
    internal val tabsMenu: TabSwitcherButton by lazy { findViewById(R.id.tabsMenu) }
    internal val fireIconMenu: FrameLayout by lazy { findViewById(R.id.fireIconMenu) }
    internal val plusIconMenu: FrameLayout by lazy { findViewById(R.id.plusIconMenu) }
    internal val aiChatMenu: View? by lazy { findViewById(R.id.aiChatIconMenu) }
    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    internal val browserMenu: FrameLayout by lazy { findViewById(R.id.browserMenu) }
    internal val browserMenuHighlight: View by lazy { findViewById(R.id.browserMenuHighlight) }
    internal val animatedIconBackgroundView: View by lazy { findViewById(R.id.animatedIconBackgroundView) }
    internal val cookieAnimation: LottieAnimationView by lazy { findViewById(R.id.cookieAnimation) }
    internal val adBlockingAnimation: LottieAnimationView by lazy { findViewById(R.id.adBlockingAnimation) }
    internal val sceneRoot: ViewGroup by lazy { findViewById(R.id.sceneRoot) }
    override val omniBarContainer: View by lazy { findViewById(R.id.omniBarContainer) }
    override val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }
    internal val toolbarContainer: ViewGroup by lazy { findViewById(R.id.toolbarContainer) }
    internal val customTabToolbarContainer by lazy {
        IncludeCustomTabToolbarBinding.bind(
            findViewById(R.id.customTabToolbarContainer),
        )
    }
    internal val newCustomTabToolbarContainer by lazy {
        IncludeNewCustomTabToolbarBinding.bind(
            findViewById(R.id.newCustomTabToolbarContainer),
        )
    }
    internal val browserMenuImageView: ImageView by lazy { findViewById(R.id.browserMenuImageView) }
    override val shieldIcon: LottieAnimationView by lazy { findViewById(R.id.shieldIcon) }
    internal val addressBarTrackersBlockedAnimationShieldIcon: LottieAnimationView by lazy {
        findViewById(R.id.addressBarTrackersBlockedAnimationShieldIcon)
    }
    internal val pageLoadingIndicator: ProgressBar by lazy { findViewById(R.id.pageLoadingIndicator) }
    internal val pageLoadProgressBar: PageLoadProgressBar by lazy { findViewById(R.id.pageLoadProgressBar) }
    internal val searchIcon: ImageView by lazy { findViewById(R.id.searchIcon) }
    override val daxIcon: ImageView by lazy { findViewById(R.id.daxIcon) }
    internal val globeIcon: ImageView by lazy { findViewById(R.id.globeIcon) }
    internal val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    internal val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    internal val placeholder: View by lazy { findViewById(R.id.placeholder) }
    internal val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    internal val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    internal val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }
    internal val omniBarButtonTransitionSet: TransitionSet by lazy {
        TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(
                ChangeBounds().apply {
                    duration = omnibarAnimationManager.getChangeBoundsDuration()
                    interpolator = OvershootInterpolator(omnibarAnimationManager.getTension())
                },
            )
            addTransition(
                Fade().apply {
                    duration = omnibarAnimationManager.getFadeDuration()
                    addTarget(clearTextButton)
                    addTarget(voiceSearchButton)
                    addTarget(fireIconMenu)
                    addTarget(plusIconMenu)
                    addTarget(tabsMenu)
                    addTarget(aiChatMenu)
                    addTarget(browserMenu)
                    addTarget(duckAISidebar)
                    addTarget(duckAIBack)
                },
            )
        }
    }
    private val omnibarTextInputClickCatcher: View by lazy { findViewById(R.id.omnibarTextInputClickCatcher) }

    internal fun omnibarViews(): List<View> =
        listOf(
            clearTextButton,
            omnibarTextInput,
            searchIcon,
        )

    internal fun customTabViews(): List<View> =
        listOf(
            newCustomTabToolbarContainer.customTabDomain,
        )

    internal fun shieldViews(): List<View> =
        listOf(
            shieldIcon,
        )

    internal fun customTabShieldViews(): List<View> =
        listOf(
            newCustomTabToolbarContainer.customTabShieldIcon,
        )

    override var isScrollingEnabled: Boolean
        get() {
            return if (isAttachedToWindow) {
                viewModel.viewState.value.scrollingEnabled
            } else {
                true
            }
        }
        set(value) {
            if (isAttachedToWindow) {
                viewModel.onOmnibarScrollingEnabledChanged(value)
            }
        }

    override val isEditing: Boolean
        get() {
            return if (isAttachedToWindow) {
                viewModel.viewState.value.hasFocus
            } else {
                false
            }
        }

    override val isEditingFlow by lazy {
        viewModel.viewState.map {
            isAttachedToWindow && it.hasFocus
        }
    }

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    private val viewModel: OmnibarLayoutViewModel by lazy {
        ViewModelProvider(
            findViewTreeViewModelStoreOwner()!!,
            viewModelFactory,
        )[OmnibarLayoutViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    private var lastSeenPrivacyShield: Pair<PrivacyShieldState, Boolean>? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val coroutineScope = requireNotNull(findViewTreeLifecycleOwner()?.lifecycleScope)

        conflatedStateJob +=
            coroutineScope.launch {
                viewModel.viewState.flowWithLifecycle(lifecycleOwner.lifecycle).collectLatest {
                    render(it)
                }
            }

        conflatedCommandJob +=
            coroutineScope.launch {
                viewModel.commands().flowWithLifecycle(lifecycleOwner.lifecycle).collectLatest {
                    processCommand(it)
                }
            }

        if (lastViewMode != null) {
            logcat { "Omnibar: onAttachedToWindow lastViewMode $lastViewMode" }
            decorateDeferred(lastViewMode!!)
            lastViewMode = null
        }

        if (decoration != null) {
            logcat { "Omnibar: onAttachedToWindow decoration $decoration" }
            decorateDeferred(decoration!!)
            decoration = null
        }

        if (stateBuffer.isNotEmpty()) {
            stateBuffer.forEach {
                reduce(it)
            }
            stateBuffer.clear()
        }

        animatorHelper.setListener(this)
        findInPage.findInPageContainer.viewTreeObserver.addOnGlobalLayoutListener(findInPageLayoutVisibilityChangeListener)
        AutomationEngine.detectedFieldCount.observeForever(detectedFieldsObserver)

        (aiChatMenu as? ImageView)?.let { iv ->
            iv.setImageResource(CommonR.drawable.ic_automation_24)
            iv.setColorFilter(context.getColorFromAttr(CommonR.attr.daxColorPrimaryIcon))
            iv.contentDescription = "Automation"
        }
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        focusAnimator?.cancel()
        findInPage.findInPageContainer.viewTreeObserver.removeOnGlobalLayoutListener(findInPageLayoutVisibilityChangeListener)
        AutomationEngine.detectedFieldCount.removeObserver(detectedFieldsObserver)
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setOmnibarTextListener(textListener: TextListener) {
        omnibarTextListener = textListener

        omnibarTextInput.onFocusChangeListener =
            OnFocusChangeListener { _, hasFocus: Boolean ->
                if (isAttachedToWindow) {
                    viewModel.onOmnibarFocusChanged(hasFocus, omnibarTextInput.text.toString())
                    omnibarTextListener?.onFocusChanged(hasFocus, omnibarTextInput.text.toString())
                }
            }

        omnibarTextInput.onBackKeyListener =
            object : KeyboardAwareEditText.OnBackKeyListener {
                override fun onBackKey(): Boolean {
                    if (isAttachedToWindow) {
                        viewModel.onBackKeyPressed()
                        return omnibarTextListener?.onBackKeyPressed() ?: false
                    }
                    return false
                }
            }

        omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (isAttachedToWindow) {
                    if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        viewModel.onEnterKeyPressed()
                        omnibarTextListener?.onEnterPressed()
                        return@OnEditorActionListener true
                    }
                }
                false
            },
        )

        omnibarTextInput.setOnTouchListener { _, event ->
            if (isAttachedToWindow) {
                viewModel.onUserTouchedOmnibarTextInput(event.action)
            }
            false
        }

        omnibarTextInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                var clearQuery = false
                var deleteLastCharacter = false

                override fun afterTextChanged(editable: Editable) {
                    if (isAttachedToWindow) {
                        viewModel.onInputStateChanged(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                            clearQuery,
                            deleteLastCharacter,
                        )
                    }
                    omnibarTextListener?.onOmnibarTextChanged(
                        OmnibarTextState(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                        ),
                    )
                }

                override fun beforeTextChanged(
                    s: CharSequence,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                    logcat { "Omnibar: $count characters beginning at $start are about to be replaced by new text with length $after" }
                    clearQuery = start == 0 && after == 0
                    deleteLastCharacter = count == 1 && clearQuery
                }
            },
        )

        omnibarTextInput.showSuggestionsListener =
            object : ShowSuggestionsListener {
                override fun showSuggestions() {
                    omnibarTextListener?.onShowSuggestions(
                        OmnibarTextState(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                        ),
                    )
                }
            }
    }

    override fun setOmnibarItemPressedListener(itemPressedListener: ItemPressedListener) {
        omnibarItemPressedListener = itemPressedListener

        tabsMenu.setOnClickListener {
            omnibarItemPressedListener?.onTabsButtonPressed()
        }
        tabsMenu.setOnLongClickListener {
            val longPressHandled = omnibarItemPressedListener?.onTabsButtonLongPressed() ?: false
            return@setOnLongClickListener longPressHandled
        }
        fireIconMenu.setOnClickListener {
            if (isAttachedToWindow) {
                viewModel.onFireIconPressed(isPulseAnimationPlaying())
            }
            omnibarItemPressedListener?.onFireButtonPressed()
        }
        plusIconMenu.setOnClickListener {
            omnibarItemPressedListener?.onPlusButtonPressed(it)
        }
        browserMenu.setOnClickListener {
            omnibarItemPressedListener?.onBrowserMenuPressed()
        }

        // Short tap: fill the current page with the default/pinned group
        aiChatMenu?.setOnClickListener {
            AutomationEngine.fillCurrentPage()
        }

        // Long press: open the DDG-styled automation menu
        aiChatMenu?.setOnLongClickListener { anchor ->
            showAutomationMenu(anchor)
            true
        }

        shieldIcon.setOnClickListener {
            if (isAttachedToWindow) {
                viewModel.onPrivacyShieldButtonPressed()
            }
            omnibarItemPressedListener?.onPrivacyShieldPressed()
        }
        clearTextButton.setOnClickListener {
            if (isAttachedToWindow) {
                viewModel.onClearTextButtonPressed()
            }
        }
        voiceSearchButton.setOnClickListener {
            omnibarItemPressedListener?.onVoiceSearchPressed()
        }
        backIcon.setOnClickListener {
            viewModel.onBackButtonPressed()
            omnibarItemPressedListener?.onBackButtonPressed()
        }
        duckAIHeader.setOnClickListener {
            viewModel.onDuckAiHeaderClicked()
        }
        duckAISidebar.setOnClickListener {
            omnibarItemPressedListener?.onDuckAISidebarButtonPressed()
        }
        duckAIBack.setOnClickListener {
            omnibarItemPressedListener?.onDuckAIBackButtonPressed()
        }
    }

    /**
     * Show the DDG-styled automation popup menu.
     *
     * Groups:
     *  1. Automation controls (teach mode, rotation, reset, rescan, clear)
     *  2. Fill actions (one entry per saved data group)
     */
    private fun showAutomationMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        val domain = AutomationEngine.currentDomain().ifBlank { "this site" }

        val teachLabel = if (AutomationEngine.isTeachModeActive.value) {
            "Stop teach mode"
        } else {
            "Teach mode"
        }

        val modeLabel = if (SiteProfileRepository.rotationMode == RotationMode.SEQUENTIAL) {
            "Rotation: 1-by-1"
        } else {
            "Rotation: random"
        }

        // Section 1: Automation controls
        popup.menu.add(0, MENU_TEACH_MODE, 0, teachLabel)
        popup.menu.add(0, MENU_ROTATION_MODE, 1, modeLabel)
        popup.menu.add(0, MENU_RESET_COUNTER, 2, "Reset counter for $domain")
        popup.menu.add(0, MENU_RESCAN_PAGE, 3, "Rescan page")
        popup.menu.add(0, MENU_CLEAR_RULES, 4, "Clear learned rules")

        // Section 2: Fill actions
        val groups = VaultRepository.groups.value
        groups.forEachIndexed { index, group ->
            val pin = if (group.pinned) "\u2605 " else ""
            val used = SiteProfileRepository.getUsedCountForSite(domain, group.name)
            val itemId = MENU_FILL_BASE + index
            val groupId = if (index == 0) GROUP_FILL else 0
            popup.menu.add(groupId, itemId, 10 + index, "Fill: $pin${group.name} ($used used)")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_TEACH_MODE -> AutomationEngine.toggleTeachMode()
                MENU_ROTATION_MODE -> AutomationEngine.toggleRotationMode()
                MENU_RESET_COUNTER -> AutomationEngine.resetCurrentSiteRotation()
                MENU_RESCAN_PAGE -> AutomationEngine.injectScanner()
                MENU_CLEAR_RULES -> AutomationEngine.clearCurrentSiteProfile()
                else -> {
                    val idx = item.itemId - MENU_FILL_BASE
                    groups.getOrNull(idx)?.let { group ->
                        AutomationEngine.fillCurrentPage(group.name)
                    }
                }
            }
            true
        }
        popup.show()
    }

    override fun setLogoClickListener(logoClickListener: LogoClickListener) {
        omnibarLogoClickedListener = logoClickListener
    }

    fun render(viewState: ViewState) {
        applyAddressBarRebrandRadius(
            viewState.isAddressBarRebrandEnabled,
            rebrandAddressBarRadius,
            legacyAddressBarRadius,
            omnibarCardShadow,
            omnibarCardView,
        )

        when (viewState.viewMode) {
            is ViewMode.CustomTab -> {
                renderCustomTabMode(viewState, viewState.viewMode)
            }

            is ViewMode.DuckAI -> {
                renderDuckAiMode(viewState)
            }

            else -> {
                renderBrowserMode(viewState)
            }
        }

        duckAIHeader.isVisible = viewState.showDuckAIHeader

        leadingIconContainer.isGone = viewState.showDuckAIHeader
        omnibarTextInput.isGone = viewState.showDuckAIHeader

        if (viewState.leadingIconState == PrivacyShield) {
            renderPrivacyShield(
                privacyShieldState = viewState.privacyShield,
                viewMode = viewState.viewMode,
                isAddressBarRebrandEnabled = viewState.isAddressBarRebrandEnabled,
            )
        } else {
            lastSeenPrivacyShield = null
        }

        if (viewState.hasFocus || isFindInPageVisible) {
            animateOmnibarFocusedState(focused = true)
        } else {
            animateOmnibarFocusedState(focused = false)
        }

        omnibarCardShadow.isGone = viewState.viewMode is ViewMode.CustomTab && !isFindInPageVisible

        renderButtons(viewState)

        omniBarButtonTransitionSet.doOnEnd {
            omnibarTextInput.requestLayout()
        }
    }

    private fun renderPosition() {
        if (omnibarType == OmnibarType.SINGLE_BOTTOM) {
            omnibarCardShadow.updateLayoutParams {
                flipOmnibarMargins()
            }

            iconsContainer.updateLayoutParams {
                flipOmnibarMargins()
            }

            duckAISidebar.updateLayoutParams {
                (this as MarginLayoutParams).apply {
                    topMargin = omnibarCardMarginBottom
                    bottomMargin = omnibarCardMarginTop
                }
            }

            duckAIBack.updateLayoutParams {
                (this as MarginLayoutParams).apply {
                    topMargin = omnibarCardMarginBottom
                    bottomMargin = omnibarCardMarginTop
                }
            }

            shieldIconPulseAnimationContainer.updateLayoutParams {
                flipOmnibarMargins()
            }

            shieldIconPulseAnimationContainer.setPadding(
                shieldIconPulseAnimationContainer.paddingLeft,
                shieldIconPulseAnimationContainer.paddingTop,
                shieldIconPulseAnimationContainer.paddingRight,
                6.toPx(),
            )
        }
    }

    fun processCommand(command: Command) {
        when (command) {
            Command.CancelAnimations -> {
                cancelAddressBarAnimations()
            }

            is StartCookiesAnimation -> {
                createCookiesAnimation(command.isCosmetic)
            }

            is StartAdBlockingAnimation -> {
                createAdBlockingAnimation(command.icon, command.text)
            }

            Command.AdBlockingAnimationSuppressed -> {
                omnibarTextListener?.onAdBlockingAnimationSuppressed()
            }

            MoveCaretToFront -> {
                moveCaretToFront()
            }

            is StartTrackersAnimation -> {
                startTrackersAnimation(
                    command.entities,
                    command.isCustomTab,
                    command.isAddressBarTrackersAnimationEnabled,
                    command.useSoftwareRenderingMode,
                )
            }

            is LaunchNativeInput -> {
                omnibarNativeInputLaunchListener?.launchNativeInput(query = command.query)
            }

            is Command.EasterEggLogoClicked -> {
                cancelEasterEggLogoAnimation()
                onLogoClicked(command.url)
            }

            is Command.FocusInputField -> {
                omnibarTextInput.postDelayed(
                    {
                        omnibarTextInput.requestFocus()
                    },
                    200,
                )
            }

            is Command.CancelEasterEggLogoAnimation -> cancelEasterEggLogoAnimation()

            is Command.CopyUrlToClipboard -> {
                copyUrlToClipboardAndShowToast(command)
            }
        }
    }

    private fun copyUrlToClipboardAndShowToast(command: Command.CopyUrlToClipboard) {
        val notificationShownAutomatically = clipboardInteractor.copyToClipboard(toCopy = command.url, isSensitive = false)
        if (!notificationShownAutomatically) {
            Toast.makeText(context, R.string.urlCopiedToClipboard, Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveCaretToFront() {
        omnibarTextInput.post {
            omnibarTextInput.setSelection(0)
        }
    }

    private fun renderTabIcon(viewState: ViewState) {
        if (viewState.shouldUpdateTabsCount) {
            tabsMenu.count = viewState.tabCount
            tabsMenu.hasUnread = viewState.hasUnreadTabs
        }
    }

    private fun renderOmnibarText(viewState: ViewState) {
        if (viewState.updateOmnibarText) {
            omnibarTextInput.setText(viewState.omnibarText)
        }
    }

    private fun renderLeadingIconState(viewState: ViewState) {
        when (val leadingIconState = viewState.leadingIconState) {
            OmnibarLayoutViewModel.LeadingIconState.Search -> {
                searchIcon.show()
                shieldIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            PrivacyShield -> {
                shieldIcon.show()
                searchIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.Dax -> {
                with(daxIcon) {
                    setOnClickListener(null)
                    show()
                    Glide
                        .with(this)
                        .load(CommonR.drawable.ic_ddg_logo)
                        .transition(withCrossFade())
                        .placeholder(daxIcon.drawable)
                        .listener(null)
                        .into(this)
                }
                shieldIcon.gone()
                searchIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.Globe -> {
                globeIcon.show()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.DuckPlayer -> {
                globeIcon.gone()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.setImageResource(resolveDuckPlayerIcon(viewState.isAddressBarRebrandEnabled))
                duckPlayerIcon.show()
            }

            is EasterEggLogo -> {
                daxIcon.show()
                val logoUrl = leadingIconState.logoUrl
                Glide
                    .with(daxIcon)
                    .load(logoUrl)
                    .placeholder(daxIcon.drawable)
                    .transition(withCrossFade())
                    .listener(EasterEggLogoListener(leadingIconState, logoUrl))
                    .into(daxIcon)
                daxIcon.setOnClickListener {
                    viewModel.onLogoClicked()
                }
                globeIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.gone()
            }
        }
    }

    fun renderButtons(viewState: ViewState) {
        val showAutomationIcon = (AutomationEngine.detectedFieldCount.value ?: 0) > 0
        val newTransitionState =
            TransitionState(
                showClearButton = viewState.showClearButton,
                showVoiceSearch = viewState.showVoiceSearch,
                showTabsMenu = viewState.showTabsMenu && !viewState.showFindInPage,
                showFireIcon = viewState.showFireIcon && !viewState.showFindInPage,
                showBrowserMenu = viewState.showBrowserMenu && !viewState.showFindInPage,
                showBrowserMenuHighlight = viewState.showBrowserMenuHighlight,
                showChatMenu = showAutomationIcon,
                showSpacer = viewState.showClearButton || viewState.showVoiceSearch,
                showDuckSidebar = viewState.showDuckAISidebar,
                showDuckBack = viewState.showDuckAISidebar,
                isDuckAiMode = viewState.viewMode is ViewMode.DuckAI,
                isNativeInputEnabled = viewState.isNativeInputEnabled,
                isNativeChatInputEnabled = viewState.isNativeChatInputEnabled,
            )

        if (omnibarAnimationManager.isFeatureEnabled() && previousTransitionState != null &&
            (
                newTransitionState.showFireIcon != previousTransitionState?.showFireIcon ||
                    newTransitionState.isDuckAiMode != previousTransitionState?.isDuckAiMode ||
                    newTransitionState.isNativeInputEnabled != previousTransitionState?.isNativeInputEnabled ||
                    newTransitionState.isNativeChatInputEnabled != previousTransitionState?.isNativeChatInputEnabled ||
                    newTransitionState.showTabsMenu != previousTransitionState?.showTabsMenu ||
                    newTransitionState.showBrowserMenu != previousTransitionState?.showBrowserMenu ||
                    newTransitionState.showDuckSidebar != previousTransitionState?.showDuckSidebar
                )
        ) {
            TransitionManager.beginDelayedTransition(toolbarContainer, omniBarButtonTransitionSet)
        }

        clearTextButton.isVisible = viewState.showClearButton
        voiceSearchButton.isVisible = viewState.showVoiceSearch
        tabsMenu.isVisible = newTransitionState.showTabsMenu
        fireIconMenu.isVisible = shouldShowFireIcon(
            showFireIcon = newTransitionState.showFireIcon,
            isDuckAiMode = newTransitionState.isDuckAiMode,
            isNativeChatInputEnabled = newTransitionState.isNativeChatInputEnabled,
        )
        plusIconMenu.isVisible = shouldShowPlusIcon(
            showFireIcon = newTransitionState.showFireIcon,
            isDuckAiMode = newTransitionState.isDuckAiMode,
            isNativeChatInputEnabled = newTransitionState.isNativeChatInputEnabled,
        )
        aiChatMenu?.isVisible = showAutomationIcon
        browserMenu.isVisible = newTransitionState.showBrowserMenu
        browserMenuHighlight.isVisible = newTransitionState.showBrowserMenuHighlight
        applyAiChatMenuStyling(viewState.showContextualSheetIcon)
        aiChatDivider.isVisible = (viewState.showVoiceSearch || viewState.showClearButton) && showAutomationIcon
        duckAISidebar.isVisible = newTransitionState.showDuckSidebar
        duckAIBack.isVisible = newTransitionState.showDuckBack

        if (omnibarAnimationManager.isFeatureEnabled()) {
            toolbarContainer.requestLayout()
        }

        previousTransitionState = newTransitionState

        applyEnabledState(viewState)

        val showBackArrow = viewState.hasFocus
        if (showBackArrow) {
            backIcon.show()
            searchIcon.gone()
            shieldIcon.gone()
            daxIcon.gone()
            globeIcon.gone()
            duckPlayerIcon.gone()
        } else {
            backIcon.hide()
        }
    }

    private fun renderBrowserMode(viewState: ViewState) {
        ensureBrowserAddressFieldVisible()

        renderOutline(viewState.hasFocus)
        renderOmnibarText(viewState)
        if (viewState.expanded) {
            setExpanded(true, viewState.expandedAnimated)
        }

        if (viewState.isProgressBarUpgradeEnabled) {
            updatePageLoadProgressBar(viewState)
        } else {
            if (viewState.isLoading) {
                pageLoadingIndicator.show()
            }
            smoothProgressAnimator.onNewProgress(viewState.loadingProgress) {
                if (!viewState.isLoading) {
                    pageLoadingIndicator.hide()
                }
            }
        }

        isScrollingEnabled = viewState.scrollingEnabled

        if (viewState.isAddressBarTrackersAnimationEnabled) {
            shieldIconPulseAnimationContainer.updateLayoutParams {
                (this as MarginLayoutParams).apply {
                    marginStart = 1.toPx()
                }
            }
        }

        renderTabIcon(viewState)
        renderPulseAnimation(viewState)

        renderLeadingIconState(viewState)
    }

    private fun ensureBrowserAddressFieldVisible() {
        if (!omniBarContainer.isVisible || omniBarContainer.alpha < 1f) {
            omniBarContainer.alpha = 1f
            omniBarContainer.show()
        }
        if (!omnibarCardShadow.isVisible || omnibarCardShadow.alpha < 1f) {
            omnibarCardShadow.alpha = 1f
            omnibarCardShadow.show()
        }
    }

    private fun renderDuckAiMode(viewState: ViewState) {
        logcat { "Omnibar: renderDuckAiMode $viewState" }
        renderTabIcon(viewState)
        renderOmnibarText(viewState)
        shieldIcon.gone()
        if (viewState.isProgressBarUpgradeEnabled) {
            updatePageLoadProgressBar(viewState)
        } else {
            pageLoadingIndicator.isVisible = viewState.isLoading
        }
        voiceSearchButton.isVisible = viewState.showVoiceSearch
        renderPulseAnimation(viewState)
    }

    private fun updatePageLoadProgressBar(viewState: ViewState) {
        pageLoadProgressBar.setStallDetectionEnabled(viewState.isProgressBarIndeterminateEnabled)
        if (viewState.isLoading) {
            if (!pageLoadProgressBar.isStarted) {
                pageLoadProgressBar.start(viewState.isAnimationUpdateEnabled)
            }
            pageLoadProgressBar.onProgressUpdate(progress = viewState.loadingProgress.toFloat())
        } else {
            if (pageLoadProgressBar.isStarted) {
                pageLoadProgressBar.triggerCompletion()
            }
        }
    }

    private fun renderCustomTabMode(
        viewState: ViewState,
        viewMode: ViewMode.CustomTab,
    ) {
        logcat { "Omnibar: renderCustomTabMode $viewState" }
        configureCustomTabOmnibar(viewMode)
        renderCustomTab(viewMode, viewState.isAddressBarRebrandEnabled)
    }

    override fun decorate(decoration: Decoration) {
        logcat { "Omnibar: decorate $decoration" }
        if (isAttachedToWindow) {
            decorateDeferred(decoration)
        } else {
            if (decoration is Mode) {
                val lastMode = lastViewMode?.viewMode
                if (lastMode !is ViewMode.CustomTab) {
                    lastViewMode = decoration
                }
                this.decoration = null
            } else if (this.decoration == null) {
                this.decoration = decoration
            }
        }
    }

    private fun decorateDeferred(decoration: Decoration) {
        when (decoration) {
            is Mode -> {
                viewModel.onViewModeChanged(decoration.viewMode)
            }

            is PrivacyShieldChanged -> {
                viewModel.onPrivacyShieldChanged(decoration.privacyShield)
            }

            Decoration.CancelAnimations -> {
                cancelAddressBarAnimations()
            }

            is LaunchTrackersAnimation -> {
                viewModel.onAnimationStarted(decoration)
            }

            is LaunchCookiesAnimation -> {
                viewModel.onAnimationStarted(decoration)
            }

            is QueueCookiesAnimation -> {
                createCookiesAnimation(isCosmetic = decoration.isCosmetic, enqueueAnimation = true)
            }

            is LaunchAdBlockingAnimation -> {
                viewModel.onAnimationStarted(decoration)
            }

            is ChangeCustomTabTitle -> {
                viewModel.onCustomTabTitleUpdate(decoration)
            }

            is HighlightOmnibarItem -> {
                viewModel.onHighlightItem(decoration)
            }

            is DisableVoiceSearch -> {
                viewModel.onVoiceSearchDisabled(decoration.url)
            }

            is Decoration.LockForOnboarding -> {
                viewModel.setLocked(decoration.locked)
            }

            is Decoration.CancelEasterEggLogoAnimation -> viewModel.onCancelAddressBarAnimations()
        }
    }

    override fun reduce(stateChange: StateChange) {
        if (isAttachedToWindow) {
            reduceDeferred(stateChange)
        } else {
            logcat { "Omnibar: reduce not attached saving $stateChange" }
            this.stateBuffer.add(stateChange)
        }
    }

    private fun reduceDeferred(stateChange: StateChange) {
        viewModel.onExternalStateChange(stateChange)
    }

    private fun renderPulseAnimation(viewState: ViewState) {
        val fireIconVisible = shouldShowFireIcon(viewState.showFireIcon, viewState.viewMode is ViewMode.DuckAI, viewState.isNativeChatInputEnabled)
        val targetView =
            if (viewState.highlightFireButton.isHighlighted() && fireIconVisible) {
                fireIconImageView
            } else if (viewState.highlightPrivacyShield.isHighlighted() && viewState.leadingIconState == PrivacyShield) {
                placeholder
            } else {
                null
            }

        if (targetView != null) {
            if (pulseAnimation.isActive) {
                pulseAnimation.stop()
            }
            doOnLayout {
                pulseAnimation.playOn(targetView = targetView)
            }
        } else {
            pulseAnimation.stop()
        }
    }

    override fun isPulseAnimationPlaying() = pulseAnimation.isActive

    private fun createCookiesAnimation(
        isCosmetic: Boolean,
        enqueueAnimation: Boolean = false,
    ) {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.createCookiesAnimation(
                context,
                omnibarViews(),
                shieldViews(),
                animatedIconBackgroundView,
                cookieAnimation,
                sceneRoot,
                isCosmetic,
                enqueueAnimation,
                useLightAnimation = if (browserMode == BrowserMode.FIRE) false else null,
            )
        }
    }

    private fun createAdBlockingAnimation(
        icon: Int,
        text: Int,
    ) {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.createAdBlockingAnimation(
                context,
                omnibarViews(),
                shieldViews(),
                animatedIconBackgroundView,
                adBlockingAnimation,
                sceneRoot,
                icon,
                text,
            )
        }
    }

    private fun cancelAddressBarAnimations() {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.cancelAnimations(omnibarViews())
        }
    }

    private fun cancelEasterEggLogoAnimation() {
        daxIcon.removeCallbacks(playEasterEggLogoWiggle)
        easterEggLogoAnimator?.cancel()
        easterEggLogoAnimator = null
        daxIcon.rotation = 0f
    }

    private fun startTrackersAnimation(
        events: List<Entity>?,
        isCustomTab: Boolean,
        isAddressBarTrackersAnimationEnabled: Boolean,
        useSoftwareRenderingMode: Boolean,
    ) {
        if (!isCustomTab) {
            if (isAddressBarTrackersAnimationEnabled) {
                animatorHelper.startAddressBarTrackersAnimation(
                    context = context,
                    addressBarTrackersBlockedAnimationShieldIcon = addressBarTrackersBlockedAnimationShieldIcon,
                    sceneRoot = sceneRoot,
                    animatedIconBackgroundView = animatedIconBackgroundView,
                    omnibarViews = omnibarViews(),
                    shieldViews = shieldViews(),
                    entities = events,
                    useSoftwareRenderingMode = useSoftwareRenderingMode,
                )
            } else {
                animatorHelper.startTrackersAnimation(
                    context = context,
                    shieldAnimationView = shieldIcon,
                    trackersAnimationView = trackersAnimation,
                    omnibarViews = omnibarViews(),
                    entities = events,
                    useLightAnimation = if (browserMode == BrowserMode.FIRE) false else null,
                )
            }
        } else if (omnibarRepository.isNewCustomTabEnabled) {
            val animationBackgroundColor = calculateAnimationBackgroundColor(customTabToolbarColor)
            if (isAddressBarTrackersAnimationEnabled) {
                animatorHelper.startAddressBarTrackersAnimation(
                    context = context,
                    addressBarTrackersBlockedAnimationShieldIcon = newCustomTabToolbarContainer.addressBarTrackersBlockedAnimationShieldIcon,
                    sceneRoot = newCustomTabToolbarContainer.customTabSceneRoot,
                    animatedIconBackgroundView = newCustomTabToolbarContainer.animatedIconBackgroundView,
                    omnibarViews = customTabViews(),
                    shieldViews = customTabShieldViews(),
                    entities = events,
                    customBackgroundColor = animationBackgroundColor,
                    useSoftwareRenderingMode = useSoftwareRenderingMode,
                )
            } else {
                animatorHelper.startTrackersAnimation(
                    context = context,
                    shieldAnimationView = newCustomTabToolbarContainer.customTabShieldIcon,
                    trackersAnimationView = newCustomTabToolbarContainer.trackersAnimation,
                    omnibarViews = customTabViews(),
                    entities = events,
                    useLightAnimation = isColorLight(animationBackgroundColor),
                )
            }
        }
    }

    private fun renderPrivacyShield(
        privacyShieldState: PrivacyShieldState,
        viewMode: ViewMode,
        isAddressBarRebrandEnabled: Boolean,
    ) {
        val renderState = privacyShieldState to isAddressBarRebrandEnabled
        renderIfChanged(renderState, lastSeenPrivacyShield) {
            lastSeenPrivacyShield = renderState
            val shieldIconView =
                if (viewMode is ViewMode.Browser || viewMode is ViewMode.Pdf) {
                    shieldIcon
                } else if (omnibarRepository.isNewCustomTabEnabled) {
                    newCustomTabToolbarContainer.customTabShieldIcon
                } else {
                    customTabToolbarContainer.customTabShieldIcon
                }

            val useLightAnimation = when {
                browserMode == BrowserMode.FIRE -> false
                viewMode is ViewMode.CustomTab &&
                    shouldUseCustomTabToolbarColorForShield(
                        isAddressBarRebrandEnabled = isAddressBarRebrandEnabled,
                        isNewCustomTabEnabled = omnibarRepository.isNewCustomTabEnabled,
                        isDefaultToolbarColor = isDefaultToolbarColor(viewMode.toolbarColor),
                    ) -> isColorLight(viewMode.toolbarColor)
                else -> null
            }

            val boxed = privacyShieldView.setAnimationView(
                shieldIconView,
                privacyShieldState,
                viewMode,
                useLightAnimation,
                isAddressBarRebrandEnabled,
            )
            val rebrandLayout = resolveRebrandPrivacyShieldLayout(privacyShieldState, isAddressBarRebrandEnabled)
            if (boxed && rebrandLayout != null) {
                val slotSize = rebrandLayout.slotSizeDp.toPx(context)
                val contentInset = rebrandLayout.contentInsetDp.toPx(context)
                shieldIconView.updateLayoutParams<MarginLayoutParams> {
                    width = slotSize
                    height = slotSize
                    marginStart = 0
                }
                shieldIconView.setPadding(contentInset, contentInset, contentInset, contentInset)
                shieldIconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            } else if (privacyShieldState != PrivacyShieldState.UNKNOWN) {
                val isNewCustomTab =
                    viewMode is ViewMode.CustomTab && omnibarRepository.isNewCustomTabEnabled
                shieldIconView.updateLayoutParams<MarginLayoutParams> {
                    width = if (isNewCustomTab) {
                        shieldIconBoxSize
                    } else {
                        LayoutParams.WRAP_CONTENT
                    }
                    height = LayoutParams.MATCH_PARENT
                    marginStart = 0
                }
                shieldIconView.setPadding(0, 0, 0, 0)
                shieldIconView.scaleType =
                    if (isNewCustomTab) {
                        ImageView.ScaleType.MATRIX
                    } else {
                        ImageView.ScaleType.FIT_CENTER
                    }
            }
        }
    }

    private fun renderOutline(enabled: Boolean) {
        omniBarContainer.isPressed = enabled
    }

    private fun configureCustomTabOmnibar(customTab: ViewMode.CustomTab) {
        if (omnibarRepository.isNewCustomTabEnabled) {
            customTabToolbarColor = customTab.toolbarColor
            with(newCustomTabToolbarContainer) {
                if (!customTabToolbar.isVisible) {
                    if (omnibarRepository.omnibarType == OmnibarType.SINGLE_BOTTOM) {
                        newCustomTabToolbarContainer.customTabToolbar.updateLayoutParams {
                            flipOmnibarMargins()
                        }
                    }

                    if (customTab.toolbarColor != 0 && !isDefaultToolbarColor(customTab.toolbarColor)) {
                        toolbar.background = customTab.toolbarColor.toDrawable()
                        toolbarContainer.background = customTab.toolbarColor.toDrawable()

                        val foregroundColor = calculateCustomTabForegroundColor(customTab.toolbarColor)
                        customTabCloseIcon.setColorFilter(foregroundColor)
                        browserMenuImageView.setColorFilter(foregroundColor)
                        customTabDomain.setTextColor(foregroundColor)
                    }
                    val animationBackgroundColor = calculateAnimationBackgroundColor(customTab.toolbarColor)

                    val iconBackground = newCustomTabToolbarContainer.animatedIconBackgroundView.background
                    if (iconBackground is android.graphics.drawable.GradientDrawable) {
                        val mutatedDrawable = iconBackground.mutate() as android.graphics.drawable.GradientDrawable
                        mutatedDrawable.setColor(animationBackgroundColor)
                        mutatedDrawable.setStroke(1, animationBackgroundColor)
                    }

                    browserMenu.isVisible = true

                    omniBarContainer.hide()
                    customTabToolbar.show()

                    customTabCloseIcon.setOnClickListener {
                        omnibarItemPressedListener?.onCustomTabClosePressed()
                    }

                    customTabShieldIcon.setOnClickListener { _ ->
                        omnibarItemPressedListener?.onCustomTabPrivacyDashboardPressed()
                    }

                    customTabToolbar.setOnLongClickListener {
                        viewModel.onCustomTabUrlLongClicked()
                        true
                    }
                }
            }
        } else {
            with(customTabToolbarContainer) {
                if (!customTabToolbar.isVisible) {
                    customTabCloseIcon.setOnClickListener {
                        omnibarItemPressedListener?.onCustomTabClosePressed()
                    }

                    customTabShieldIcon.setOnClickListener { _ ->
                        omnibarItemPressedListener?.onCustomTabPrivacyDashboardPressed()
                    }

                    toolbar.background = customTab.toolbarColor.toDrawable()
                    toolbarContainer.background = customTab.toolbarColor.toDrawable()

                    omniBarContainer.hide()
                    customTabToolbar.show()

                    browserMenu.isVisible = true

                    val foregroundColor = calculateCustomTabBackgroundColor(customTab.toolbarColor)
                    customTabCloseIcon.setColorFilter(foregroundColor)
                    customTabDomain.setTextColor(foregroundColor)
                    customTabDomainOnly.setTextColor(foregroundColor)
                    customTabTitle.setTextColor(foregroundColor)
                    browserMenuImageView.setColorFilter(foregroundColor)
                }
            }
        }
    }

    private fun ViewGroup.LayoutParams.flipOmnibarMargins() {
        (this as MarginLayoutParams).apply {
            topMargin = omnibarCardMarginBottom
            bottomMargin = omnibarCardMarginTop
        }
    }

    private fun isDefaultToolbarColor(color: Int): Boolean {
        val defaultLightColor = ContextCompat.getColor(context, CommonR.color.background_background_light)
        val defaultDarkColor = ContextCompat.getColor(context, CommonR.color.background_background_dark)
        return color == defaultLightColor || color == defaultDarkColor
    }

    private fun calculateAddressBarColor(mainToolbarColor: Int): Int {
        return if (isColorLight(mainToolbarColor)) {
            val targetSaturation = 0.55f
            val targetLightness = 0.90f
            val hsl = floatArrayOf(0f, 0f, 0f)
            ColorUtils.colorToHSL(mainToolbarColor, hsl)

            if (hsl[1] < 0.01f) {
                hsl[1] = 0f
            } else {
                hsl[1] = targetSaturation
            }
            hsl[2] = targetLightness

            ColorUtils.HSLToColor(hsl)
        } else {
            ColorUtils.blendARGB(mainToolbarColor, Color.WHITE, 0.20f)
        }
    }

    private fun calculateAnimationBackgroundColor(mainToolbarColor: Int): Int {
        val blendColor = if (isColorLight(mainToolbarColor)) Color.BLACK else Color.WHITE
        return ColorUtils.blendARGB(mainToolbarColor, blendColor, 0.12f)
    }

    private fun renderCustomTab(
        viewMode: ViewMode.CustomTab,
        isAddressBarRebrandEnabled: Boolean,
    ) {
        logcat { "Omnibar: updateCustomTabTitle $decoration" }
        val duckPlayerIcon = resolveDuckPlayerIcon(isAddressBarRebrandEnabled)

        if (omnibarRepository.isNewCustomTabEnabled) {
            with(newCustomTabToolbarContainer) {
                viewMode.domain?.let {
                    customTabDomain.text = viewMode.domain
                    customTabDomain.show()
                }

                customTabShieldIcon.isInvisible = viewMode.showDuckPlayerIcon
                customTabDuckPlayerIcon.setImageResource(duckPlayerIcon)
                customTabDuckPlayerIcon.isVisible = viewMode.showDuckPlayerIcon
            }
        } else {
            with(customTabToolbarContainer) {
                viewMode.domain?.let {
                    customTabDomain.text = viewMode.domain
                    customTabDomainOnly.text = viewMode.domain
                    customTabDomain.show()
                    customTabDomainOnly.show()
                }

                viewMode.title?.let {
                    customTabTitle.text = viewMode.title
                    customTabTitle.show()
                    customTabDomainOnly.hide()
                }

                customTabShieldIcon.isInvisible = viewMode.showDuckPlayerIcon
                customTabDuckPlayerIcon.setImageResource(duckPlayerIcon)
                customTabDuckPlayerIcon.isVisible = viewMode.showDuckPlayerIcon
            }
        }
    }

    private fun calculateCustomTabBackgroundColor(color: Int): Int {
        if (color == 0) {
            return if ((context as DuckDuckGoActivity).isDarkThemeEnabled()) Color.WHITE else Color.BLACK
        }

        if (color == Color.WHITE || Color.alpha(color) < 128) {
            return Color.BLACK
        }
        val greyValue =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt()
        return if (greyValue < 186) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    private fun calculateCustomTabForegroundColor(color: Int): Int {
        return if (isColorLight(color)) Color.BLACK else Color.WHITE
    }

    private fun isColorLight(color: Int): Boolean {
        if (color == 0) {
            return !(context as DuckDuckGoActivity).isDarkThemeEnabled()
        }

        if (color == Color.WHITE || Color.alpha(color) < 128) {
            return true
        }

        val luminance = ColorUtils.calculateLuminance(color)
        return luminance > 0.5
    }

    private fun applyFindInPageTheme(toolbarColor: Int) {
        val backgroundColor = calculateAddressBarColor(toolbarColor)
        val isColorLight = isColorLight(backgroundColor)

        with(findInPage) {
            findInPageContainer.background = backgroundColor.toDrawable()

            val foregroundColor = if (isColorLight) Color.BLACK else Color.WHITE
            val hintColor = if (foregroundColor == Color.WHITE) {
                Color.argb(153, 255, 255, 255)
            } else {
                Color.argb(153, 0, 0, 0)
            }

            findInPageInput.setTextColor(foregroundColor)
            findInPageInput.setHintTextColor(hintColor)
            findInPageMatches.setTextColor(foregroundColor)

            listOf(
                findIcon,
                previousSearchTermButton,
                nextSearchTermButton,
                closeFindInPagePanel,
            ).forEach { imageView ->
                imageView.setColorFilter(foregroundColor)
            }
        }
    }

    private fun onLogoClicked(url: String) {
        omnibarLogoClickedListener?.onClick(url)
    }

    override fun measuredHeight(): Int = measuredHeight

    override fun height(): Int = height

    override fun getTranslation(): Float = translationY

    override fun setTranslation(y: Float) {
        translationY = y
    }

    override var isUiLocked: Boolean = false

    override fun isOmnibarScrollingEnabled(): Boolean = isScrollingEnabled && !isUiLocked

    override fun isBottomNavEnabled(): Boolean = false

    override fun getBehavior(): CoordinatorLayout.Behavior<AppBarLayout> =
        when (omnibarType) {
            OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> TopAppBarBehavior(context, this)
            OmnibarType.SINGLE_BOTTOM -> BottomAppBarBehavior(context, this)
        }

    override fun setExpanded(expanded: Boolean) {
        when (omnibarType) {
            OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> super.setExpanded(expanded)
            OmnibarType.SINGLE_BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded)
        }
    }

    override fun disableViewStateSaving() {
        customTabToolbarContainer.customTabDomainOnly.isSaveEnabled = false
        customTabToolbarContainer.customTabDomain.isSaveEnabled = false
        customTabToolbarContainer.customTabTitle.isSaveEnabled = false
        customTabToolbarContainer.customTabDuckPlayerIcon.isSaveEnabled = false
        customTabToolbarContainer.customTabCloseIcon.isSaveEnabled = false
        customTabToolbarContainer.customTabShieldIcon.isSaveEnabled = false

        findInPage.findInPageContainer.isSaveEnabled = false
        findInPage.findInPageMatches.isSaveEnabled = false
        findInPage.nextSearchTermButton.isSaveEnabled = false
        findInPage.previousSearchTermButton.isSaveEnabled = false
        findInPage.findIcon.isSaveEnabled = false

        newCustomTabToolbarContainer.customTabCloseIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.trackersAnimation.isSaveEnabled = false
        newCustomTabToolbarContainer.customTabShieldIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.addressBarTrackersBlockedAnimationShieldIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.customTabDuckPlayerIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.daxIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.customTabDomain.isSaveEnabled = false

        pageLoadingIndicator.isSaveEnabled = false
        pageLoadProgressBar.isSaveEnabled = false
        shieldIcon.isSaveEnabled = false
        omnibarTextInput.isSaveEnabled = false
    }

    override fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    ) {
        when (omnibarType) {
            OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> super.setExpanded(expanded, animate)
            OmnibarType.SINGLE_BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded)
        }
    }

    override fun setMenuIcon(resId: Int) {
        ContextCompat.getDrawable(context, resId)?.let {
            browserMenuImageView.setImageDrawable(it)
        }
    }

    override fun onAnimationFinished() {
        omnibarTextListener?.onTrackersCountFinished()
    }

    override fun setDraftTextIfNtpOrSerp(query: String) {
        viewModel.setDraftTextIfNtpOrSerp(query)
    }

    private fun enableTextInputClickCatcher(enabled: Boolean) {
        omnibarTextInputClickCatcher.isVisible = enabled

        omnibarTextInput.apply {
            isEnabled = !enabled
            isFocusable = !enabled
            isFocusableInTouchMode = !enabled
        }
    }

    private fun applyEnabledState(viewState: ViewState) {
        val state = viewState.enabledState
        val isLocked = state != EnabledState.ALL
        val nonFireEnabled = state == EnabledState.ALL
        val fireEnabled = state != EnabledState.NONE

        applyEnabled(tabsMenu, nonFireEnabled)
        applyEnabled(browserMenu, nonFireEnabled)
        aiChatMenu?.let { applyEnabled(it, nonFireEnabled) }
        applyEnabled(voiceSearchButton, nonFireEnabled)
        applyEnabled(clearTextButton, nonFireEnabled)
        applyEnabled(duckAISidebar, nonFireEnabled)
        applyEnabled(duckAIHeader, nonFireEnabled)
        applyEnabled(duckAIFreePill, nonFireEnabled)
        applyEnabled(duckAIBack, nonFireEnabled)
        applyEnabled(shieldIcon, nonFireEnabled)
        applyEnabled(plusIconMenu, nonFireEnabled)
        applyEnabled(fireIconMenu, fireEnabled)
        omnibarTextInput.alpha = if (nonFireEnabled) 1.0f else LOCKED_INPUT_ALPHA

        enableTextInputClickCatcher(viewState.showTextInputClickCatcher || isLocked)

        if (isLocked) {
            omnibarTextInputClickCatcher.setOnClickListener(null)
        } else if (omnibarNativeInputLaunchListener != null) {
            omnibarTextInputClickCatcher.setOnClickListener {
                viewModel.onTextInputClickCatcherClicked()
            }
        }
    }

    private fun applyEnabled(
        view: View,
        enabled: Boolean,
    ) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1.0f else LOCKED_INPUT_ALPHA
    }

    private fun applyAiChatMenuStyling(showContextualSheetIcon: Boolean) {
        if (lastAppliedShowContextualSheetIcon == showContextualSheetIcon) return
        lastAppliedShowContextualSheetIcon = showContextualSheetIcon
    }

    override fun setNativeInputLaunchListener(listener: NativeInputLaunchListener) {
        omnibarNativeInputLaunchListener = listener
        omnibarTextInputClickCatcher.setOnClickListener {
            viewModel.onTextInputClickCatcherClicked()
        }
    }

    private fun animateOmnibarFocusedState(focused: Boolean) {
    }

    private fun onFindInPageShown() {
        omniBarContentContainer.hide()
        customTabToolbarContainerWrapper.hide()
        if (viewModel.viewState.value.viewMode is ViewMode.CustomTab) {
            val toolbarColor = (viewModel.viewState.value.viewMode as ViewMode.CustomTab).toolbarColor

            if (!isDefaultToolbarColor(toolbarColor)) {
                applyFindInPageTheme(toolbarColor)
            }
            omniBarContainer.show()
            browserMenu.gone()
        }
        animateOmnibarFocusedState(focused = true)
        viewModel.onFindInPageRequested()
    }

    private fun onFindInPageHidden() {
        omniBarContentContainer.show()
        customTabToolbarContainerWrapper.show()
        if (viewModel.viewState.value.viewMode is ViewMode.CustomTab) {
            omniBarContainer.hide()
            browserMenu.isVisible = viewModel.viewState.value.showBrowserMenu
        }
        if (!viewModel.viewState.value.hasFocus) {
            animateOmnibarFocusedState(focused = false)
        }
        viewModel.onFindInPageDismissed()
    }

    override fun show() {
        visibility = View.VISIBLE
    }

    override fun gone() {
        visibility = View.GONE
    }

    private inner class EasterEggLogoListener(
        private val leadingIconState: EasterEggLogo,
        private val logoUrl: String,
    ) : RequestListener<Drawable> {

        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean,
        ): Boolean = false

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean,
        ): Boolean {
            if (!leadingIconState.isFavourite && logoUrl != lastAnimatedLogoUrl) {
                lastAnimatedLogoUrl = logoUrl
                daxIcon.postDelayed(playEasterEggLogoWiggle, EASTER_EGG_ANIMATION_DELAY_MS)
            }
            return false
        }
    }

    companion object {
        private const val EASTER_EGG_ANIMATION_DELAY_MS = 1000L
        private const val LOCKED_INPUT_ALPHA = 0.4f

        // Popup menu item IDs
        private const val MENU_TEACH_MODE = 1
        private const val MENU_ROTATION_MODE = 2
        private const val MENU_RESET_COUNTER = 3
        private const val MENU_RESCAN_PAGE = 4
        private const val MENU_CLEAR_RULES = 5
        private const val MENU_FILL_BASE = 100

        // Popup menu group IDs (used to render section dividers in the DDG theme)
        private const val GROUP_FILL = 1
    }
}

internal fun shouldUseCustomTabToolbarColorForShield(
    isAddressBarRebrandEnabled: Boolean,
    isNewCustomTabEnabled: Boolean,
    isDefaultToolbarColor: Boolean,
): Boolean = if (isAddressBarRebrandEnabled) {
    !isNewCustomTabEnabled || !isDefaultToolbarColor
} else {
    isNewCustomTabEnabled && !isDefaultToolbarColor
}

internal data class RebrandPrivacyShieldLayout(
    val slotSizeDp: Int,
    val contentInsetDp: Int,
)

internal fun resolveRebrandPrivacyShieldLayout(
    privacyShieldState: PrivacyShieldState,
    isAddressBarRebrandEnabled: Boolean,
): RebrandPrivacyShieldLayout? {
    if (!isAddressBarRebrandEnabled) return null

    return when (privacyShieldState) {
        PrivacyShieldState.PROTECTED -> RebrandPrivacyShieldLayout(slotSizeDp = 44, contentInsetDp = 0)
        PrivacyShieldState.UNPROTECTED,
        PrivacyShieldState.MALICIOUS,
        -> RebrandPrivacyShieldLayout(slotSizeDp = 44, contentInsetDp = 2)
        PrivacyShieldState.UNKNOWN -> null
    }
}

internal fun shouldShowFireIcon(
    showFireIcon: Boolean,
    isDuckAiMode: Boolean,
    isNativeChatInputEnabled: Boolean,
): Boolean = showFireIcon && !(isDuckAiMode && isNativeChatInputEnabled)

internal fun shouldShowPlusIcon(
    showFireIcon: Boolean,
    isDuckAiMode: Boolean,
    isNativeChatInputEnabled: Boolean,
): Boolean = showFireIcon && isDuckAiMode && isNativeChatInputEnabled

@DrawableRes
internal fun resolveDuckPlayerIcon(isAddressBarRebrandEnabled: Boolean): Int =
    if (isAddressBarRebrandEnabled) {
        R.drawable.video_player_color_24_brand_update
    } else {
        R.drawable.video_player_color_24
    }
/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pandacorp.noteui.presentation.utils.views.searchbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.annotation.MenuRes
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MarginLayoutParamsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityManagerCompat
import androidx.core.widget.TextViewCompat
import androidx.customview.view.AbsSavedState
import com.google.android.material.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.internal.ThemeEnforcement
import com.google.android.material.internal.ToolbarUtils
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.MaterialShapeUtils
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.theme.overlay.MaterialThemeOverlay
import com.pandacorp.noteui.presentation.utils.helpers.animateAlpha

@SuppressLint("RestrictedApi", "PrivateResource")
class SearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialSearchBarStyle,
) :
    Toolbar(MaterialThemeOverlay.wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr) {
    /** Returns the main [TextView] which can be used for hint and search text.  */
    private val textView: TextView
    private val layoutInflated: Boolean
    private val defaultMarginsEnabled: Boolean
    private val searchBarAnimationHelper: SearchBarAnimationHelper
    private val defaultNavigationIcon: Drawable?
    private val defaultCountModeIcon: Drawable?
    private val tintNavigationIcon: Boolean
    private val forceDefaultNavigationOnClickListener: Boolean
    private var centerView: View? = null
    private var navigationIconTint: Int? = null
    private var originalNavigationIconBackground: Drawable? = null
    var menuResId = -1
    private var defaultScrollFlagsEnabled: Boolean
    private var backgroundShape: MaterialShapeDrawable? = null
    private val accessibilityManager: AccessibilityManager?
    private val touchExplorationStateChangeListener =
        AccessibilityManagerCompat.TouchExplorationStateChangeListener { enabled: Boolean ->
            isFocusableInTouchMode = enabled
        }
    var isCountModeEnabled = false

    init {
        // Ensure we are using the correctly themed context rather than the context that was passed in.
        val ensuredContext = getContext()
        validateAttributes(attrs)
        defaultNavigationIcon = AppCompatResources.getDrawable(ensuredContext, R.drawable.ic_search_black_24)
        defaultCountModeIcon = AppCompatResources.getDrawable(ensuredContext, R.drawable.ic_arrow_back_black_24)
        searchBarAnimationHelper = SearchBarAnimationHelper()
        val a = ThemeEnforcement.obtainStyledAttributes(
            ensuredContext,
            attrs,
            R.styleable.SearchBar,
            defStyleAttr,
            DEF_STYLE_RES,
        )
        val shapeAppearanceModel =
            ShapeAppearanceModel.builder(ensuredContext, attrs, defStyleAttr, DEF_STYLE_RES).build()
        val elevation = a.getDimension(R.styleable.SearchBar_elevation, 0f)
        defaultMarginsEnabled = a.getBoolean(R.styleable.SearchBar_defaultMarginsEnabled, true)
        defaultScrollFlagsEnabled = a.getBoolean(R.styleable.SearchBar_defaultScrollFlagsEnabled, true)
        val hideNavigationIcon = a.getBoolean(R.styleable.SearchBar_hideNavigationIcon, false)
        forceDefaultNavigationOnClickListener =
            a.getBoolean(R.styleable.SearchBar_forceDefaultNavigationOnClickListener, false)
        tintNavigationIcon = a.getBoolean(R.styleable.SearchBar_tintNavigationIcon, true)
        if (a.hasValue(R.styleable.SearchBar_navigationIconTint)) {
            navigationIconTint = a.getColor(R.styleable.SearchBar_navigationIconTint, -1)
        }
        val textAppearanceResId = a.getResourceId(R.styleable.SearchBar_android_textAppearance, -1)
        val text = a.getString(R.styleable.SearchBar_android_text)
        val hint = a.getString(R.styleable.SearchBar_android_hint)
        val strokeWidth = a.getDimension(R.styleable.SearchBar_strokeWidth, -1f)
        val strokeColor = a.getColor(R.styleable.SearchBar_strokeColor, Color.TRANSPARENT)
        a.recycle()
        if (!hideNavigationIcon) {
            initNavigationIcon()
        }
        isClickable = true
        isFocusable = true
        LayoutInflater.from(ensuredContext).inflate(R.layout.mtrl_search_bar, this)
        layoutInflated = true
        textView = findViewById(R.id.search_bar_text_view)
        ViewCompat.setElevation(this, elevation)
        initTextView(textAppearanceResId, text, hint)
        initBackground(shapeAppearanceModel, elevation, strokeWidth, strokeColor)
        accessibilityManager = getContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        setupTouchExplorationStateChangeListener()
    }

    private fun setupTouchExplorationStateChangeListener() {
        if (accessibilityManager != null) {
            // Handle the case where touch exploration is already enabled.
            if (accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled) {
                isFocusableInTouchMode = true
            }

            // Handle the case where touch exploration state can change while the view is active.
            addOnAttachStateChangeListener(
                object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(ignored: View) {
                        AccessibilityManagerCompat.addTouchExplorationStateChangeListener(
                            accessibilityManager,
                            touchExplorationStateChangeListener,
                        )
                    }

                    override fun onViewDetachedFromWindow(ignored: View) {
                        AccessibilityManagerCompat.removeTouchExplorationStateChangeListener(
                            accessibilityManager,
                            touchExplorationStateChangeListener,
                        )
                    }
                },
            )
        }
    }

    private fun validateAttributes(attributeSet: AttributeSet?) {
        if (attributeSet == null) {
            return
        }
        if (attributeSet.getAttributeValue(NAMESPACE_APP, "title") != null) {
            throw UnsupportedOperationException(
                "SearchBar does not support title. Use hint or text instead.",
            )
        }
        if (attributeSet.getAttributeValue(NAMESPACE_APP, "subtitle") != null) {
            throw UnsupportedOperationException(
                "SearchBar does not support subtitle. Use hint or text instead.",
            )
        }
    }

    private fun initNavigationIcon() {
        // If no navigation icon, set up the default one; otherwise, re-set it for tinting if needed.
        navigationIcon = if (navigationIcon == null) defaultNavigationIcon else navigationIcon

        // Make the navigation icon button decorative (not clickable/focusable) by default so that the
        // overall search bar handles the click. If a navigation icon click listener is set later on,
        // the button will be made clickable/focusable.
        setNavigationIconDecorative(true)
    }

    private fun initTextView(@StyleRes textAppearanceResId: Int, text: String?, hint: String?) {
        if (textAppearanceResId != -1) {
            TextViewCompat.setTextAppearance(textView, textAppearanceResId)
        }
        this.text = text
        this.hint = hint
        if (navigationIcon == null) {
            MarginLayoutParamsCompat.setMarginStart(
                (textView.layoutParams as MarginLayoutParams),
                resources
                    .getDimensionPixelSize(R.dimen.m3_searchbar_text_margin_start_no_navigation_icon),
            )
        }
    }

    private fun initBackground(
        shapeAppearance: ShapeAppearanceModel,
        elevation: Float,
        strokeWidth: Float,
        @ColorInt strokeColor: Int,
    ) {
        backgroundShape = MaterialShapeDrawable(shapeAppearance)
        backgroundShape!!.initializeElevationOverlay(context)
        backgroundShape!!.elevation = elevation
        if (strokeWidth >= 0) {
            backgroundShape!!.setStroke(strokeWidth, strokeColor)
        }
        val backgroundColor = MaterialColors.getColor(this, R.attr.colorSurface)
        val rippleColor = MaterialColors.getColor(this, R.attr.colorControlHighlight)
        val background: Drawable
        backgroundShape!!.fillColor = ColorStateList.valueOf(backgroundColor)
        background = RippleDrawable(ColorStateList.valueOf(rippleColor), backgroundShape, backgroundShape)
        ViewCompat.setBackground(this, background)
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (layoutInflated && centerView == null && child !is ActionMenuView) {
            centerView = child
            centerView!!.alpha = 0f
        }
        super.addView(child, index, params)
    }

    override fun setElevation(elevation: Float) {
        super.setElevation(elevation)
        backgroundShape?.elevation = elevation
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = EditText::class.java.canonicalName
        var text = text
        val isTextEmpty = TextUtils.isEmpty(text)
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            info.hintText = hint
            info.isShowingHintText = isTextEmpty
        }
        if (isTextEmpty) {
            text = hint
        }
        info.text = text
    }

    override fun setNavigationOnClickListener(listener: OnClickListener?) {
        if (forceDefaultNavigationOnClickListener) {
            // Ignore the listener if forcing of the default navigation icon is enabled.
            return
        }
        super.setNavigationOnClickListener(listener)
        setNavigationIconDecorative(listener == null)
    }

    override fun setNavigationIcon(navigationIcon: Drawable?) {
        super.setNavigationIcon(maybeTintNavigationIcon(navigationIcon))
    }

    private fun maybeTintNavigationIcon(navigationIcon: Drawable?): Drawable? {
        if (!tintNavigationIcon || navigationIcon == null) {
            return navigationIcon
        }
        val navigationIconColor: Int = navigationIconTint ?: MaterialColors.getColor(
            this,
            if (navigationIcon === defaultNavigationIcon) R.attr.colorOnSurfaceVariant else R.attr.colorOnSurface,
        )
        val wrappedNavigationIcon = DrawableCompat.wrap(navigationIcon.mutate())
        DrawableCompat.setTint(wrappedNavigationIcon, navigationIconColor)
        return wrappedNavigationIcon
    }

    private fun setNavigationIconDecorative(decorative: Boolean) {
        val navigationIconButton = ToolbarUtils.getNavigationIconButton(this) ?: return
        navigationIconButton.isClickable = !decorative
        navigationIconButton.isFocusable = !decorative
        val navigationIconBackground = navigationIconButton.background
        if (navigationIconBackground != null) {
            // Save original navigation icon background so we can restore it later if needed.
            originalNavigationIconBackground = navigationIconBackground
        }
        // Even if the navigation icon is not clickable/focusable, a ripple will still show up when the
        // parent view (overall search bar) is clicked. So here we set the background to null to avoid
        // that, and restore the original background when the icon becomes clickable.
        navigationIconButton.background = if (decorative) null else originalNavigationIconBackground
    }

    override fun inflateMenu(@MenuRes resId: Int) {
        super.inflateMenu(resId)
        menuResId = resId
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measureCenterView(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutCenterView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MaterialShapeUtils.setParentAbsoluteElevation(this, backgroundShape!!)
        setDefaultMargins()
        setOrClearDefaultScrollFlags()
    }

    /**
     * [SearchBar] does not support the [Toolbar.setTitle] method, or its corresponding
     * xml attribute. Instead, use [.setHint] or [.setText], or their corresponding xml
     * attributes, to provide a text affordance for your [SearchBar].
     */
    override fun setTitle(title: CharSequence) {
        // Don't do anything. SearchBar can't have a title.
        // Note: we can't throw an exception here because setTitle() is called by setSupportActionBar().
    }

    /**
     * [SearchBar] does not support the [Toolbar.setSubtitle] method, or its corresponding
     * xml attribute. Instead, use [.setHint] or [.setText], or their corresponding xml
     * attributes, to provide a text affordance for your [SearchBar].
     */
    override fun setSubtitle(subtitle: CharSequence) {
        // Don't do anything. SearchBar can't have a subtitle.
        // Note: we can't throw an exception here because setSubtitle() is called by
        // ActionBar#setDisplayShowTitleEnabled().
    }

    private fun setDefaultMargins() {
        if (defaultMarginsEnabled && layoutParams is MarginLayoutParams) {
            val marginHorizontal = resources.getDimensionPixelSize(R.dimen.m3_searchbar_margin_horizontal)
            val marginVertical = resources.getDimensionPixelSize(R.dimen.m3_searchbar_margin_vertical)
            val lp = layoutParams as MarginLayoutParams
            lp.leftMargin = defaultIfZero(lp.leftMargin, marginHorizontal)
            lp.topMargin = defaultIfZero(lp.topMargin, marginVertical)
            lp.rightMargin = defaultIfZero(lp.rightMargin, marginHorizontal)
            lp.bottomMargin = defaultIfZero(lp.bottomMargin, marginVertical)
        }
    }

    private fun defaultIfZero(value: Int, defValue: Int) = if (value == 0) defValue else value

    private fun setOrClearDefaultScrollFlags() {
        if (layoutParams is AppBarLayout.LayoutParams) {
            val lp = layoutParams as AppBarLayout.LayoutParams
            if (defaultScrollFlagsEnabled) {
                if (lp.scrollFlags == 0) {
                    lp.scrollFlags = DEFAULT_SCROLL_FLAGS
                }
            } else {
                if (lp.scrollFlags == DEFAULT_SCROLL_FLAGS) {
                    lp.scrollFlags = 0
                }
            }
        }
    }

    private fun measureCenterView(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        centerView?.measure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun layoutCenterView() {
        if (centerView == null) {
            return
        }
        val centerViewWidth = centerView!!.measuredWidth
        val left = measuredWidth / 2 - centerViewWidth / 2
        val right = left + centerViewWidth
        val centerViewHeight = centerView!!.measuredHeight
        val top = measuredHeight / 2 - centerViewHeight / 2
        val bottom = top + centerViewHeight
        layoutChild(centerView!!, left, top, right, bottom)
    }

    private fun layoutChild(child: View, left: Int, top: Int, right: Int, bottom: Int) {
        if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL) {
            child.layout(measuredWidth - right, top, measuredWidth - left, bottom)
        } else {
            child.layout(left, top, right, bottom)
        }
    }

    /** Returns the optional centered child view of this [SearchBar]  */
    fun getCenterView(): View? {
        return centerView
    }

    var text: CharSequence?
        /** Returns the text of main [TextView], which usually represents the search text.  */
        get() = textView.text

        /** Sets the text of main [TextView].  */
        set(text) {
            textView.text = text
        }

    var hint: CharSequence?
        /** Returns the hint of main [TextView].  */
        get() = textView.hint

        /** Sets the hint of main [TextView].  */
        set(hint) {
            textView.hint = hint
        }

    @get:ColorInt
    var strokeColor: Int
        /** Returns the color of the [SearchBar] outline stroke.  */
        get() = backgroundShape!!.strokeColor!!.defaultColor

        /** Sets the color of the [SearchBar] outline stroke.  */
        set(strokeColor) {
            if (this.strokeColor != strokeColor) {
                backgroundShape!!.strokeColor = ColorStateList.valueOf(strokeColor)
            }
        }

    @get:Dimension
    var strokeWidth: Float
        /** Returns the width in pixels of the [SearchBar] outline stroke.  */
        get() = backgroundShape!!.strokeWidth

        /** Sets the width in pixels of the [SearchBar] outline stroke.  */
        set(strokeWidth) {
            if (this.strokeWidth != strokeWidth) {
                backgroundShape!!.strokeWidth = strokeWidth
            }
        }
    val cornerSize: Float
        /** Returns the size in pixels of the [SearchBar] corners.  */
        get() = // Assume all corner sizes are the same.
            backgroundShape!!.topLeftCornerResolvedSize

    /**
     * Stops the on load animation which transitions from the center view to the hint [ ].
     */
    fun stopOnLoadAnimation() {
        searchBarAnimationHelper.stopOnLoadAnimation(this)
    }

    private val isExpanding: Boolean
        /** Returns whether the expand animation is running.  */
        get() = searchBarAnimationHelper.isExpanding

    /** See [SearchBar.expand].  */
    private fun expand(expandedView: View): Boolean {
        return expand(expandedView, null)
    }

    /** See [SearchBar.expand].  */
    private fun expand(expandedView: View, appBarLayout: AppBarLayout?): Boolean {
        return expand(expandedView, appBarLayout, false)
    }

    /**
     * Starts an expand animation, if it's not already started, which transitions from the [ ] to the `expandedView`, e.g., a contextual [Toolbar].
     *
     *
     * Note: If you are using an [AppBarLayout] in conjunction with the [SearchBar],
     * you may pass in a reference to your [AppBarLayout] so that its visibility and offset can
     * be taken into account for the animation.
     *
     * @return whether or not the expand animation was started
     */
    private fun expand(
        expandedView: View,
        appBarLayout: AppBarLayout?,
        skipAnimation: Boolean,
    ): Boolean {
        // Start the expand if the expanded view is not already showing or in the process of expanding,
        // or if the expanded view is collapsing since the final state should be expanded.
        if (expandedView.visibility != VISIBLE && !isExpanding || isCollapsing) {
            searchBarAnimationHelper.startExpandAnimation(
                this,
                expandedView,
                appBarLayout,
                skipAnimation,
            )
            return true
        }
        return false
    }

    private val isCollapsing: Boolean
        /** Returns whether the collapse animation is running.  */
        get() = searchBarAnimationHelper.isCollapsing

    /** See [SearchBar.collapse].  */
    private fun collapse(expandedView: View): Boolean {
        return collapse(expandedView, null)
    }

    /** See [SearchBar.collapse].  */
    private fun collapse(expandedView: View, appBarLayout: AppBarLayout?): Boolean {
        return collapse(expandedView, appBarLayout, false)
    }

    /**
     * Starts a collapse animation, if it's not already started, which transitions from the `expandedView`, e.g., a contextual [Toolbar], to the [SearchBar].
     *
     *
     * Note: If you are using an [AppBarLayout] in conjunction with the [SearchBar],
     * you may pass in a reference to your [AppBarLayout] so that its visibility and offset can
     * be taken into account for the animation.
     *
     * @return whether or not the collapse animation was started
     */
    private fun collapse(
        expandedView: View,
        appBarLayout: AppBarLayout?,
        skipAnimation: Boolean,
    ): Boolean {
        // Start the collapse if the expanded view is showing and not in the process of collapsing, or
        // if the expanded view is expanding since the final state should be collapsed.
        if (expandedView.visibility == VISIBLE && !isCollapsing || isExpanding) {
            searchBarAnimationHelper.startCollapseAnimation(
                this,
                expandedView,
                appBarLayout,
                skipAnimation,
            )
            return true
        }
        return false
    }

    fun startCountMode(withAnimation: Boolean, count: Int, onBackArrowClick: () -> Unit = {}) {
        isCountModeEnabled = true
        if (withAnimation) {
            navigationIcon?.animateAlpha(255, 0, 200) {
                navigationIcon = defaultCountModeIcon
                navigationIcon?.animateAlpha(0, 255, 200)
            }
        } else {
            navigationIcon = defaultCountModeIcon
        }

        setNavigationOnClickListener {
            onBackArrowClick()
        }
        textView.animateAlpha(withAnimation, 1f, 0f, 200) {
            text = null
            hint = count.toString()
            textView.animateAlpha(withAnimation, 0f, 1f, 200)
        }
        val menuIcons = getChildAt(childCount - 1)
        menuIcons.animateAlpha(withAnimation, 1f, 0f, 200) {
            menu.clear()
            inflateMenu(com.pandacorp.noteui.app.R.menu.menu_notes_selection)
            menuIcons.animateAlpha(withAnimation, 0f, 1f, 200)
        }
    }

    fun stopCountMode(restoredText: String?, restoredHint: CharSequence) {
        isCountModeEnabled = false
        navigationIcon?.animateAlpha(255, 0, 200) {
            navigationIcon = defaultNavigationIcon
            navigationIcon?.animateAlpha(0, 255, 200)
        }
        setNavigationOnClickListener(null)
        textView.animateAlpha(true, 1f, 0f, 200) {
            text = restoredText
            hint = restoredHint
            textView.animateAlpha(true, 0f, 1f, 200)
        }
        val menuIcons = getChildAt(childCount - 1)
        menuIcons.animateAlpha(true, 1f, 0f, 200) {
            menu.clear()
            inflateMenu(com.pandacorp.noteui.app.R.menu.menu_main)
            menuIcons.animateAlpha(true, 0f, 1f, 200)
        }
    }

    val compatElevation: Float
        get() = if (backgroundShape != null) backgroundShape!!.elevation else ViewCompat.getElevation(this)

    override fun onSaveInstanceState(): Parcelable {
        val savedState = SavedState(super.onSaveInstanceState())
        val text = text
        savedState.text = text?.toString()
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        text = state.text
    }

    internal class SavedState(superState: Parcelable?) : AbsSavedState(superState!!) {
        var text: String? = null

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeString(text)
        }
    }

    companion object {
        private val DEF_STYLE_RES = R.style.Widget_Material3_SearchBar
        private const val DEFAULT_SCROLL_FLAGS = (
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
                or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP_MARGINS
            )
        private const val NAMESPACE_APP = "http://schemas.android.com/apk/res-auto"
    }
}
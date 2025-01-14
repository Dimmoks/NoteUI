package com.pandacorp.noteui.presentation.ui.screen

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.fragula2.navigation.SwipeBackFragment
import com.github.dhaval2404.imagepicker.ImagePicker
import com.pandacorp.noteui.app.R
import com.pandacorp.noteui.app.databinding.ScreenNoteBinding
import com.pandacorp.noteui.domain.model.ColorItem
import com.pandacorp.noteui.presentation.ui.adapter.notes.ColorsAdapter
import com.pandacorp.noteui.presentation.ui.adapter.notes.ImagesAdapter
import com.pandacorp.noteui.presentation.utils.dialog.CustomBottomSheetDialog
import com.pandacorp.noteui.presentation.utils.dialog.DialogColorPicker
import com.pandacorp.noteui.presentation.utils.helpers.Constants
import com.pandacorp.noteui.presentation.utils.helpers.Utils
import com.pandacorp.noteui.presentation.utils.helpers.changeTextBackgroundColor
import com.pandacorp.noteui.presentation.utils.helpers.changeTextForegroundColor
import com.pandacorp.noteui.presentation.utils.helpers.changeTextGravity
import com.pandacorp.noteui.presentation.utils.helpers.getJson
import com.pandacorp.noteui.presentation.utils.helpers.hideToolbarWhileScrolling
import com.pandacorp.noteui.presentation.utils.helpers.insertImage
import com.pandacorp.noteui.presentation.utils.helpers.makeTextBold
import com.pandacorp.noteui.presentation.utils.helpers.makeTextItalic
import com.pandacorp.noteui.presentation.utils.helpers.setSpannableFromJson
import com.pandacorp.noteui.presentation.utils.helpers.sp
import com.pandacorp.noteui.presentation.utils.views.UndoRedoHelper
import com.pandacorp.noteui.presentation.viewModels.ColorViewModel
import com.pandacorp.noteui.presentation.viewModels.CurrentNoteViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class NoteScreen : Fragment() {
    private var _binding: ScreenNoteBinding? = null
    private val binding get() = _binding!!

    private val navController by lazy { findNavController() }
    private val swipeBackFragment by lazy { requireParentFragment() as SwipeBackFragment }

    private val currentNoteViewModel: CurrentNoteViewModel by sharedViewModel()
    private val colorsViewModel: ColorViewModel by viewModel()

    private val colorsAdapter by lazy {
        ColorsAdapter().apply {
            setOnClickListener { colorItem ->
                if (colorItem.color == ColorItem.ADD) {
                    addColorDialog.show()
                    return@setOnClickListener
                }
                val editText = getFocusedEditText() ?: return@setOnClickListener

                when (currentNoteViewModel.clickedActionMenuButton.value) {
                    Constants.ClickedActionButton.FOREGROUND ->
                        editText.changeTextForegroundColor(colorItem.color)

                    Constants.ClickedActionButton.BACKGROUND ->
                        editText.changeTextBackgroundColor(colorItem.color)

                    Constants.ClickedActionButton.NULL -> return@setOnClickListener
                }
            }

            setOnLongClickListener { colorItem ->
                if (colorItem.color == ColorItem.ADD) {
                    resetColorsDialog.apply {
                        setOnRemoveClickListener {
                            colorsViewModel.removeColor(colorItem)
                            dismiss()
                        }
                        show()
                    }
                } else {
                    colorClickDialog.apply {
                        setOnRemoveClickListener {
                            colorsViewModel.removeColor(colorItem)
                            dismiss()
                        }
                        show()
                    }
                }
            }

            lifecycleScope.launch {
                colorsViewModel.colorsList.collect {
                    submitList(it)
                }
            }
        }
    }
    private val imagesAdapter by lazy {
        ImagesAdapter().apply {
            setOnClickListener { drawable, position ->
                binding.noteBackgroundImageView.setImageDrawable(drawable)
                currentNoteViewModel.note.value!!.background = position.toString()
            }
            val imagesList = mutableListOf<Drawable>().apply {
                Utils.backgroundDrawablesList.forEach {
                    add(ContextCompat.getDrawable(requireContext(), it)!!)
                }
            }
            submitList(imagesList)
        }
    }

    private val addColorDialog by lazy {
        DialogColorPicker(requireActivity()).apply {
            setOnPositiveButtonClick { envelope, _ ->
                colorsViewModel.addColor(ColorItem(color = envelope.color))
            }
        }
    }
    private val resetColorsDialog by lazy {
        CustomBottomSheetDialog(requireContext(), ColorItem.ADD).apply {
            setOnResetClickListener {
                colorsViewModel.resetColors()
                dismiss()
            }
            setOnRemoveAllClickListener {
                colorsViewModel.removeAllColors()
                dismiss()
            }
        }
    }
    private val colorClickDialog by lazy {
        CustomBottomSheetDialog(requireContext(), ColorItem.COLOR)
    }

    private val isHideToolbarWhileScrolling by lazy {
        sp.getBoolean(Constants.Preferences.isHideActionBarOnScrollKey, Constants.Preferences.isHideActionBarOnScrollDefaultValue)
    }

    private lateinit var undoRedoTitleEditTextHelper: UndoRedoHelper
    private lateinit var undoRedoContentEditTextHelper: UndoRedoHelper

    private val pickNoteBackgroundImageResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (it.resultCode == AppCompatActivity.RESULT_OK) {
            val imageUri = it.data!!.data

            binding.noteBackgroundImageView.setImageURI(imageUri)
            currentNoteViewModel.note.value!!.background = imageUri.toString()
        }
    }

    private val pickImageToAddResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (it.resultCode == AppCompatActivity.RESULT_OK) {
            val imageUri = it.data!!.data
            val editText: EditText =
                getFocusedEditText()!!
            // Get a drawable from uri
            imageUri?.also {
                editText.insertImage(imageUri)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val callback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerMenu.isDrawerOpen(GravityCompat.END)) {
                    binding.drawerMenu.closeDrawer(GravityCompat.END)
                } else {
                    navController.popBackStack()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            callback,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ScreenNoteBinding.inflate(inflater, container, false)

        initViews()

        return binding.root
    }

    override fun onPause() {
        val noteItem = currentNoteViewModel.note.value!!
        noteItem.title = binding.titleEditText.getJson()
        noteItem.content = binding.contentEditText.getJson()
        currentNoteViewModel.updateNote(noteItem)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val dialogKey = when {
            addColorDialog.isShowing -> Constants.DialogKey.COLOR_DIALOG
            else -> Constants.DialogKey.NULL
        }
        outState.apply {
            putInt(Constants.DialogKey.KEY, dialogKey)
        }
        currentNoteViewModel.titleEditHistory.value = undoRedoTitleEditTextHelper.editHistory
        currentNoteViewModel.contentEditHistory.value = undoRedoContentEditTextHelper.editHistory
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.apply {
            when (currentNoteViewModel.clickedActionMenuButton.value) {
                Constants.ClickedActionButton.NULL -> {}

                Constants.ClickedActionButton.FOREGROUND -> {
                    binding.actionMenuScrollView.visibility = View.GONE
                    binding.colorsRoot.visibility = View.VISIBLE
                }

                Constants.ClickedActionButton.BACKGROUND -> {
                    binding.actionMenuScrollView.visibility = View.GONE
                    binding.colorsRoot.visibility = View.VISIBLE
                }

                Constants.ClickedActionButton.GRAVITY -> {
                    binding.actionMenuScrollView.visibility = View.GONE
                    binding.gravityRoot.visibility = View.VISIBLE
                }
            }
            // Block swiping, because android doesn't call onDrawerOpened after rotation
            if (binding.drawerMenu.isDrawerOpen(GravityCompat.END)) swipeBackFragment.setScrollingEnabled(false)
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    private fun initViews() {
        Utils.changeNoteBackground(
            currentNoteViewModel.note.value!!.background,
            binding.noteBackgroundImageView,
            isUseGlide = false,
        )

        binding.drawerMenu.setOnApplyWindowInsetsListener { _, windowInsets ->
            val isHorizontal = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            if (isHorizontal) {
                // Don't resize if the user is in the landscape orientation, due to small screen size
                requireActivity().window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN,
                )
                return@setOnApplyWindowInsetsListener windowInsets
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val imeHeight = windowInsets.getInsets(WindowInsets.Type.ime()).bottom
                val layoutParams = (binding.actionMenuRoot.layoutParams as ViewGroup.MarginLayoutParams)
                layoutParams.bottomMargin = imeHeight
                binding.actionMenuRoot.layoutParams = layoutParams
            } else {
                @Suppress("DEPRECATION")
                requireActivity().window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                )
            }
            windowInsets
        }

        addStopSwipeOnTouch(binding.titleEditText)
        addStopSwipeOnTouch(binding.contentEditText)
        addStopSwipeOnTouch(binding.actionMenuRoot)
        addStopSwipeOnTouch(binding.actionMenuScrollView)
        addStopSwipeOnTouch(binding.gravityRoot)
        addStopSwipeOnTouch(binding.colorsRoot)

        binding.toolbar.title = null
        binding.toolbar.background = if (currentNoteViewModel.note.value!!.isShowTransparentActionBar) {
            ColorDrawable(Color.TRANSPARENT)
        } else {
            val tv = TypedValue()
            requireContext().theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
            ColorDrawable(tv.data)
        }
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            val editText = getFocusedEditText()
            if (editText == null) {
                navController.popBackStack()
            } else {
                // Clear focus and close the keyboard
                editText.clearFocus()
                val imm =
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editText.windowToken, 0)
            }
        }
        binding.toolbar.hideToolbarWhileScrolling(isHideToolbarWhileScrolling)
        binding.toolbar.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_note, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.menu_note_hamburger -> {
                            binding.drawerMenu.apply {
                                if (isDrawerOpen(GravityCompat.END)) {
                                    closeDrawer(GravityCompat.END)
                                } else {
                                    openDrawer(GravityCompat.END)
                                }
                            }
                        }

                        R.id.menu_note_extended_undo -> {
                            if (binding.titleEditText.hasFocus()) {
                                if (undoRedoTitleEditTextHelper.canUndo) {
                                    undoRedoTitleEditTextHelper.undo()
                                }
                            }

                            if (binding.contentEditText.hasFocus()) {
                                if (undoRedoContentEditTextHelper.canUndo) {
                                    undoRedoContentEditTextHelper.undo()
                                }
                            }
                        }

                        R.id.menu_note_extended_redo -> {
                            if (binding.titleEditText.hasFocus()) {
                                if (undoRedoContentEditTextHelper.canRedo) {
                                    undoRedoContentEditTextHelper.redo()
                                }
                            }
                            if (binding.contentEditText.hasFocus()) {
                                if (undoRedoContentEditTextHelper.canRedo) {
                                    undoRedoContentEditTextHelper.redo()
                                }
                            }
                        }
                    }
                    return true
                }
            },
            viewLifecycleOwner,
        )
        binding.titleEditText.setOnFocusChangeListener { _, hasFocus ->
            if (isHideToolbarWhileScrolling) {
                binding.toolbar.hideToolbarWhileScrolling(!hasFocus)
            }
            binding.toolbar.menu.clear()
            if (hasFocus) {
                binding.toolbar.inflateMenu(R.menu.menu_note_extended)
            } else {
                binding.toolbar.inflateMenu(R.menu.menu_note)
            }
        }
        binding.contentEditText.setOnFocusChangeListener { _, hasFocus ->
            if (isHideToolbarWhileScrolling) {
                binding.toolbar.hideToolbarWhileScrolling(!hasFocus)
            }
            binding.toolbar.menu.clear()
            if (hasFocus) {
                binding.toolbar.inflateMenu(R.menu.menu_note_extended)
            } else {
                binding.toolbar.inflateMenu(R.menu.menu_note)
            }
        }

        binding.contentEditText.textSize = sp.getInt(
            Constants.Preferences.contentTextSizeKey,
            Constants.Preferences.contentTextSizeDefaultValue,
        ).toFloat()
        binding.titleEditText.textSize = sp.getInt(
            Constants.Preferences.titleTextSizeKey,
            Constants.Preferences.titleTextSizeDefaultValue,
        ).toFloat()

        binding.titleEditText.setSpannableFromJson(currentNoteViewModel.note.value!!.title)
        binding.contentEditText.setSpannableFromJson(currentNoteViewModel.note.value!!.content)

        undoRedoTitleEditTextHelper =
            UndoRedoHelper(binding.titleEditText, currentNoteViewModel.titleEditHistory.value!!)
        undoRedoContentEditTextHelper =
            UndoRedoHelper(binding.contentEditText, currentNoteViewModel.contentEditHistory.value!!)

        initDrawerMenu()

        initActionBottomMenu()
    }

    private fun initDrawerMenu() = CoroutineScope(Dispatchers.Main).launch {
        delay(300) // Add delay to remove lags
        binding.imageRecyclerView.adapter = imagesAdapter

        binding.motionDrawerLayout.attachEditText(
            binding.contentEditText,
            sp.getInt(
                Constants.Preferences.disableDrawerAnimationKey,
                Constants.Preferences.disableDrawerAnimationDefaultValue,
            ),
        )

        binding.motionDrawerLayout.loadLayoutDescription(R.xml.drawer_layout_motion_scene) // Set programmatically to remove lags

        binding.drawerMenu.addDrawerListener(object : DrawerListener {
            override fun onDrawerOpened(drawerView: View) {
                swipeBackFragment.setScrollingEnabled(false)
            }

            override fun onDrawerClosed(drawerView: View) {
                swipeBackFragment.setScrollingEnabled(true)
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            override fun onDrawerStateChanged(newState: Int) {
                if (newState == DrawerLayout.STATE_DRAGGING) {
                    swipeBackFragment.setScrollingEnabled(false)
                }
            }
        })
        binding.expandChangeBackgroundButton.apply {
            val showMoreDrawable =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_show_more_animated)
                    as AnimatedVectorDrawable
            val showLessDrawable =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_show_less_animated)
                    as AnimatedVectorDrawable
            setOnClickListener {
                if (binding.changeBackgroundExpandableLayout.isExpanded) {
                    binding.changeBackgroundButtonImageView.setImageDrawable(showLessDrawable)
                    showLessDrawable.start()
                    binding.changeBackgroundExpandableLayout.collapse()
                } else {
                    binding.changeBackgroundButtonImageView.setImageDrawable(showMoreDrawable)
                    showMoreDrawable.start()
                    binding.changeBackgroundExpandableLayout.expand()
                }
            }
        }
        binding.drawerMenuSelectImageButton.setOnClickListener {
            val dm = resources.displayMetrics
            val height = dm.heightPixels.toFloat()
            val width = dm.widthPixels.toFloat()

            ImagePicker.with(activity = requireActivity())
                .crop(width, height)
                .createIntent {
                    pickNoteBackgroundImageResult.launch(it)
                }
        }
        binding.drawerMenuResetButton.setOnClickListener {
            val tv = TypedValue()
            requireContext().theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
            val colorBackground = tv.data
            binding.noteBackgroundImageView.setImageDrawable(
                ColorDrawable(colorBackground),
            )
            currentNoteViewModel.note.value!!.background = colorBackground.toString()
        }

        binding.transparentActionBarSwitch.apply {
            isChecked = currentNoteViewModel.note.value!!.isShowTransparentActionBar
            setOnCheckedChangeListener { _, isChecked ->
                binding.toolbar.background =
                    if (isChecked) {
                        ColorDrawable(Color.TRANSPARENT)
                    } else {
                        val tv = TypedValue()
                        requireContext().theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
                        ColorDrawable(tv.data)
                    }
                currentNoteViewModel.note.value!!.isShowTransparentActionBar = isChecked
            }
        }
    }

    private fun initActionBottomMenu() {
        binding.colorsRecyclerView.post {
            binding.colorsRecyclerView.adapter = colorsAdapter
            // Code needed to resolve the bug when RecyclerView is not scrollable inside of ViewPager
            binding.colorsRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN ->
                            binding.colorsRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    return false
                }

                override fun onTouchEvent(view: RecyclerView, event: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }

        binding.gravityStartButton.setOnClickListener {
            val editText = getFocusedEditText()
            if (editText != binding.contentEditText) return@setOnClickListener // Don't change the gravity for titleEditText
            editText.changeTextGravity(Gravity.START)
        }
        binding.gravityCenterButton.setOnClickListener {
            val editText = getFocusedEditText()
            if (editText != binding.contentEditText) return@setOnClickListener // Don't change gravity for titleEditText
            editText.changeTextGravity(Gravity.CENTER)
        }
        binding.gravityEndButton.setOnClickListener {
            val editText = getFocusedEditText()
            if (editText != binding.contentEditText) return@setOnClickListener // Don't change gravity for titleEditText
            editText.changeTextGravity(Gravity.END)
        }

        binding.gravityCloseButton.setOnClickListener {
            Utils.animateViewSliding(binding.actionMenuScrollView, binding.gravityRoot)
            currentNoteViewModel.clickedActionMenuButton.value = Constants.ClickedActionButton.NULL
        }

        binding.changeTextForegroundColor.setOnClickListener {
            Utils.animateViewSliding(binding.colorsRoot, binding.actionMenuScrollView)
            currentNoteViewModel.clickedActionMenuButton.value = Constants.ClickedActionButton.FOREGROUND
        }
        binding.changeTextGravity.setOnClickListener {
            Utils.animateViewSliding(binding.gravityRoot, binding.actionMenuScrollView)
            currentNoteViewModel.clickedActionMenuButton.value = Constants.ClickedActionButton.GRAVITY
        }
        binding.changeTextBackgroundColor.setOnClickListener {
            Utils.animateViewSliding(binding.colorsRoot, binding.actionMenuScrollView)
            currentNoteViewModel.clickedActionMenuButton.value = Constants.ClickedActionButton.BACKGROUND
        }

        binding.colorsClearButton.setOnClickListener {
            val editText = getFocusedEditText() ?: return@setOnClickListener
            when (currentNoteViewModel.clickedActionMenuButton.value) {
                Constants.ClickedActionButton.FOREGROUND ->
                    editText.changeTextForegroundColor()

                Constants.ClickedActionButton.BACKGROUND ->
                    editText.changeTextBackgroundColor()

                Constants.ClickedActionButton.NULL ->
                    throw IllegalArgumentException("Value cannot be null when the color buttons were clicked.")
            }
        }
        binding.colorsClose.setOnClickListener {
            Utils.animateViewSliding(binding.actionMenuScrollView, binding.colorsRoot)
            currentNoteViewModel.clickedActionMenuButton.value = Constants.ClickedActionButton.NULL
        }

        binding.addImage.setOnClickListener {
            if (getFocusedEditText() == binding.contentEditText) {
                ImagePicker.Builder(activity = requireActivity()).createIntent { resultIntent ->
                    pickImageToAddResult.launch(resultIntent)
                }
            }
        }

        binding.actionMenuButtonBold.setOnClickListener {
            val editText = getFocusedEditText() ?: return@setOnClickListener
            editText.makeTextBold()
        }
        binding.actionMenuButtonItalic.setOnClickListener {
            val editText = getFocusedEditText() ?: return@setOnClickListener
            editText.makeTextItalic()
        }
    }

    private fun getFocusedEditText(): EditText? {
        return if (binding.titleEditText.isFocused) {
            binding.titleEditText
        } else if (binding.contentEditText.isFocused) {
            binding.contentEditText
        } else {
            null
        }
    }

    private fun addStopSwipeOnTouch(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    swipeBackFragment.setScrollingEnabled(false)

                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    swipeBackFragment.setScrollingEnabled(true)
                }

                MotionEvent.ACTION_CANCEL ->
                    swipeBackFragment.setScrollingEnabled(true)
            }
            return@setOnTouchListener false
        }
    }
}
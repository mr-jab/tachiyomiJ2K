package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.appcompat.widget.TintTypedArray.obtainStyledAttributes
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DialogQuadstateBinding
import eu.kanade.tachiyomi.widget.materialdialogs.TriStateMultiChoiceDialogAdapter
import eu.kanade.tachiyomi.widget.materialdialogs.TriStateMultiChoiceListener

fun Context.materialAlertDialog() = MaterialAlertDialogBuilder(withOriginalWidth())

fun MaterialAlertDialogBuilder.addCheckBoxPrompt(
    @StringRes stringRes: Int,
    isChecked: Boolean = false,
    listener: MaterialAlertDialogBuilderOnCheckClickListener? = null
): MaterialAlertDialogBuilder {
    return addCheckBoxPrompt(context.getString(stringRes), isChecked, listener)
}

fun MaterialAlertDialogBuilder.addCheckBoxPrompt(
    text: CharSequence,
    isChecked: Boolean = false,
    listener: MaterialAlertDialogBuilderOnCheckClickListener? = null
): MaterialAlertDialogBuilder {
    return setMultiChoiceItems(
        arrayOf(text),
        booleanArrayOf(isChecked)
    ) { dialog, _, checked ->
        listener?.onClick(dialog, checked)
    }
}

fun AlertDialog.disableTexts(texts: Array<String>) {
    val listView = listView ?: return
    listView.setOnHierarchyChangeListener(
        object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View) {
                val text = (child as? AppCompatCheckedTextView)?.text ?: return
                if (texts.contains(text)) {
                    child.setOnClickListener(null)
                    child.isEnabled = false
                } else {
                    child.isEnabled = true
                }
            }

            override fun onChildViewRemoved(view: View?, view1: View?) {}
        }
    )
}

@SuppressLint("ResourceType")
fun MaterialAlertDialogBuilder.setCustomTitleAndMessage(title: Int, message: String): MaterialAlertDialogBuilder {
    return setCustomTitle(
        MaterialTextView(context).apply {
            setPadding(
                24.dpToPx,
                18.dpToPx,
                24.dpToPx,
                0
            )
            val typedArray = context.obtainStyledAttributes(
                R.attr.materialAlertDialogTheme,
                intArrayOf(R.attr.materialAlertDialogTitleTextStyle, R.attr.materialAlertDialogBodyTextStyle)
            )
            val attrValue = typedArray.getResourceId(0, 0)
            val attrValue2 = typedArray.getResourceId(1, 0)
            typedArray.recycle()
            var typedArray2 = context.obtainStyledAttributes(attrValue, intArrayOf(android.R.attr.textAppearance))
            val attrValue3 = typedArray.getResourceId(0, 0)
            typedArray2.recycle()
            typedArray2 = context.obtainStyledAttributes(attrValue2, intArrayOf(android.R.attr.textAppearance, android.R.attr.textColor))
            val attrValue4 = typedArray.getResourceId(0, 0)
            val attrValue5 = typedArray.getColor(1, 0)
            typedArray2.recycle()
            setTextAppearance(attrValue3)
            text = buildSpannedString {
                append(context.getString(title))
                color(attrValue5) {
                    inSpans(TextAppearanceSpan(context, attrValue4)) { append("\n\n" + message) }
                }
            }
        }
    )
}

/**
 * A variant of listItemsMultiChoice that allows for checkboxes that supports 4 states instead.
 */
@CheckResult
internal fun MaterialAlertDialogBuilder.setTriStateItems(
    message: String? = null,
    items: List<CharSequence>,
    disabledIndices: IntArray? = null,
    initialSelection: IntArray = IntArray(items.size),
    selection: TriStateMultiChoiceListener
): MaterialAlertDialogBuilder {
    val binding = DialogQuadstateBinding.inflate(LayoutInflater.from(context))
    binding.list.layoutManager = LinearLayoutManager(context)
    binding.list.adapter = TriStateMultiChoiceDialogAdapter(
        dialog = this,
        items = items,
        disabledItems = disabledIndices,
        initialSelection = initialSelection,
        listener = selection
    )
    val updateScrollIndicators = {
        binding.scrollIndicatorUp.isVisible = binding.list.canScrollVertically(-1)
        binding.scrollIndicatorDown.isVisible = binding.list.canScrollVertically(1)
    }
    binding.list.setOnScrollChangeListener { _, _, _, _, _ ->
        updateScrollIndicators()
    }
    binding.list.post {
        updateScrollIndicators()
    }

    if (message != null) {
        binding.message.text = message
        binding.message.isVisible = true
    }
    return setView(binding.root)
}

val DialogInterface.isPromptChecked: Boolean
    get() = (this as? AlertDialog)?.listView?.isItemChecked(0) ?: false

fun interface MaterialAlertDialogBuilderOnCheckClickListener {
    fun onClick(var1: DialogInterface?, var3: Boolean)
}

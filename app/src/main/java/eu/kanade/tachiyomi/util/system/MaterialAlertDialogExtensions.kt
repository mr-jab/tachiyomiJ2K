package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.style.TextAppearanceSpan
import androidx.annotation.StringRes
import androidx.appcompat.widget.TintTypedArray.obtainStyledAttributes
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.inSpans
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import eu.kanade.tachiyomi.R

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

val DialogInterface.isPromptChecked: Boolean
    get() = (this as? AlertDialog)?.listView?.isItemChecked(0) ?: false

fun interface MaterialAlertDialogBuilderOnCheckClickListener {
    fun onClick(var1: DialogInterface?, var3: Boolean)
}

package eu.kanade.tachiyomi.widget.materialdialogs

import android.view.LayoutInflater
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.databinding.DialogQuadstateBinding

/**
 * A variant of listItemsMultiChoice that allows for checkboxes that supports 4 states instead.
 */
@CheckResult
fun MaterialAlertDialogBuilder.listItemsQuadStateMultiChoice(
    @StringRes title: Int? = null,
    items: List<CharSequence>,
    disabledIndices: IntArray? = null,
    initialSelection: IntArray = IntArray(items.size),
    selection: QuadStateMultiChoiceListener
): MaterialAlertDialogBuilder {
    val binding = DialogQuadstateBinding.inflate(LayoutInflater.from(context))
    binding.list.layoutManager = LinearLayoutManager(context)
    binding.list.adapter = QuadStateMultiChoiceDialogAdapter(
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

    if (title != null) {
        binding.message.setText(title)
        binding.message.isVisible = true
    }
    return setView(binding.root)
}

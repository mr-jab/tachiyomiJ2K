package eu.kanade.tachiyomi.widget.materialdialogs

import android.view.LayoutInflater
import androidx.annotation.CheckResult
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.databinding.DialogQuadstateBinding

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

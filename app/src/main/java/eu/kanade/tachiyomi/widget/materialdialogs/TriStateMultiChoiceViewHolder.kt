package eu.kanade.tachiyomi.widget.materialdialogs

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.widget.TriStateCheckBox

internal class TriStateMultiChoiceViewHolder(
    itemView: View,
    private val adapter: TriStateMultiChoiceDialogAdapter
) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
    init {
        itemView.setOnClickListener(this)
    }

    val controlView: TriStateCheckBox = itemView.findViewById(R.id.md_tri_state_checkbox)

    var isEnabled: Boolean
        get() = itemView.isEnabled
        set(value) {
            itemView.isEnabled = value
            controlView.isEnabled = value
            controlView.alpha = if (value) 1f else 0.75f
        }

    override fun onClick(view: View) = adapter.itemClicked(bindingAdapterPosition)
}

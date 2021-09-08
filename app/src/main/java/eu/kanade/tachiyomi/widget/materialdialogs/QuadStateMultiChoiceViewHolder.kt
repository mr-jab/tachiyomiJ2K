package eu.kanade.tachiyomi.widget.materialdialogs

import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.widget.TriStateCheckBox

class TriItemDiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}

internal class QuadStateMultiChoiceViewHolder(
    itemView: View,
    private val adapter: QuadStateMultiChoiceDialogAdapter
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

package com.dy.sparkmodule.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dy.sparkmodule.R
import com.dy.sparkmodule.model.FriendConfig

class FriendAdapter(
    private val data: MutableList<FriendConfig>,
    private val onDelete: (Int) -> Unit,
    private val onEdit: (Int) -> Unit
) : RecyclerView.Adapter<FriendAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val info: TextView = v.findViewById(R.id.textInfo)
        val edit: Button = v.findViewById(R.id.btnEditText)
        val del: Button = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = data[position]
        val head = if (f.note.isNullOrBlank()) {
            f.name
        } else {
            "${f.name}（备注：${f.note}）"
        }
        holder.info.text = if (f.customText.isNullOrBlank()) {
            "$head\n专属文案：（未设置，用全局轮换）"
        } else {
            "$head\n专属文案：${f.customText}"
        }
        holder.edit.setOnClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) onEdit(idx)
        }
        holder.del.setOnClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) onDelete(idx)
        }
    }

    override fun getItemCount(): Int = data.size
}

package com.example.backlightcontroller

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView 适配器，用于显示按钮列表
 */
class ButtonAdapter(
    private val buttons: List<ButtonConfig>,
    private val onButtonClick: (ButtonConfig) -> Unit
) : RecyclerView.Adapter<ButtonAdapter.ButtonViewHolder>() {

    class ButtonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val button: Button = view.findViewById(R.id.button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_button, parent, false)
        return ButtonViewHolder(view)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        val buttonConfig = buttons[position]
        
        holder.button.apply {
            text = buttonConfig.name
            
            // 设置背景颜色
            try {
                setBackgroundColor(Color.parseColor(buttonConfig.color))
            } catch (e: IllegalArgumentException) {
                // 如果颜色格式无效，使用默认颜色
                setBackgroundColor(Color.GRAY)
            }
            
            // 设置点击事件
            setOnClickListener {
                onButtonClick(buttonConfig)
            }
        }
    }

    override fun getItemCount(): Int = buttons.size
}

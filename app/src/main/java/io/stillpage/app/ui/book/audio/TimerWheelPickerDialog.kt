package io.stillpage.app.ui.book.audio

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.NumberPicker
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.stillpage.app.R
import io.stillpage.app.model.AudioPlay

/**
 * 密码锁样式的定时器选择对话框
 * 提供更直观的滚轮式时间选择界面
 */
class TimerWheelPickerDialog : DialogFragment() {

    private var selectedHours = 0
    private var selectedMinutes = 30 // 默认30分钟
    private lateinit var dialogView: android.view.View
    
    companion object {
        fun show(context: Context) {
            if (context is androidx.fragment.app.FragmentActivity) {
                val dialog = TimerWheelPickerDialog()
                dialog.show(context.supportFragmentManager, "TimerWheelPickerDialog")
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_timer_wheel_picker, null)
        
        setupUI()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("设置定时关闭")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val totalMinutes = selectedHours * 60 + selectedMinutes
                AudioPlay.setTimer(totalMinutes)
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("关闭定时") { _, _ ->
                AudioPlay.setTimer(0)
            }
            .create()
    }
    
    private fun setupUI() {
        val hoursPicker = dialogView.findViewById<NumberPicker>(R.id.picker_hours)
        val minutesPicker = dialogView.findViewById<NumberPicker>(R.id.picker_minutes)
        val timeDisplay = dialogView.findViewById<TextView>(R.id.tv_selected_time)
        
        // 设置小时选择器 (0-5小时)
        hoursPicker.minValue = 0
        hoursPicker.maxValue = 5
        hoursPicker.value = selectedHours
        hoursPicker.wrapSelectorWheel = false
        
        // 设置分钟选择器 (0, 5, 10, 15, ..., 55)
        val minuteValues = (0..55 step 5).toList().toTypedArray()
        val minuteDisplayValues = minuteValues.map { "${it}分" }.toTypedArray()
        minutesPicker.minValue = 0
        minutesPicker.maxValue = minuteValues.size - 1
        minutesPicker.displayedValues = minuteDisplayValues
        minutesPicker.value = minuteValues.indexOf(30) // 默认30分钟
        minutesPicker.wrapSelectorWheel = false
        
        // 更新时间显示
        updateTimeDisplay(timeDisplay)
        
        // 小时选择器监听器
        hoursPicker.setOnValueChangedListener { _, _, newVal ->
            selectedHours = newVal
            updateTimeDisplay(timeDisplay)
        }
        
        // 分钟选择器监听器
        minutesPicker.setOnValueChangedListener { _, _, newVal ->
            selectedMinutes = minuteValues[newVal]
            updateTimeDisplay(timeDisplay)
        }
        
        // 设置预设按钮
        dialogView.findViewById<android.view.View>(R.id.btn_preset_15).setOnClickListener {
            setTimeAndUpdateUI(0, 15, hoursPicker, minutesPicker, timeDisplay, minuteValues)
        }
        
        dialogView.findViewById<android.view.View>(R.id.btn_preset_30).setOnClickListener {
            setTimeAndUpdateUI(0, 30, hoursPicker, minutesPicker, timeDisplay, minuteValues)
        }
        
        dialogView.findViewById<android.view.View>(R.id.btn_preset_60).setOnClickListener {
            setTimeAndUpdateUI(1, 0, hoursPicker, minutesPicker, timeDisplay, minuteValues)
        }
        
        dialogView.findViewById<android.view.View>(R.id.btn_preset_120).setOnClickListener {
            setTimeAndUpdateUI(2, 0, hoursPicker, minutesPicker, timeDisplay, minuteValues)
        }
    }
    
    /**
     * 格式化时间显示
     */
    private fun updateTimeDisplay(textView: TextView) {
        val totalMinutes = selectedHours * 60 + selectedMinutes
        val displayText = when {
            totalMinutes == 0 -> "关闭定时"
            selectedHours == 0 -> "${selectedMinutes}分钟"
            selectedMinutes == 0 -> "${selectedHours}小时"
            else -> "${selectedHours}小时${selectedMinutes}分钟"
        }
        textView.text = displayText
    }
    
    /**
     * 设置时间并更新UI
     */
    private fun setTimeAndUpdateUI(
        hours: Int, 
        minutes: Int, 
        hoursPicker: NumberPicker, 
        minutesPicker: NumberPicker, 
        timeDisplay: TextView,
        minuteValues: Array<Int>
    ) {
        selectedHours = hours
        selectedMinutes = minutes
        
        hoursPicker.value = hours
        
        // 找到最接近的分钟值
        val closestMinuteIndex = minuteValues.indexOfFirst { it >= minutes }.takeIf { it >= 0 } ?: minuteValues.lastIndex
        minutesPicker.value = closestMinuteIndex
        
        updateTimeDisplay(timeDisplay)
    }
}
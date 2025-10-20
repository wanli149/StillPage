package io.stillpage.app.ui.book.audio

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.stillpage.app.R
import io.stillpage.app.databinding.DialogTimerPickerBinding
import io.stillpage.app.model.AudioPlay
import io.stillpage.app.utils.viewbindingdelegate.viewBinding

/**
 * 改进的定时器选择对话框
 * 提供更直观的滑动式时间选择界面和预设选项
 */
class TimerPickerDialog : DialogFragment() {

    private val binding by viewBinding(DialogTimerPickerBinding::bind)
    private var selectedMinutes = 30 // 默认30分钟
    
    // 时间映射：滑动条位置 -> 分钟数
    // 0-35 对应 5分钟到3小时，采用非线性映射提供更好的用户体验
    private val timeMapping = arrayOf(
        5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60,  // 0-11: 5-60分钟，每5分钟
        70, 80, 90, 100, 110, 120,                       // 12-17: 70-120分钟，每10分钟
        135, 150, 165, 180                               // 18-21: 135-180分钟，每15分钟
    )
    
    companion object {
        fun show(context: Context) {
            if (context is androidx.fragment.app.FragmentActivity) {
                val dialog = TimerPickerDialog()
                dialog.show(context.supportFragmentManager, "TimerPickerDialog")
            }
        }
    }

    private lateinit var dialogView: View
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_timer_picker, null)
        
        // 在这里直接设置UI和监听器
        setupUI()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("设置定时关闭")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                // 确保使用选中的时间
                AudioPlay.setTimer(selectedMinutes)
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("关闭定时") { _, _ ->
                AudioPlay.setTimer(0)
            }
            .create()
    }
    
    private fun setupUI() {
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekbar_timer)
        val timeDisplay = dialogView.findViewById<TextView>(R.id.tv_selected_time)
        
        // 设置滑动条
        seekBar.max = timeMapping.size - 1
        seekBar.progress = 5 // 默认30分钟
        
        // 更新时间显示
        updateTimeDisplay(timeDisplay, selectedMinutes)
        
        // 滑动条监听器
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    selectedMinutes = timeMapping[progress]
                    updateTimeDisplay(timeDisplay, selectedMinutes)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 设置预设按钮
        dialogView.findViewById<View>(R.id.btn_preset_15).setOnClickListener {
            selectedMinutes = 15
            setTimeAndUpdateUI(15, seekBar, timeDisplay)
        }
        
        dialogView.findViewById<View>(R.id.btn_preset_30).setOnClickListener {
            selectedMinutes = 30
            setTimeAndUpdateUI(30, seekBar, timeDisplay)
        }
        
        dialogView.findViewById<View>(R.id.btn_preset_60).setOnClickListener {
            selectedMinutes = 60
            setTimeAndUpdateUI(60, seekBar, timeDisplay)
        }
        
        dialogView.findViewById<View>(R.id.btn_preset_120).setOnClickListener {
            selectedMinutes = 120
            setTimeAndUpdateUI(120, seekBar, timeDisplay)
        }
    }
    
    /**
     * 格式化时间显示
     */
    private fun updateTimeDisplay(textView: TextView, minutes: Int) {
        val displayText = when {
            minutes < 60 -> "${minutes}分钟"
            minutes % 60 == 0 -> "${minutes / 60}小时"
            else -> "${minutes / 60}小时${minutes % 60}分钟"
        }
        textView.text = displayText
    }
    
    /**
     * 设置时间并更新UI
     */
    private fun setTimeAndUpdateUI(minutes: Int, seekBar: SeekBar, timeDisplay: TextView) {
        selectedMinutes = minutes
        // 找到最接近的滑动条位置
        val closestIndex = timeMapping.indexOfFirst { it >= minutes }.takeIf { it >= 0 } ?: timeMapping.lastIndex
        seekBar.progress = closestIndex
        updateTimeDisplay(timeDisplay, selectedMinutes)
    }
}
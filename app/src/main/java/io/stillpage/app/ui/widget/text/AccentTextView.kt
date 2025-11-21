package io.stillpage.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.stillpage.app.R
import io.stillpage.app.lib.theme.accentColor
import io.stillpage.app.utils.getCompatColor

class AccentTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    init {
        if (!isInEditMode) {
            setTextColor(context.accentColor)
        } else {
            setTextColor(context.getCompatColor(R.color.accent))
        }
    }

}

package io.stillpage.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ProgressBar
import io.stillpage.app.lib.theme.accentColor
import io.stillpage.app.utils.applyTint

class ThemeProgressBar(context: Context, attrs: AttributeSet) : ProgressBar(context, attrs) {

    init {
        if (!isInEditMode) {
            applyTint(context.accentColor)
        }
    }
}
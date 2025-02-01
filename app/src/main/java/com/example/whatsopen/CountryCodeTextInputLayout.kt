package com.example.whatsopen

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout

class CountryCodeTextInputLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.textInputStyle
) : TextInputLayout(context, attrs, defStyleAttr) {

    private val flagTextView: TextView = TextView(context).apply {
        textSize = 18f
        gravity = Gravity.CENTER_VERTICAL
    }

    private val countryNameTextView: TextView = TextView(context).apply {
        textSize = 14f
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
    }

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(flagTextView)
        addView(countryNameTextView)
    }

    init {
        addView(container)
        // Set consistent padding from the start
        editText?.setPadding(
            70.toPx,  // Fixed left padding
            editText?.paddingTop ?: 0,
            editText?.paddingRight ?: 0,
            editText?.paddingBottom ?: 0
        )
    }

    fun setCountryInfo(flag: String?, countryName: String?) {
        // Find the country_info TextView in the parent view
        (parent?.parent as? ViewGroup)?.findViewById<TextView>(R.id.country_info)?.apply {
            text = if (flag != null && countryName != null) "$flag  $countryName" else null
        }
    }

    private val Int.toPx: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}

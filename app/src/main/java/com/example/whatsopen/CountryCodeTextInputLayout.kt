package com.example.whatsopen

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
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
        setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.country_flag_text_size)
        )
        gravity = Gravity.CENTER_VERTICAL
    }

    private val countryNameTextView: TextView = TextView(context).apply {
        setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.country_name_text_size)
        )
        gravity = Gravity.CENTER_VERTICAL
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
    }

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(flagTextView)
        addView(countryNameTextView)
    }

    init {
        addView(container)
        editText?.setPadding(
            resources.getDimensionPixelSize(R.dimen.country_code_padding_start),
            editText?.paddingTop ?: 0,
            editText?.paddingRight ?: 0,
            editText?.paddingBottom ?: 0
        )
    }

    fun setCountryInfo(flag: String?, countryName: String?) {
        (parent?.parent as? ViewGroup)?.findViewById<TextView>(R.id.country_info)?.apply {
            text = if (flag != null && countryName != null) "$flag  $countryName" else null
        }
    }
}

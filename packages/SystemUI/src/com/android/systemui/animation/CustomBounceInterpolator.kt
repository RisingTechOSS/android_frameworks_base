package com.android.systemui.animation

import android.view.animation.Interpolator
import kotlin.math.cos
import kotlin.math.exp

class CustomBounceInterpolator(private val amplitude: Double, private val frequency: Double) : Interpolator {
    override fun getInterpolation(time: Float): Float {
        return (exp(-time / amplitude) * -cos(frequency * time) + 1).toFloat()
    }
}

package org.futo.inputmethod.latin.uix

import androidx.compose.material3.ColorScheme

// Not exhaustive
fun ColorScheme.differsFrom(other: ColorScheme): Boolean {
    return this.background != other.background
            || this.surface != other.surface
            || this.primary != other.primary
            || this.secondary != other.secondary
            || this.primaryContainer != other.primaryContainer
            || this.secondaryContainer != other.secondaryContainer
            || this.onSurface != other.onSurface
            || this.onBackground != other.onBackground
            || this.onPrimary != other.onPrimary
}
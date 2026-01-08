package org.futo.inputmethod.latin.uix.actions.langspecific.chinese

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource

@Composable
fun StatusButton(text: String, @DrawableRes iconId: Int, iconColor: Color, modifier: Modifier = Modifier) {
    val icon = painterResource(iconId)
    Row {
        Text(text = text)
        Canvas(modifier = modifier) {
            translate(
                left = this.size.width / 2.0f - icon.intrinsicSize.width / 2.0f,
                top = this.size.height / 2.0f - icon.intrinsicSize.height / 2.0f
            ) {
                with(icon) {
                    draw(
                        icon.intrinsicSize,
                        colorFilter = ColorFilter.tint(iconColor)
                    )
                }
            }
        }
    }
}

@Composable
fun RimeStatusMenu(modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier.fillMaxWidth(0.8f),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
//            StatusButton()
//            StatusButton()
        }
//        Row() { }
//        Row() { }
    }
}
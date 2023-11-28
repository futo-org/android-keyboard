package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow

data class EmojiItem(
    val emoji: String,
    val description: String,
    val category: String
)

@Composable
fun EmojiGrid(onClick: (EmojiItem) -> Unit, onExit: () -> Unit, onBackspace: () -> Unit, onSpace: () -> Unit) {
    val context = LocalContext.current

    val emojis = remember {
        val stream = context.resources.openRawResource(R.raw.gemoji)
        val text = stream.bufferedReader().readText()
        val emojidata = Json.parseToJsonElement(text)
        emojidata.jsonArray.map {
            EmojiItem(
                emoji = it.jsonObject["emoji"]!!.jsonPrimitive.content,
                description = it.jsonObject["description"]!!.jsonPrimitive.content,
                category = it.jsonObject["category"]!!.jsonPrimitive.content,
            )
        }
    }

    val spToDp = context.resources.displayMetrics.scaledDensity / context.resources.displayMetrics.density

    Column {
        LazyVerticalGrid(
            columns = GridCells.Adaptive((40.sp * spToDp).value.dp),
            contentPadding = PaddingValues(10.dp),
            modifier = Modifier.weight(1.0f)
        ) {
            items(emojis) { emoji ->
                Box(modifier = Modifier.fillMaxSize().clickable {
                    onClick(emoji)
                }) {
                    Text(
                        text = emoji.emoji,
                        fontSize = 24.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(modifier = Modifier.padding(2.dp, 8.dp, 2.dp, 0.dp)) {
                IconButton(onClick = { onExit() }) {
                    Text("ABC", fontSize = 14.sp)
                }

                Button(onClick = { onSpace() }, modifier = Modifier.weight(1.0f).padding(8.dp, 2.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.33f),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    disabledContainerColor = MaterialTheme.colorScheme.outline,
                    disabledContentColor = MaterialTheme.colorScheme.onBackground,
                ), shape = RoundedCornerShape(32.dp)) {
                    Text("")
                }

                IconButton(onClick = { onBackspace() }) {
                    val icon = painterResource(id = R.drawable.delete)
                    val iconColor = MaterialTheme.colorScheme.onBackground

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        translate(
                            left = this.size.width / 2.0f - icon.intrinsicSize.width / 2.0f,
                            top = this.size.height / 2.0f - icon.intrinsicSize.height / 2.0f
                        ) {
                            with(icon) {
                                draw(
                                    icon.intrinsicSize,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                        iconColor
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EmojiGridPreview() {
    EmojiGrid(
        onBackspace = {},
        onClick = {},
        onExit = {},
        onSpace = {}
    )
}




val EmojiAction = Action(
    icon = R.drawable.smile,
    name = R.string.title_emojis,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        object : ActionWindow {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.title_emojis)
            }

            @Composable
            override fun WindowContents() {
                EmojiGrid(onClick = {
                    manager.typeText(it.emoji)
                }, onExit = {
                    manager.closeActionWindow()
                }, onSpace = {
                    manager.typeText(" ")
                }, onBackspace = {
                    manager.backspace(1)
                })
            }

            override fun close() {

            }
        }
    }
)
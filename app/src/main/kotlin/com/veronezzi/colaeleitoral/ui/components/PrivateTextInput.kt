package com.veronezzi.colaeleitoral.ui.components

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * Text typed inside [content] is not learned by the keyboard (`IME_FLAG_NO_PERSONALIZED_LEARNING`):
 * names of candidates searched in the app reveal political interest, and some keyboards sync what
 * they learn to the cloud. Use it with [PrivateSearchKeyboardOptions], which turns autocorrect off.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoPersonalizedLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val privateRequest = object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val connection = request.createInputConnection(outAttributes)
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    return connection
                }
            }
            nextHandler.startInputMethod(privateRequest)
        },
        content = content,
    )
}

/** Search fields: no autocorrect or capitalization, and the action key reads "Buscar". */
val PrivateSearchKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text,
    imeAction = ImeAction.Search,
)

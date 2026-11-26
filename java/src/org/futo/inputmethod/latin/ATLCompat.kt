package org.futo.inputmethod.latin

import android.content.Context
import android.content.ContextWrapper
import android.inputmethodservice.InputMethodService
import android.os.Build
import org.futo.inputmethod.latin.BuildConfig
import java.lang.reflect.Method

object ATLCompat {
    /** Any uses of this to determine logic is a bug that should be fixed in ATL */
    val IsATL = BuildConfig.DEBUG && Build.PRODUCT == "atl"

    fun launchKeyboard(context: Context, layershell: Boolean) {
        if(!BuildConfig.DEBUG) return
        if(!IsATL) return

        val ime = LatinIME()

        try {
            val attachMethod: Method? =
                ime.javaClass.asSubclass(ContextWrapper::class.java)
                    .getMethod("atl_attach_base_context", Context::class.java)

            if(attachMethod != null) {
                attachMethod.invoke(ime, context)
                println("atl_attach_base_context invoked $attachMethod")
            } else {
                println("atl_attach_base_context method not found")
                return
            }

            val launchMethod: Method? =
                ime.javaClass.asSubclass(InputMethodService::class.java)
                    .getMethod("launch_keyboard", Boolean::class.javaPrimitiveType)

            if(launchMethod != null) {
                launchMethod.invoke(ime, layershell)
                println("launch_keyboard invoked $launchMethod")
            } else {
                println("launch_keyboard method not found")
            }
        } catch(e: Exception) {
            e.printStackTrace()
            println("Launching failed: $e")
        }
    }
}
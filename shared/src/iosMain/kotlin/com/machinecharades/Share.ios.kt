package com.machinecharades

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareAction(): (String) -> Unit = remember {
    { text ->
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root != null) {
            val sheet = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
            root.presentViewController(sheet, animated = true, completion = null)
        }
    }
}

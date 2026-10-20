package com.wyrd.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks whether the active pointer gesture began inside a horizontally scrollable markdown
 * table. The global spring-loaded pull-to-record gesture reads this and stands down, so swiping
 * a table horizontally always pans the table and never arms STT.
 */
class TableTouchState {
    var isTouchingTable by mutableStateOf(false)
}

val LocalTableTouchState = compositionLocalOf { TableTouchState() }

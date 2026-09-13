package com.dacs.attendance.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * How the widget reaches the Hilt graph. Glance creates the widget itself,
 * so it cannot be constructor-injected like everything else.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetStateLoader(): WidgetStateLoader
}

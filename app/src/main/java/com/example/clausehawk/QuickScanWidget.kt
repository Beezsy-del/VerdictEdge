package com.example.clausehawk

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class QuickScanWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    // Intent to launch Camera Scan action in MainActivity
    val scanIntent = Intent(context, MainActivity::class.java).apply {
        action = "com.example.clausehawk.ACTION_QUICK_SCAN"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val scanPendingIntent = PendingIntent.getActivity(
        context,
        101,
        scanIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Intent to launch Document Upload action in MainActivity
    val uploadIntent = Intent(context, MainActivity::class.java).apply {
        action = "com.example.clausehawk.ACTION_UPLOAD_DOC"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val uploadPendingIntent = PendingIntent.getActivity(
        context,
        102,
        uploadIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val views = RemoteViews(context.packageName, R.layout.widget_quick_scan).apply {
        setOnClickPendingIntent(R.id.btn_scan, scanPendingIntent)
        setOnClickPendingIntent(R.id.btn_upload, uploadPendingIntent)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
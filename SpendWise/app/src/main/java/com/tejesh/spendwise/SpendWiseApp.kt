package com.tejesh.spendwise

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.BuildConfig
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.tejesh.spendwise.workers.RecurringTransactionWorker
import com.tejesh.spendwise.workers.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SpendWiseApp : Application(), Configuration.Provider  { // Configuration.Provider -> custom config for workmanager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration // custom config
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "SpendWiseApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Initializing SpendWiseApp")

        if (BuildConfig.DEBUG) {
            configureFirebaseEmulators() // it configures Firebase to connect to local emulators instead of the live production backend, to save costs
        }

        createNotificationChannel()
        scheduleDailyReminder()
        scheduleRecurringTransactionWorker()
    }

    private fun configureFirebaseEmulators() {
        try {
            Log.d(TAG, "Debug build detected - configuring Firebase emulators")
            Firebase.auth.useEmulator("10.0.2.2", 9099)
            Firebase.firestore.useEmulator("10.0.2.2", 8080)
            Log.d(TAG, "Firebase emulators configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring Firebase emulators", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SpendWise Reminders"
            val descriptionText = "Notifications to remind you to log transactions."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                "spendwise_reminder_channel",
                name,
                importance
            ).apply {
                description = descriptionText
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun scheduleDailyReminder() {
        try {
            val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
                1, TimeUnit.DAYS,
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "daily_reminder_work",
                ExistingPeriodicWorkPolicy.KEEP,
                reminderRequest
            )
            Log.d(TAG, "Daily reminder worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling daily reminder", e)
        }
    }

    private fun scheduleRecurringTransactionWorker() {
        try {
            val recurringRequest = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(
                12, TimeUnit.HOURS,
                2, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "recurring_transaction_work",
                ExistingPeriodicWorkPolicy.KEEP,
                recurringRequest
            )
            Log.d(TAG, "Recurring transaction worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling recurring transaction worker", e)
        }
    }
}

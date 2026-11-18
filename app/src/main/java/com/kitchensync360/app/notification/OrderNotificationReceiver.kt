package com.kitchensync360.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kitchensync360.app.data.KitchenSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OrderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WearOrderNotificationManager.ACTION_ACKNOWLEDGE_ORDER) return

        val orderId = intent.getStringExtra(WearOrderNotificationManager.EXTRA_ORDER_ID) ?: return
        val orderNumber = intent.getIntExtra(WearOrderNotificationManager.EXTRA_ORDER_NUMBER, 0)
        val tableNumber = intent.getIntExtra(WearOrderNotificationManager.EXTRA_TABLE_NUMBER, 0)

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                KitchenSyncRepository.getInstance(appContext).handleWatchOrNotificationAcknowledge(
                    orderId = orderId,
                    orderNumber = orderNumber,
                    tableNumber = tableNumber
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

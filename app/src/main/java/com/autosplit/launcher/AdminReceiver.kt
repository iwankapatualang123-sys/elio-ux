package com.autosplit.launcher

import android.app.admin.DeviceAdminReceiver

/** Izin admin perangkat yang hanya meminta kebijakan "force-lock" (kunci layar). */
class AdminReceiver : DeviceAdminReceiver()

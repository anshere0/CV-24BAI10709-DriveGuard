package com.driveguard.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.driveguard.mobile.app.DriveGuardAndroidApp
import com.driveguard.mobile.app.theme.DriveGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DriveGuardTheme {
                DriveGuardAndroidApp()
            }
        }
    }
}

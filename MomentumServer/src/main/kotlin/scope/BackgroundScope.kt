package com.example.scope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object BackgroundScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
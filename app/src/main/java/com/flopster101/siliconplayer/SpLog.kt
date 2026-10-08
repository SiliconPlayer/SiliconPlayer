package com.flopster101.siliconplayer

import android.util.Log

object SpLog {
    const val TAG = "SiliconPlayer"

    fun d(module: String, message: String) {
        Log.d(TAG, "[$module] $message")
    }

    fun d(module: String, message: String, tr: Throwable) {
        Log.d(TAG, "[$module] $message", tr)
    }

    fun i(module: String, message: String) {
        Log.i(TAG, "[$module] $message")
    }

    fun w(module: String, message: String) {
        Log.w(TAG, "[$module] $message")
    }

    fun w(module: String, message: String, tr: Throwable) {
        Log.w(TAG, "[$module] $message", tr)
    }

    fun e(module: String, message: String) {
        Log.e(TAG, "[$module] $message")
    }

    fun e(module: String, message: String, tr: Throwable) {
        Log.e(TAG, "[$module] $message", tr)
    }
}

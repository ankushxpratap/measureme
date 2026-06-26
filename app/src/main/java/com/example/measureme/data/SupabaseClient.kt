package com.example.measureme.data

import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

private const val TAG = "SupabaseClient"

val supabase by lazy {
    Log.d(TAG, "Initializing Supabase client...")
    try {
        val client = createSupabaseClient(
            supabaseUrl = "https://taldiraqsmxysiemxuec.supabase.co",
            supabaseKey = "sb_publishable_DFOxhtacAnHvCfXTG6yREw_uw4IUJHR"
        ) {
            install(Auth)
            install(Postgrest)
        }
        Log.d(TAG, "Supabase client initialized successfully")
        client
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Supabase client", e)
        throw e
    }
}

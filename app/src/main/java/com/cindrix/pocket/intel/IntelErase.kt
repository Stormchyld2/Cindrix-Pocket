package com.cindrix.pocket.intel

import android.content.Context

/** Part of the Hub's erase order: removes the saved Intel history, cached articles, home states and custom feeds from this phone. */
fun eraseIntelData(ctx: Context) {
    try { ctx.deleteDatabase("overwatch.db") } catch (_: Exception) {}
    try { ctx.deleteSharedPreferences("pocket_intel") } catch (_: Exception) {}
}

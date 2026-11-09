package com.cindrix.pocket

/** What the app remembers about a barcode, so a repeat scan needs no typing. */
data class CatalogEntry(
    val barcode: String,
    val name: String,
    val category: String,
    val unit: String,
    /** The use-by date typed last time, offered again in fast mode. */
    val lastUseBy: String = "",
)

/** One line in the batch that will be exported for Cindrix Ops. */
data class BatchItem(
    val scanId: String,
    val name: String,
    val barcode: String,
    val qty: Double,
    val unit: String,
    val useBy: String,
    val category: String,
    val location: String,
    val scannedAt: String,
    /** True when this line takes stock out instead of adding it. */
    val remove: Boolean = false,
)

object Units {
    val all = listOf("ea", "can", "jar", "bottle", "bag", "box", "pack", "lb", "oz", "kg", "g", "L", "gal")
}

object Categories {
    val all = listOf("Food", "Water", "Medical", "Hygiene", "Fuel", "Tools", "Power", "Other")
}

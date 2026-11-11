package com.cindrix.pocket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Everything the screens show, saved to disk on every change. */
class AppState(private val store: Store) {

    val batch = mutableStateListOf<BatchItem>()
    private val catalog = LinkedHashMap<String, CatalogEntry>()

    var callsign by mutableStateOf(store.callsign)
        private set

    var folderUri by mutableStateOf(store.folderUri)
        private set

    var lastLocation by mutableStateOf(store.lastLocation)
        private set

    /** Fast mode: a known item asks only for quantity and use-by date. Off by default. */
    var fastMode by mutableStateOf(store.fastMode)
        private set

    var soundOn by mutableStateOf(store.soundOn)
        private set

    var vibrateOn by mutableStateOf(store.vibrateOn)
        private set

    /** Files saved and waiting to be delivered, oldest first. */
    val stock = mutableStateListOf<StockLine>()

    var hubBase by mutableStateOf(store.hubBase)
        private set
    var hubToken by mutableStateOf(store.hubToken)
        private set

    /** True after the invite QR has been used: the phone then has the Hub's own code for it. */
    val linked: Boolean get() = hubBase != null && hubToken != null

    fun link(base: String, token: String) {
        store.hubBase = base; store.hubToken = token
        hubBase = base; hubToken = token
    }

    fun unlink() {
        store.hubBase = null; store.hubToken = null
        hubBase = null; hubToken = null
    }

    /** The phone's code on reports: the Hub's own once joined (the Hub refuses any other), otherwise made from the callsign. */
    fun deviceCode(): String = hubToken?.let { HubLink.deviceId(it) } ?: TrackSheet.deviceCode(callsign)

    /** The Hub told this phone to erase itself. */
    fun wipeEverything() {
        store.wipeEverything()
        unlink()
        batch.clear(); stock.clear(); outbox.clear()
    }

    val books = Books(store.dir)
    val contactCard = ContactCardStore(store.dir)

    val outbox = mutableStateListOf<String>()
    val outboxStore = Outbox(store.dir)

    init {
        outbox.addAll(outboxStore.list())
        store.loadStockText()?.let { StockList.parse(it) }?.let { stock.addAll(it) }
        batch.addAll(store.loadBatch())
        catalog.putAll(store.loadCatalog())
    }

    fun updateCallsign(value: String) {
        callsign = Callsign.clean(value)
        store.callsign = callsign
    }

    fun setFolder(uri: String?) {
        folderUri = uri
        store.folderUri = uri
    }

    fun rememberLocation(value: String) {
        lastLocation = value
        store.lastLocation = value
    }

    fun updateFastMode(on: Boolean) {
        fastMode = on
        store.fastMode = on
    }

    fun updateSound(on: Boolean) {
        soundOn = on
        store.soundOn = on
    }

    fun updateVibrate(on: Boolean) {
        vibrateOn = on
        store.vibrateOn = on
    }

    fun source(): String = callsign.ifBlank { "Callsign" }

    fun lookup(barcode: String): CatalogEntry? = catalog[barcode]

    /** Remember what a barcode is, so the next scan fills in the name. */
    fun remember(item: BatchItem) {
        if (item.barcode.isEmpty()) return
        catalog[item.barcode] = CatalogEntry(item.barcode, item.name, item.category, item.unit, item.useBy)
        store.saveCatalog(catalog)
    }

    /** Adds the item, or adds to an identical line already in the batch. Returns true if merged. */
    fun addOrMerge(item: BatchItem): Boolean {
        val index = if (item.barcode.isEmpty()) {
            -1
        } else {
            batch.indexOfFirst {
                it.barcode == item.barcode &&
                    it.unit == item.unit &&
                    it.remove == item.remove &&
                    it.useBy == item.useBy &&
                    it.location == item.location
            }
        }
        return if (index >= 0) {
            val existing = batch[index]
            batch[index] = existing.copy(qty = existing.qty + item.qty)
            persist()
            true
        } else {
            batch.add(item)
            persist()
            false
        }
    }

    fun update(old: BatchItem, new: BatchItem) {
        val index = batch.indexOfFirst { it.scanId == old.scanId }
        if (index >= 0) {
            batch[index] = new
            persist()
        }
    }

    fun remove(item: BatchItem) {
        batch.removeAll { it.scanId == item.scanId }
        persist()
    }

    fun clear() {
        batch.clear()
        persist()
    }

    fun newItem(barcode: String): Pair<BatchItem, Boolean> {
        val known = if (barcode.isNotEmpty()) lookup(barcode) else null
        val item = BatchItem(
            scanId = UUID.randomUUID().toString(),
            name = known?.name ?: "",
            barcode = barcode,
            qty = 1.0,
            unit = known?.unit ?: "ea",
            useBy = "",
            category = known?.category ?: "",
            location = lastLocation,
            scannedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        )
        return Pair(item, known != null)
    }

    /** File name and text of the import file for the current batch. */
    fun exportFile(): Pair<String, String> {
        val name = InventoryCsv.fileName(source(), Instant.now())
        val text = InventoryCsv.build(batch.toList(), source())
        return Pair(name, text)
    }

    /** Saves the batch as a file in the outbox, empties the batch, and returns the file name. */
    fun queueBatch(): String? {
        if (batch.isEmpty()) return null
        val file = exportFile()
        if (!outboxStore.add(file.first, file.second)) return null
        clear()
        outbox.clear()
        outbox.addAll(outboxStore.list())
        return file.first
    }

    /** Takes in a stock list file. Returns false (and keeps the old one) when it is not a stock list. */
    fun loadStock(text: String): Boolean {
        val parsed = StockList.parse(text) ?: return false
        store.saveStockText(text)
        stock.clear()
        stock.addAll(parsed)
        return true
    }

    /** What the stock list says is on hand, adjusted by what is in the batch that has not been sent yet. */
    fun onHand(barcode: String): Pair<Double, String>? {
        val base = StockList.onHand(stock, barcode)
        val pending = batch.filter { it.barcode == barcode }.sumOf { if (it.remove) -it.qty else it.qty }
        if (base == null) return if (pending != 0.0) Pair(pending, "") else null
        return Pair(base.first + pending, base.second)
    }

    fun refreshOutbox() {
        outbox.clear()
        outbox.addAll(outboxStore.list())
    }

    fun delivered(name: String) {
        outboxStore.markDelivered(name)
        outbox.clear()
        outbox.addAll(outboxStore.list())
    }

    fun discard(name: String) {
        outboxStore.delete(name)
        outbox.clear()
        outbox.addAll(outboxStore.list())
    }

    private fun persist() {
        store.saveBatch(batch.toList())
    }
}

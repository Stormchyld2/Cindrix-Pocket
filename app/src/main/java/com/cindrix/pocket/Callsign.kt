package com.cindrix.pocket

import java.util.Locale
import java.util.Random

/** Default device names in the same style as Cindrix AirLink, for example Falcon-27. */
object Callsign {
    private val words = listOf(
        "Falcon", "Raven", "Viper", "Ranger", "Badger", "Wolf", "Hawk", "Cobra", "Bison", "Ember",
        "Anvil", "Granite", "Cedar", "Ridge", "Harbor", "Summit", "Timber", "Sparrow", "Otter", "Lynx",
    )

    fun generate(random: Random = Random()): String {
        val word = words[random.nextInt(words.size)]
        val number = String.format(Locale.US, "%02d", random.nextInt(100))
        return "$word-$number"
    }

    fun clean(text: String): String =
        text.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(24)
}

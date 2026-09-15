package com.wonder.provider.ui.conversation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.wonder.provider.model.PersonaIconCatalog
import com.wonder.provider.model.PersonaPanel

object PersonaAvatars {
    private val aurora = listOf(
        Color(0xFF2DD4BF),
        Color(0xFF6366F1),
        Color(0xFFF97316)
    )

    private val icons: Map<String, ImageVector> = mapOf(
        "spa" to Icons.Outlined.Spa,
        "shield" to Icons.Outlined.Shield,
        "restaurant" to Icons.Outlined.Restaurant,
        "bar" to Icons.Outlined.LocalBar,
        "runner" to Icons.AutoMirrored.Outlined.DirectionsRun,
        "wallet" to Icons.Outlined.AccountBalanceWallet,
        "sunset" to Icons.Outlined.WbTwilight,
        "bolt" to Icons.Outlined.Bolt,
        "museum" to Icons.Outlined.Museum,
        "moon" to Icons.Outlined.Bedtime,
        "sunrise" to Icons.Outlined.WbSunny,
        "camera" to Icons.Outlined.CameraAlt,
        "rain" to Icons.Outlined.Umbrella,
        "home" to Icons.Outlined.Home,
        "meditation" to Icons.Outlined.SelfImprovement,
        "flight" to Icons.Outlined.FlightTakeoff,
        "map" to Icons.Outlined.Map,
        "coffee" to Icons.Outlined.Coffee,
        "music" to Icons.Outlined.MusicNote,
        "pets" to Icons.Outlined.Pets,
        "hike" to Icons.Outlined.Terrain,
        "shop" to Icons.Outlined.ShoppingBag,
        "family" to Icons.Outlined.Groups,
        "compass" to Icons.Outlined.Explore,
        "sparkle" to Icons.Outlined.AutoAwesome,
        "hotel" to Icons.Outlined.Hotel,
        "train" to Icons.Outlined.Train,
        "wine" to Icons.Outlined.WineBar,
        "theater" to Icons.Outlined.TheaterComedy,
        "sport" to Icons.Outlined.SportsSoccer,
        "eco" to Icons.Outlined.Eco,
        "history" to Icons.Outlined.History,
        "beach" to Icons.Outlined.WbTwilight,
        "bike" to Icons.Outlined.DirectionsBike,
        "book" to Icons.Outlined.Book,
        "ghost" to Icons.Outlined.AutoAwesome,
        "robot" to Icons.Outlined.AutoAwesome,
        "crown" to Icons.Outlined.AutoAwesome,
        "fire" to Icons.Outlined.Bolt,
        "snow" to Icons.Outlined.Spa
    )

    data class Style(
        val icon: ImageVector,
        val tint: Color,
        val emoji: String?
    )

    fun styleFor(panel: PersonaPanel): Style =
        styleFor(panel.resolvedIconKey(), panel.resolvedEmoji(), panel.persona)

    fun styleFor(iconKey: String, emoji: String?, persona: String): Style {
        val resolvedKey = PersonaIconCatalog.resolveIconKey(persona, iconKey)
        val icon = icons[resolvedKey] ?: Icons.Outlined.Explore
        val tint = aurora[kotlin.math.abs(persona.hashCode()) % aurora.size]
        return Style(icon = icon, tint = tint, emoji = emoji)
    }
}

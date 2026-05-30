package com.example.vpn.model

import java.util.UUID

data class VpnProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceUrl: String?, // null if manually/locally created
    val lastUpdated: Long = System.currentTimeMillis(),
    val proxiesCount: Int,
    val isSelected: Boolean = false
)

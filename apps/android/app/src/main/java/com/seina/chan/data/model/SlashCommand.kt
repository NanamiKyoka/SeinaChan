package com.seina.chan.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SlashCommand(
    val name: String,
    val description: String
)

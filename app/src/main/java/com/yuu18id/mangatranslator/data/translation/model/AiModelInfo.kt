package com.yuu18id.mangatranslator.data.translation.model

import kotlinx.serialization.Serializable

@Serializable
data class AiModelInfo(
    val id: String,
    val displayName: String = id,
    val description: String = ""
)

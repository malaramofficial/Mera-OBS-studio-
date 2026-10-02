package com.malaramofficial.mobilestudio.domain.model.scene

/**
 * GPU shader parameters for real-time green/blue screen chroma keying.
 */
data class ChromaKeyConfig(
    val enabled: Boolean = false,
    val keyColorHex: Long = 0xFF00FF00, // Standard pure green default
    val similarity: Float = 0.40f,      // Color distance cutoff (0.0 to 1.0)
    val smoothness: Float = 0.08f,      // Edge feathering threshold (0.0 to 1.0)
    val spillReduction: Float = 0.50f   // Desaturation of edge reflections (0.0 to 1.0)
) {
    init {
        require(similarity in 0f..1f) { "Similarity must be in [0, 1], got $similarity" }
        require(smoothness in 0f..1f) { "Smoothness must be in [0, 1], got $smoothness" }
        require(spillReduction in 0f..1f) { "Spill reduction must be in [0, 1], got $spillReduction" }
    }
}

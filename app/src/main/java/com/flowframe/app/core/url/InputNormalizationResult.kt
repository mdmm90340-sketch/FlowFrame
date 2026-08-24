package com.flowframe.app.core.url

sealed interface InputNormalizationResult {
    object NoSupportedLink : InputNormalizationResult

    data class SingleSupportedLink(
        val link: SupportedUrl,
    ) : InputNormalizationResult

    data class MultipleSupportedLinks(
        val links: List<SupportedUrl>,
    ) : InputNormalizationResult {
        init {
            require(links.size >= 2) { "MultipleSupportedLinks requires at least two links" }
        }
    }
}

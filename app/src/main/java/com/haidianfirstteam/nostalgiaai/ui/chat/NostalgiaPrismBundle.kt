package com.haidianfirstteam.nostalgiaai.ui.chat

import io.noties.prism4j.annotations.PrismBundle

/**
 * Generates a Prism4j GrammarLocator for syntax highlighting.
 *
 * NOTE: This class is only used by the prism4j-bundler annotation processor.
 */
@PrismBundle(
    include = [
        // core
        "clike",
        // common languages
        "java",
        "kotlin",
        "json",
        "javascript",
        "markup",
        "bash",
        "python"
    ],
    grammarLocatorClassName = ".NostalgiaGrammarLocator"
)
class NostalgiaPrismBundle

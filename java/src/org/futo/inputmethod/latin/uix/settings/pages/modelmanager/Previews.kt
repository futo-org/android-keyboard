package org.futo.inputmethod.latin.uix.settings.pages.modelmanager

import org.futo.inputmethod.latin.xlm.ModelInfo
import org.futo.inputmethod.latin.xlm.ModelInfoLoader
import java.io.File

val PreviewModelLoader = ModelInfoLoader(path = File("/tmp/badmodel.gguf"), name = "badmodel")

val PreviewModels = listOf(
    ModelInfo(
        name = "ml4_model",
        description = "A simple model",
        author = "FUTO",
        license = "GPL",
        features = listOf("inverted_space", "xbu_char_autocorrect_v1", "char_embed_mixing_v1"),
        languages = listOf("en-US"),
        tokenizer_type = "Embedded SentencePiece",
        finetune_count = 16,
        path = "?"
    ),


    ModelInfo(
        name = "ml4_model",
        description = "A simple model",
        author = "FUTO",
        license = "GPL",
        features = listOf("inverted_space", "xbu_char_autocorrect_v1", "char_embed_mixing_v1"),
        languages = listOf("en-US"),
        tokenizer_type = "Embedded SentencePiece",
        finetune_count = 0,
        path = "?"
    ),


    ModelInfo(
        name = "gruby",
        description = "Polish Model",
        author = "FUTO",
        license = "GPL",
        features = listOf("inverted_space", "xbu_char_autocorrect_v1", "char_embed_mixing_v1"),
        languages = listOf("pl"),
        tokenizer_type = "Embedded SentencePiece",
        finetune_count = 23,
        path = "?"
    ),

    ModelInfo(
        name = "gruby",
        description = "Polish Model",
        author = "FUTO",
        license = "GPL",
        features = listOf("inverted_space", "xbu_char_autocorrect_v1", "char_embed_mixing_v1"),
        languages = listOf("pl"),
        tokenizer_type = "Embedded SentencePiece",
        finetune_count = 0,
        path = "?"
    ),
)
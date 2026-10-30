package tss.t.shared.text

import java.text.Normalizer

internal actual fun canonicalDecompose(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

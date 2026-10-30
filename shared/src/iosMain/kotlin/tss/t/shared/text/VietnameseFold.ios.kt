package tss.t.shared.text

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun canonicalDecompose(text: String): String =
    (text as NSString).decomposedStringWithCanonicalMapping

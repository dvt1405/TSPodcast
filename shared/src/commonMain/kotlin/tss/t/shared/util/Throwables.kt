package tss.t.shared.util

/**
 * Simple Kotlin class name of this throwable, for logs. Swift cannot name Kotlin classes
 * that are not exported (Ktor and Darwin exceptions show up as `Shared_kobjc…`), and the
 * message may carry URLs, so logs use this instead of `message`.
 */
val Throwable.typeName: String
    get() = this::class.simpleName ?: "Throwable"

package tss.t.shared.flow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * Observes [flow] from Swift: [onEach] runs on the main queue (so a `@MainActor` model can apply
 * the value directly) until the returned handle is closed, e.g. in `deinit` or `onDisappear`.
 *
 * Swift: `let handle = Watch_iosKt.watch(flow: repository.items) { value in ... }`; `value` arrives as
 * `Any?` and is cast to the element type.
 */
fun <T> watch(flow: Flow<T>, onEach: (T) -> Unit): Closeable = watch(flow, Dispatchers.Main, onEach)

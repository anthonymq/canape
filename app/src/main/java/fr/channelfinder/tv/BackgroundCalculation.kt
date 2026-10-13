package fr.channelfinder.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class Calculation<T>(val inputs: List<Any?>, val result: T)

@Composable
internal fun <T> backgroundCalculation(vararg inputs: Any?, calculate: (() -> Unit) -> T): T? {
    val keys = inputs.toList()
    val calculation by produceState<Calculation<T>?>(null, *inputs) {
        value = Calculation(keys, calculateInBackground(calculate))
    }
    return calculation?.takeIf { it.inputs == keys }?.result
}

internal suspend fun <T> calculateInBackground(calculate: (() -> Unit) -> T): T {
    val snapshot = Snapshot.takeSnapshot()
    try {
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            snapshot.enter { calculate { context.ensureActive() } }
        }
    } finally {
        snapshot.dispose()
    }
}

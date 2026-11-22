package com.example.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class OperationType {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    LIST,
    WRITE
}

fun handleFirestoreError(exception: Exception, operation: OperationType, path: String) {
    val rootError = generateSequence<Throwable>(exception) { it.cause }
        .firstOrNull { it is FirebaseFirestoreException }
        ?: generateSequence<Throwable>(exception) { it.cause }.firstOrNull { it !is CancellationException }
        ?: exception

    val json = try {
        JSONObject().apply {
            put("event", "firestore_error")
            put("operation", operation.name)
            put("path", path)
            put("message", rootError.localizedMessage ?: rootError.toString())
        }.toString()
    } catch (_: Exception) {
        "event=firestore_error operation=$operation path=$path message=${rootError.message ?: "Unknown"}"
    }
    Log.e("FirestoreError", json)
}

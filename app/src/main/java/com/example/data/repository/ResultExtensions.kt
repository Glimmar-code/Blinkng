package com.example.data.repository

internal inline fun <T> Result<T>.filterCatching(predicate: (T) -> Boolean): Result<T> =
    fold(
        onSuccess = { value ->
            runCatching {
                check(predicate(value)) { "Result did not satisfy validation." }
                value
            }
        },
        onFailure = { error -> Result.failure(error) }
    )

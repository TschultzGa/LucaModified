package de.culture4life.luca.util

import io.reactivex.rxjava3.core.Maybe

inline fun <T, R> Maybe<T>.mapNotNull(crossinline block: (T) -> R?): Maybe<R> = flatMap { Maybe.fromCallable { block(it) } }

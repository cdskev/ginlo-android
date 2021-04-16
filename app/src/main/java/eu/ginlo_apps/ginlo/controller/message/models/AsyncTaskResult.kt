// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.models

sealed class AsyncTaskResult<T> {
    class Error(val message: String, val code: String?)
}

class AsyncTaskError<T>(val error: AsyncTaskResult.Error) : AsyncTaskResult<T>()

class AsyncTaskSuccessful<T>(val item: T) : AsyncTaskResult<T>()


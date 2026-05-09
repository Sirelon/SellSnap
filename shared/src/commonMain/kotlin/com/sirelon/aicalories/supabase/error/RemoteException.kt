package com.sirelon.sellsnap.supabase.error

/**
 * Represents an error returned by a remote server (e.g. Supabase).
 */
class RemoteException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

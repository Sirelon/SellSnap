package com.sirelon.sellsnap.features.seller.whisper.di

import com.sirelon.sellsnap.audio.createAudioRecorder
import com.sirelon.sellsnap.audio.createLiveAudioRecorder
import com.sirelon.sellsnap.features.seller.whisper.RealtimeTranscriptionClient
import com.sirelon.sellsnap.features.seller.whisper.WhisperViewModel
import com.sirelon.sellsnap.network.createRealtimeHttpClient
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val realtimeHttpClientQualifier = named("openAiRealtimeHttpClient")

val whisperModule = module {
    single { createAudioRecorder() }
    single { createLiveAudioRecorder() }
    single(qualifier = realtimeHttpClientQualifier) { createRealtimeHttpClient() }
    single {
        RealtimeTranscriptionClient(
            httpClient = get(qualifier = realtimeHttpClientQualifier),
            tokenProvider = get(),
            json = get(),
        )
    }
    viewModelOf(::WhisperViewModel)
}

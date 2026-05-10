package com.sirelon.aicalories.features.seller.whisper.di

import com.sirelon.aicalories.audio.createAudioRecorder
import com.sirelon.aicalories.audio.createLiveAudioRecorder
import com.sirelon.aicalories.features.seller.whisper.RealtimeTranscriptionClient
import com.sirelon.aicalories.features.seller.whisper.WhisperViewModel
import com.sirelon.aicalories.network.createRealtimeHttpClient
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

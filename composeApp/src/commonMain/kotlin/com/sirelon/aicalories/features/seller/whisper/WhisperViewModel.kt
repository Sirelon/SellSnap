package com.sirelon.sellsnap.features.seller.whisper

import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.sirelon.sellsnap.audio.AudioRecorder
import com.sirelon.sellsnap.audio.LiveAudioRecorder
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.whisper_live_transcription_failed
import com.sirelon.sellsnap.generated.resources.whisper_openai_api_key_missing
import com.sirelon.sellsnap.generated.resources.whisper_recording_failed
import com.sirelon.sellsnap.generated.resources.whisper_start_recording_failed
import com.sirelon.sellsnap.generated.resources.whisper_transcription_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

class WhisperViewModel(
    private val audioRecorder: AudioRecorder,
    private val liveAudioRecorder: LiveAudioRecorder,
    private val realtimeTranscriptionClient: RealtimeTranscriptionClient,
    private val openAI: OpenAI,
) : BaseViewModel<WhisperContract.State, WhisperContract.Event, WhisperContract.Effect>() {

    private var audioData: ByteArray? = null
    private var liveTranscriptionJob: Job? = null

    override fun initialState(): WhisperContract.State = WhisperContract.State()

    override fun onEvent(event: WhisperContract.Event) {
        when (event) {
            WhisperContract.Event.RecordToggled -> onRecordToggled()
            WhisperContract.Event.TranscribeClicked -> transcribe()
            WhisperContract.Event.LiveRecordToggled -> onLiveRecordToggled()
        }
    }

    private fun onRecordToggled() {
        if (currentState().isLiveTranscribing) {
            stopLiveTranscription()
        }

        if (currentState().isRecording) {
            val bytes = runCatching { audioRecorder.stopRecording() }.getOrNull()
            audioData = bytes
            setState {
                it.copy(
                    isRecording = false,
                    hasAudioData = bytes != null,
                    errorMessage = null,
                )
            }
            if (bytes == null) {
                showError(Res.string.whisper_recording_failed)
            }
            return
        }

        runCatching {
            audioData = null
            audioRecorder.startRecording()
        }.onSuccess {
            setState {
                it.copy(
                    isRecording = true,
                    transcriptionText = "",
                    errorMessage = null,
                    hasAudioData = false,
                )
            }
        }.onFailure { throwable ->
            showError(
                fallback = Res.string.whisper_start_recording_failed,
                message = throwable.message,
            )
        }
    }

    private fun onLiveRecordToggled() {
        if (currentState().isLiveTranscribing) {
            stopLiveTranscription()
            return
        }

        if (currentState().isRecording) {
            audioData = runCatching { audioRecorder.stopRecording() }.getOrNull()
        }

        liveTranscriptionJob?.cancel()
        liveTranscriptionJob = viewModelScope.launch {
            setState {
                it.copy(
                    isRecording = false,
                    isLiveTranscribing = true,
                    liveTranscriptionText = "",
                    errorMessage = null,
                )
            }

            realtimeTranscriptionClient
                .transcribe(liveAudioRecorder.startStreaming())
                .onCompletion {
                    liveAudioRecorder.stopStreaming()
                    setState { state -> state.copy(isLiveTranscribing = false) }
                }
                .collect { event ->
                    when (event) {
                        RealtimeTranscriptionEvent.Connected -> Unit
                        is RealtimeTranscriptionEvent.Delta -> {
                            setState {
                                it.copy(
                                    liveTranscriptionText = it.liveTranscriptionText + event.text,
                                    errorMessage = null,
                                )
                            }
                        }

                        is RealtimeTranscriptionEvent.Completed -> {
                            setState {
                                it.copy(
                                    liveTranscriptionText = event.text,
                                    errorMessage = null,
                                )
                            }
                        }

                        is RealtimeTranscriptionEvent.Error -> {
                            val message = when (event.reason) {
                                RealtimeTranscriptionErrorReason.MissingApiKey ->
                                    getString(Res.string.whisper_openai_api_key_missing)

                                RealtimeTranscriptionErrorReason.LiveTranscriptionFailed,
                                RealtimeTranscriptionErrorReason.ServerError ->
                                    getString(Res.string.whisper_live_transcription_failed)
                            }
                            setState {
                                it.copy(
                                    isLiveTranscribing = false,
                                    errorMessage = message,
                                )
                            }
                            postEffect(WhisperContract.Effect.ShowMessage(message))
                            stopLiveTranscription()
                        }
                    }
                }
        }
    }

    private fun stopLiveTranscription() {
        liveAudioRecorder.stopStreaming()
        liveTranscriptionJob?.cancel()
        liveTranscriptionJob = null
        setState { it.copy(isLiveTranscribing = false) }
    }

    private fun transcribe() {
        val bytes = audioData
        if (bytes == null || currentState().isTranscribing) return

        viewModelScope.launch {
            setState { it.copy(isTranscribing = true, errorMessage = null) }

            runCatching {
                val source = FileSource(
                    name = "audio.m4a",
                    source = Buffer().apply { write(bytes) },
                )
                openAI.transcription(
                    request = TranscriptionRequest(
                        audio = source,
                        model = ModelId(InspectionTranscriptionProfile.model),
                        prompt = InspectionTranscriptionProfile.prompt,
                    ),
                ).text
            }.onSuccess { text ->
                setState {
                    it.copy(
                        transcriptionText = text,
                        isTranscribing = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                val message = throwable.message ?: getString(Res.string.whisper_transcription_failed)
                setState {
                    it.copy(
                        isTranscribing = false,
                        errorMessage = message,
                    )
                }
                postEffect(WhisperContract.Effect.ShowMessage(message))
            }
        }
    }

    private fun showError(fallback: StringResource, message: String? = null) {
        viewModelScope.launch {
            val resolvedMessage = message ?: getString(fallback)
            setState { it.copy(errorMessage = resolvedMessage) }
            postEffect(WhisperContract.Effect.ShowMessage(resolvedMessage))
        }
    }

    override fun onCleared() {
        stopLiveTranscription()
        super.onCleared()
    }
}

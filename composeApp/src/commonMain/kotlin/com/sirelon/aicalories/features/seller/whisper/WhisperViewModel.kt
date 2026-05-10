package com.sirelon.sellsnap.features.seller.whisper

import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.sirelon.sellsnap.audio.AudioRecorder
import com.sirelon.sellsnap.audio.LiveAudioRecorder
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.io.Buffer

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
                    errorMessage = if (bytes == null) "Recording failed." else null,
                )
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
            setState { it.copy(errorMessage = throwable.message ?: "Could not start recording.") }
            postEffect(WhisperContract.Effect.ShowMessage("Could not start recording."))
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
                            setState {
                                it.copy(
                                    isLiveTranscribing = false,
                                    errorMessage = event.message,
                                )
                            }
                            postEffect(WhisperContract.Effect.ShowMessage(event.message))
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
                        model = ModelId("whisper-1"),
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
                val message = throwable.message ?: "Transcription failed."
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

    override fun onCleared() {
        stopLiveTranscription()
        super.onCleared()
    }
}

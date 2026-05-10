package com.sirelon.sellsnap.features.seller.whisper

interface WhisperContract {
    data class State(
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val transcriptionText: String = "",
        val liveTranscriptionText: String = "",
        val errorMessage: String? = null,
        val hasAudioData: Boolean = false,
        val isLiveTranscribing: Boolean = false,
    )

    sealed interface Event {
        data object RecordToggled : Event
        data object TranscribeClicked : Event
        data object LiveRecordToggled : Event
    }

    sealed interface Effect {
        data class ShowMessage(val message: String) : Effect
    }
}

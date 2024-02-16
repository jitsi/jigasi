package org.jitsi.jigasi.transcription;

import org.jitsi.jigasi.JigasiBundleActivator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jitsi.jigasi.transcription.Transcriber.FILTER_SILENCE_DEFAULT_VALUE;
import static org.jitsi.jigasi.transcription.Transcriber.P_NAME_FILTER_SILENCE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhisperWebsocketTest {

    private static class TranscriptionListenerForTest implements TranscriptionListener {

        private TranscriptionResult result;

        public TranscriptionResult getResult() {
            return result;
        }

        @Override
        public void notify(TranscriptionResult result) {
            this.result = result;
        }

        @Override
        public void completed() {

        }

        @Override
        public void failed(FailureReason reason) {

        }
    }

    @Test
    void onMessage() {
        final WhisperWebsocket whisperWebsocket = new WhisperWebsocket();
        final TranscriptionListenerForTest listener = new TranscriptionListenerForTest();
        final Participant participant = mock(Participant.class);
        when(participant.getDebugName()).thenReturn("room/id1234567890");

        whisperWebsocket.addListener(listener, participant);
        whisperWebsocket.onMessage("{\"type\":\"final\",\"participant_id\":\"id1234567890\", \"ts\":\"3457658454\", \"id\":\"01870603-f211-7b9a-a7ea-4a98f5320ff8\", \"text\":\"hello world\", \"variance\":0.9}");

        assertThat(listener.getResult().getAlternatives().stream().findFirst().orElse(null).getTranscription()).isEqualTo("hello world");
    }

    @Test
    void buildPayload() {
        final WhisperWebsocket whisperWebsocket = new WhisperWebsocket();
        final Participant participant = mock(Participant.class);
        when(participant.getTranslationLanguage()).thenReturn("eng ");

        final ByteBuffer buffer =
                whisperWebsocket.buildPayload("id1234567".repeat(5), participant, ByteBuffer.wrap("hello world!".getBytes()), () -> 123456789L);
        assertThat(buffer).isNotNull();
        assertThat(new String(buffer.array())).isEqualTo("id1234567id1234567id1234567id1234567id1234567|123456789|eng hello world!");
    }
}
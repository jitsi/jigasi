package org.jitsi.jigasi.transcription.oracle;
import com.oracle.bmc.aispeech.model.RealtimeMessageConnect;
import com.oracle.bmc.aispeech.model.RealtimeMessageAckAudio;
import com.oracle.bmc.aispeech.model.RealtimeMessageResult;

public interface OracleRealtimeClientListener
{
    void onClose(int statusCode, String statusMessage);

    void onAckMessage(RealtimeMessageAckAudio ackMessage);

    void onResult(RealtimeMessageResult result);

    void onError(Throwable error);

    void onConnect();

    void onConnectMessage(RealtimeMessageConnect connectMessage);
}

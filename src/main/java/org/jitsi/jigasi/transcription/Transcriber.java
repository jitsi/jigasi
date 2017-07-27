/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2017 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jigasi.transcription;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.util.*;

import javax.media.Buffer;
import javax.media.rtp.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A transcriber object which will keep track of participants in a conference
 * which will need to be transcribed. It will manage starting and stopping
 * of said transcription as well as providing the transcription
 *
 * @author Nik Vaessen
 */
public class Transcriber
    implements ReceiveStreamBufferListener
{
    /**
     * The logger of this class
     */
    private final static Logger logger = Logger.getLogger(Transcriber.class);

    /**
     * Currently assume everyone to have this locale
     */
    public final static Locale ENGLISH_LOCALE = Locale.forLanguageTag("en-US");

    /**
     * The states the transcriber can be in. The Transcriber
     * can only go through one cycle. So once it is started it can never
     * be started, and once is is stopped it can never be stopped and once
     * it is finished it will not be able to start again
     */
    private enum State
    {
        /**
         * when transcription has not started
         */
        NOT_STARTED,

        /**
         * when actively transcribing or when stopped but still transcribing
         * audio not yet handled
         */
        TRANSCRIBING,

        /**
         * when not accepting new audio but still transcribing audio already
         * buffered or sent
         */
        FINISHING_UP,

        /**
         * when finishing last transcriptions and no new results will ever
         * come in
         */
        FINISHED
    }

    /**
     * The current state of the transcribing
     */
    private State state = State.NOT_STARTED;

    /**
     * Holds participants of the conference which need
     * to be transcribed
     */
    private Map<Long, Participant> participants = new HashMap<>();

    /**
     * The object which will hold the actual transcription
     * and which will be continuously updated as newly transcribed
     * audio comes in
     */
    private Transcription transcription = new Transcription();

    /**
     * The MediaDevice which will get all audio to transcribe
     */
    private TranscribingAudioMixerMediaDevice mediaDevice
            = new TranscribingAudioMixerMediaDevice(this);

    /**
     * Every listener which will be notified when a new result comes in
     * or the transcription has been completed
     */
    private ArrayList<TranscriptionListener> listeners = new ArrayList<>();

    /**
     * The service which is used to send audio and receive the
     * transcription of said audio
     */
    private TranscriptionService transcriptionService;

    /**
     * A single thread which is used to manage the buffering and sending
     * of audio packets for all of the {@link Participant}s of this
     * {@link Transcriber}. This is used to offload work from the thread dealing
     * with all packets, which only has 20 ms before new packets come in.
     *
     * Will be created in {@link Transcriber#start()} and shutdown in
     * {@link Transcriber#stop()}
     */
    ExecutorService executorService;

    /**
     * Create a transcription object which can be used to add and remove
     * participants of a conference to a list of audio streams which will
     * be transcribed.
     *
     * @param service the transcription service which will be used to transcribe
     *                the audio streams
     */
    public Transcriber(TranscriptionService service)
    {
        if(!service.supportsStreamRecognition())
        {
            throw new IllegalArgumentException("Currently only services which" +
                    " support streaming recognition are supported");
        }
        this.transcriptionService = service;
    }

    /**
     * Add a participant to the list of participants being transcribed
     *
     * @param name the name of the participant to be added
     * @param ssrc the ssrc of the participant to be added
     */
    public void add(String name, long ssrc)
    {
        Participant participant;
        if(this.participants.containsKey(ssrc))
        {
            participant = this.participants.get(ssrc);
        }
        else
        {
            participant = new Participant(this, name, ssrc);
            this.participants.put(ssrc, participant);
        }

        participant.joined();
        logger.debug("Added participant " + name + " with ssrc " + ssrc);
    }

    /**
     * Remove a participant from the list of participants being transcribed
     *
     * @param name the name of the participant to be removed
     * @param ssrc the ssrc of the participant to be removed
     */
    public void remove(String name, long ssrc)
    {
        if(this.participants.containsKey(ssrc))
        {
            participants.get(ssrc).left();
            logger.debug("Removed participant "+name+" with ssrc "+ssrc);
            return;
        }
        logger.warn("Asked to remove participant" + name + " with ssrc " +
                ssrc + " which did not exist");
    }

    /**
     * Start transcribing all participants added to the list
     */
    public void start()
    {
        if(State.NOT_STARTED.equals(this.state))
        {
            this.state = State.TRANSCRIBING;
            executorService = Executors.newSingleThreadExecutor();
        }
        else
        {
            logger.warn("Trying to start Transcriber while it is" +
                    "already started");
        }
    }

    /**
     * Stop transcribing all participants added to the list
     */
    public void stop()
    {
        if(State.TRANSCRIBING.equals(this.state))
        {
            this.state = State.FINISHING_UP;
            executorService.shutdown();
        }
        else
        {
            logger.warn("Trying to stop Transcriber while it is" +
                    "already stopped");
        }
    }

    /**
     * Get whether the transcriber has been started
     *
     * @return true when the transcriber has been started
     * false when not yet started or already stopped
     */
    public boolean isTranscribing()
    {
        return State.TRANSCRIBING.equals(this.state);
    }

    /**
     * Get whether the transcribed has been stopped and will not have any new
     * results coming in. This is always true after every
     * {@link TranscriptionListener} has has their
     * {@link TranscriptionListener#completed()} method called
     *
     * @return true when the transcribed has stopped and no new results will
     * ever come in
     */
    public boolean finished()
    {
        return State.FINISHED.equals(this.state);
    }

    /**
     * Get whether the transcribed has been stopped and is processing the
     * last audio fragments before it will be finished
     *
     * @return true when the transcriber is waiting for the last results to come
     * in, false otherwise
     */
    public boolean finshingUp()
    {
        return State.FINISHING_UP.equals(this.state);
    }

    /**
     * Provides the (ongoing) transcription of the conference this object
     * is transcribing
     *
     * @return the Transcription object which will be updated
     * as long as this object keeps transcribing
     */
    public Transcription getTranscription()
    {
        return transcription;
    }

    /**
     * Add a TranscriptionListener which will be notified when the Transcription
     * is updated due to new TranscriptionResult's coming in
     *
     * @param listener the listener which will be notified
     */
    public void addTranscriptionListener(TranscriptionListener listener)
    {
        listeners.add(listener);
    }

    /**
     * Remove a TranscriptionListener such that it will no longer be
     * notified of new results
     *
     * @param listener the listener to remove
     */
    public void removeTranscriptionListener(TranscriptionListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * The transcriber can be used as a {@link ReceiveStreamBufferListener}
     * to listen for new audio packets coming in through a MediaDevice. It will
     * try to filter them based on the SSRC of the packet. If the SSRC does not
     * match a participant added to the transcribed, an exception will be thrown
     *
     * Note that this code is run in a Thread doing audio mixing and only
     * has 20 ms for each frame
     *
     * @param receiveStream the stream from which the audio was received
     * @param buffer the containing the audio as well as meta-data
     */
    @Override
    public void bufferReceived(ReceiveStream receiveStream, Buffer buffer)
    {
        if(!isTranscribing())
        {
            return;
        }

        long ssrc = receiveStream.getSSRC() & 0xffffffffL;

        Participant p = participants.get(ssrc);
        if(p != null)
        {
            p.giveBuffer(buffer);
        }
        else
        {
            logger.warn("Reading from SSRC " + ssrc + " while it is "+
                "not known as a participant");
        }
    }

    /**
     * Get the MediaDevice this transcriber is listening to for audio
     *
     * @return the AudioMixerMediaDevice which should receive all audio
     * needed to be transcribed
     */
    public AudioMixerMediaDevice getMediaDevice()
    {
        return this.mediaDevice;
    }

    /**
     * @return this {@link Transcriber}'s {@link TranscriptionService}.
     */
    public TranscriptionService getTranscriptionService()
    {
        return transcriptionService;
    }

    /**
     * Notifies all of the listeners of this {@link Transcriber} of a new
     * {@link TranscriptionResult} which was received.
     * @param result the result.
     */
    void notify(TranscriptionResult result)
    {
        for (TranscriptionListener listener : listeners)
        {
            listener.notify(result);
        }
    }

}

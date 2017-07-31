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
import javax.media.format.*;
import javax.media.rtp.*;
import java.nio.*;
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
    private final static Locale ENGLISH_LOCALE = Locale.forLanguageTag("en-US");

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
    private Transcript transcript = new Transcript();

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
     * of audio packets. This is used to offload work from the thread dealing
     * with all packets, which only has 20 ms before new packets come in.
     *
     * Will be created in {@link Transcriber#start()} and shutdown in
     * {@link Transcriber#stop()}
     */
    private ExecutorService executorService;

    /**
     * The name of the room of the conference which will be transcribed
     */
    private String roomName;

    /**
     * Create a transcription object which can be used to add and remove
     * participants of a conference to a list of audio streams which will
     * be transcribed.
     *
     * @param roomName the roomanem the transcription will take place in
     * @param service the transcription service which will be used to transcribe
     *                the audio streams
     */
    public Transcriber(String roomName, TranscriptionService service)
    {
        if(!service.supportsStreamRecognition())
        {
            throw new IllegalArgumentException("Currently only services which" +
                " support streaming recognition are supported");
        }
        this.transcriptionService = service;
        addTranscriptionListener(this.transcript);
        this.roomName = roomName;
    }


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
        this(null, service);
    }

    /**
     * Add a participant to the list of participants being transcribed
     *
     * @param name the name of the participant to be added
     * @param id the id in the JID of the participant to be added
     * @param ssrc the ssrc of the participant to be added
     */
    public void add(String name, String id, long ssrc)
    {
        Participant participant;
        if(this.participants.containsKey(ssrc))
        {
            participant = this.participants.get(ssrc);
        }
        else
        {
            participant = new Participant(name, id, ssrc);
            this.participants.put(ssrc, participant);
        }

        participant.joined();
        transcript.notifyJoined(name);
        logger.debug("Added participant " + name + " with ssrc " + ssrc);
    }

    /**
     * Remove a participant from the list of participants being transcribed
     *
     * @param name the name of the participant to be removed
     * @param id the id in the JID of the participant to be removed
     * @param ssrc the ssrc of the participant to be removed
     */
    public void remove(String name, String id, long ssrc)
    {
        transcript.notifyLeft(name);
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
            logger.debug("Transcriber is now transcribing");
            this.state = State.TRANSCRIBING;
            this.executorService = Executors.newSingleThreadExecutor();

            List<String> names = new ArrayList<>(participants.size());
            participants.values().forEach((p) -> {
                String name;
                if((name = p.getName()) == null)
                {
                    name = p.getId();
                }
                names.add(name);
            });
            this.transcript.started(roomName, names);
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
            logger.debug("Transcriber is now finishing up");
            this.state = State.FINISHING_UP;
            this.executorService.shutdown();
            this.transcript.ended();

            checkIfFinishedUp();

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
     * @return the Transcript object which will be updated
     * as long as this object keeps transcribing
     */
    public Transcript getTranscript()
    {
        return transcript;
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
     * Check if all participant have been completely transcribed. When this
     * is the case, set the state from FINISHING_UP to FINISHED
     */
    private void checkIfFinishedUp()
    {
        if(State.FINISHING_UP.equals(this.state))
        {
            for(Participant participant : participants.values())
            {
                if(!participant.isCompleted)
                {
                    return;
                }
            }
            logger.debug("Transcriber is now finished");
            this.state = State.FINISHED;
            for(TranscriptionListener listener : listeners)
            {
                listener.completed();
            }
        }
    }

    /**
     * This class describes a participant in a conference whose
     * transcription is required. It manages the transcription if its own audio
     * will locally buffered until enough audio is collected
     */
    private class Participant
            implements TranscriptionListener
    {

        /**
         * The expected amount of bytes each given buffer will have. Webrtc
         * usually has 20ms opus frames which are decoded to 2 bytes per sample
         * and 48000Hz sampling rate, which results in 2 * 48000 = 96000 bytes
         * per second, and because frames are 20 ms we have 1000/20 = 50
         * packets per second. Each packet will thus contain
         * 96000 / 50 = 1920 bytes
         */
        private static final int EXPECTED_AUDIO_LENGTH = 1920;

        /**
         * The size of the local buffer. A single packet is expected to contain
         * 1920 bytes, so the size should be a multiple of 1920. Using
         * 25 results in 20 ms * 25 packets = 500 ms of audio being buffered
         * locally before being send to the TranscriptionService
         */
        private static final int BUFFER_SIZE = EXPECTED_AUDIO_LENGTH * 25;

        /**
         * Whether we should buffer locally before sending
         */
        private static final boolean USE_LOCAL_BUFFER = true;

        /**
         * The name of the participant
         */
        private String name;

        /**
         * The id identifying audio belonging to this participant
         */
        private long ssrc;

        /**
         * The ID used in the JID of a participant
         */
        private String id;

        /**
         * The streaming session which will constantly receive audio
         */
        private TranscriptionService.StreamingRecognitionSession session;

        /**
         * A buffer which is used to locally store audio before sending
         */
        private ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        /**
         * The AudioFormat of the audio being read. It is assumed to not change
         * after initialization
         */
        private AudioFormat audioFormat;

        /**
         * Whether the current session is still transcribing
         */
        private boolean isCompleted = false;

        /**
         * Create a participant with a given name and audio stream
         *
         * @param name the name of the participant
         * @param id the id in the JID of this participant
         * @param ssrc the ssrc of the audio of this participant
         */
        Participant(String name, String id, long ssrc)
        {
            this.name = name;
            this.id = id;
            this.ssrc = ssrc;
        }

        /**
         * Get the name of the participant
         *
         * @return the name of this particular participant
         */
        public String getName()
        {
            return name;
        }

        /**
         * Get the ssrc of the audio of this participant
         *
         * @return the srrc
         */
        public long getSSRC()
        {
            return ssrc;
        }

        /**
         * Get the id in the JID of this participant
         *
         * @return the id
         */
        public String getId()
        {
            return this.id;
        }

        /**
         * When a participant joined it accepts audio and will send it
         * to be transcribed
         */
        void joined()
        {
            if (session != null && !session.ended())
            {
                return; // no need to create new session
            }

            if (transcriptionService.supportsStreamRecognition())
            {
                session = transcriptionService.initStreamingSession();
                session.addTranscriptionListener(Participant.this);
                isCompleted = false;
            }
        }

        /**
         * When a participant has left it does not accept audio and thus no new
         * results will come in
         */
        void left()
        {
            if (session != null)
            {
                session.end();
            }
        }

        /**
         * Give a packet of the audio of this participant such that it can be
         * buffered and sent to the transcription once enough has been stored
         *
         * @param buffer a buffer which is expected to contain a single packet
         *               of audio of this participant
         */
        void giveBuffer(Buffer buffer)
        {
            if (audioFormat == null)
            {
                audioFormat = (AudioFormat) buffer.getFormat();
            }

            byte[] audio = (byte[]) buffer.getData();

            if (USE_LOCAL_BUFFER)
            {
                buffer(audio);
            }
            else
            {
                sendRequest(audio);
            }
        }

        @Override
        public void notify(TranscriptionResult result)
        {
            result.setName(this.name);
            logger.debug(result);
            for (TranscriptionListener listener : listeners)
            {
                listener.notify(result);
            }
        }

        @Override
        public void completed()
        {
            isCompleted = true;
            checkIfFinishedUp();
        }

        /**
         * Get whether everything this participant said has been transcribed
         *
         * @return true if completed transcribing, false otherwise
         */
        public boolean isCompleted()
        {
            return isCompleted;
        }

        /**
         * Store the given audio in a buffer. When the buffer is full,
         * send the audio
         *
         * @param audio the audio to buffer
         */
        private void buffer(byte[] audio)
        {
            try
            {
                buffer.put(audio);
            }
            catch (BufferOverflowException | ReadOnlyBufferException e)
            {
                e.printStackTrace();
            }

            int spaceLeft = buffer.limit() - buffer.position();
            if(spaceLeft < EXPECTED_AUDIO_LENGTH)
            {
                sendRequest(buffer.array());
                buffer.clear();
            }
        }

        /**
         * Send the specified audio to the TranscriptionService.
         * <p>
         * An ExecutorService is used to offload work on the mxing thread
         *
         * @param audio the audio to send
         */
        private void sendRequest(byte[] audio)
        {
            executorService.submit(() ->
            {
                TranscriptionRequest request
                    = new TranscriptionRequest(audio,
                    audioFormat, ENGLISH_LOCALE);

                if (session != null && !session.ended())
                {
                    session.sendRequest(request);
                }
                else
                // fallback if TranscriptionService does not support streams
                // or session got ended prematurely
                {
                    // FIXME: 22/07/17 This just assumes given BUFFER_LENGTH
                    // is long enough to get decent audio length. Also does
                    // not take into account that participant's audio will
                    // be cut of midsentence. For better results, try to
                    // buffer until audio volume is silent for a "decent
                    // amount of time". Only relevant if Streaming
                    // recognition is not supported by the
                    // TranscriptionService
                    transcriptionService.sendSingleRequest(request,
                        Participant.this::notify);
                }
            });
        }
    }

}

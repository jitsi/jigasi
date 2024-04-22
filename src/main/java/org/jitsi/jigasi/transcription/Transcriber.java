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

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.stats.*;
import org.jitsi.jigasi.transcription.action.*;
import org.jitsi.utils.logging.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jivesoftware.smack.packet.*;

import javax.media.*;
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
     * The property name for the boolean value whether translations should be
     * enabled.
     */
    public final static String P_NAME_ENABLE_TRANSLATION
        = "org.jitsi.jigasi.transcription.ENABLE_TRANSLATION";

    /**
     * Whether to translate text before sending results in the target languages.
     */
    public final static boolean ENABLE_TRANSLATION_DEFAULT_VALUE = false;

    /**
     * The property name for the boolean value whether voice activity detection
     * should be used to filter out audio without speech.
     */
    public final static String P_NAME_FILTER_SILENCE
        = "org.jitsi.jigasi.transcription.FILTER_SILENCE";

    /**
     * Default value for property FILTER_SILENCE
     */
    public final static boolean FILTER_SILENCE_DEFAULT_VALUE = false;

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
     * to be transcribed. The key is the resource part of the
     * {@link ConferenceMember} retrieving by calling
     * {@link ConferenceMember#getAddress()}
     * which is equal to the name of a
     * {@link ChatRoomMember} retrieved by calling
     * {@link ChatRoomMember#getName()}
     */
    private final Map<String, Participant> participants = new HashMap<>();

    /**
     * The object which will hold the actual transcription
     * and which will be continuously updated as newly transcribed
     * audio comes in
     */
    private Transcript transcript = new Transcript();


    private static final String CUSTOM_TRANSLATION_SERVICE_PROP
            = "org.jitsi.jigasi.transcription.translationService";

    /**
     * The TranslationManager and the TranslationService which will be used
     * for managing translations.
     */
    private TranslationManager translationManager = null;

    /**
     * Every listener which will be notified when a new result comes in
     * or the transcription has been completed
     */
    private ArrayList<TranscriptionListener> listeners = new ArrayList<>();

    /**
     * Every listener which will be notified when a new <tt>TranscriptEvent</tt>
     * is created.
     */
    private ArrayList<TranscriptionEventListener> transcriptionEventListeners
        = new ArrayList<>();

    /**
     * The service which is used to send audio and receive the
     * transcription of said audio
     */
    private AbstractTranscriptionService transcriptionService;

    /**
     * A single thread which is used to manage the buffering and sending
     * of audio packets. This is used to offload work from the thread dealing
     * with all packets, which only has 20 ms before new packets come in.
     * <p>
     * Will be created in {@link Transcriber#start()} and shutdown in
     * {@link Transcriber#stop(org.jitsi.jigasi.transcription.TranscriptionListener.FailureReason)}
     */
    ExecutorService executorService;

    /**
     * The name of the room of the conference which will be transcribed
     */
    private String roomName;

    /**
     * The url of the conference which will be transcribed
     */
    private String roomUrl;

    /**
     * Whether silenced audio should be filtered out before sending audio to
     * a {@link TranscriptionService}.
     */
    private boolean filterSilence;

    /**
     * Create a transcription object which can be used to add and remove
     * participants of a conference to a list of audio streams which will
     * be transcribed.
     *
     * @param roomName the room name the transcription will take place in
     * @param roomUrl the url of the conference being transcribed
     * @param service the transcription service which will be used to transcribe
     * the audio streams
     */
    public Transcriber(String roomName,
                       String roomUrl,
                       AbstractTranscriptionService service)
    {
        if (!service.supportsStreamRecognition())
        {
            throw new IllegalArgumentException(
                    "Currently only services which support streaming "
                    + "recognition are supported");
        }
        this.transcriptionService = service;
        addTranscriptionListener(this.transcript);
        this.filterSilence = shouldFilterSilence();

        configureTranslationManager();
        if (isTranslationEnabled())
        {
            addTranscriptionListener(this.translationManager);
        }
        this.roomName = roomName;
        this.roomUrl = roomUrl;
    }

    /**
     * Create a transcription object which can be used to add and remove
     * participants of a conference to a list of audio streams which will
     * be transcribed.
     *
     * @param service the transcription service which will be used to transcribe
     * the audio streams
     */
    public Transcriber(AbstractTranscriptionService service)
    {
        this(null, null, service);
    }

    /**
     * Create the translationManager using the custom translation service configured by the user.
     * Fallback to GoogleTranslationService if no custom translation service configuration.
     */
    public void configureTranslationManager()
    {
        String customTranslationServiceClass = JigasiBundleActivator.getConfigurationService()
                .getString(CUSTOM_TRANSLATION_SERVICE_PROP, null);

        TranslationService translationService = null;
        if (customTranslationServiceClass != null)
        {
            try
            {
                translationService = (TranslationService)Class
                        .forName(customTranslationServiceClass)
                        .getDeclaredConstructor()
                        .newInstance();
            }
            catch(Exception e)
            {
                logger.error("Cannot instantiate custom translation service");
            }
        }

        if (translationService == null)
        {
            translationService = new GoogleCloudTranslationService();
        }

        translationManager = new TranslationManager(translationService);
    }

    /**
     * A debug name added to every log message printed by this instance.
     *
     * @return a {@code String}
     */
    private String getDebugName()
    {
        return roomName;
    }

    /**
     * Add a participant to the list of participants being transcribed
     *
     * @param identifier the identifier of the participant
     */
    public void participantJoined(String identifier)
    {
        Participant participant = getParticipant(identifier);

        if (participant != null)
        {
            participant.joined();

            TranscriptEvent event = transcript.notifyJoined(participant);
            if (event != null)
            {
                fireTranscribeEvent(event);
            }

            if (logger.isDebugEnabled())
                logger.debug(getDebugName() + ": added participant with identifier " + identifier);

            return;
        }

        logger.warn(
            getDebugName() + ": participant with identifier " + identifier
                +  " joined while it did not exist");

    }

    /**
     * Potentially declare that a new participant exist by making
     * its identifier known. If the identifier is already known this method
     * does nothing.
     *
     * @param identifier the identifier of new participant
     */
    public void maybeAddParticipant(String identifier)
    {
        synchronized (this.participants)
        {
            this.participants.computeIfAbsent(identifier,
                key -> new Participant(this, identifier, filterSilence));
        }
    }

    /**
     * Update the {@link Participant} with the given identifier by setting the
     * {@link ChatRoomMember} belonging to the {@link Participant}
     *
     * @param identifier the identifier of the participant
     * @param chatRoomMember the conferenceMember to set to the participant
     */
    public void updateParticipant(String identifier,
                                  ChatRoomMember chatRoomMember)
    {
        maybeAddParticipant(identifier);

        Participant participant = getParticipant(identifier);
        if (participant != null)
        {
            participant.setChatMember(chatRoomMember);

            if (chatRoomMember instanceof ChatRoomMemberJabberImpl)
            {
                Presence presence = ((ChatRoomMemberJabberImpl) chatRoomMember).getLastPresence();

                TranscriptionLanguageExtension transcriptionLanguageExtension
                    = presence.getExtension(TranscriptionLanguageExtension.class);

                TranslationLanguageExtension translationLanguageExtension
                    = presence.getExtension(TranslationLanguageExtension.class);

                if (transcriptionLanguageExtension != null)
                {
                    String language
                        = transcriptionLanguageExtension.getTranscriptionLanguage();

                    this.updateParticipantSourceLanguage(identifier,
                        language);
                }

                if (translationLanguageExtension != null)
                {
                    String language
                        = translationLanguageExtension.getTranslationLanguage();

                    if (participant.getSourceLanguage() != null &&
                            !participant.getSourceLanguage().equals(language))
                    {
                        this.updateParticipantTargetLanguage(identifier, language);
                    }
                }
                else
                {
                    this.updateParticipantTargetLanguage(identifier, null);
                }
            }
        }
        else
        {
            logger.warn(
                getDebugName() + ": asked to set chatroom member of participant"
                    + " with identifier " + identifier
                    + " while it wasn't added before");
        }
    }

    /**
     * Update the {@link Participant} with the given identifier by setting the
     * {@link ConferenceMember} belonging to the {@link Participant}
     *
     * @param identifier the identifier of the participant
     * @param conferenceMember the conferenceMember to set to the participant
     */
    public void updateParticipant(String identifier,
                                  ConferenceMember conferenceMember)
    {
        maybeAddParticipant(identifier);

        Participant participant = getParticipant(identifier);
        if (participant != null)
        {
            participant.setConfMember(conferenceMember);
        }
    }

    /**
     * Update the {@link Participant} with the given identifier by setting the
     * <tt>sourceLanguageLocale</tt> of the participant.
     *
     * @param identifier the identifier of the participant
     * @param language the source language tag for the participant
     */
    public void updateParticipantSourceLanguage(String identifier,
                                                String language)
    {
        Participant participant = getParticipant(identifier);

        if (participant != null)
        {
            participant.setSourceLanguage(language);
        }
    }

    /**
     * Update the {@link Participant} with the given identifier by setting the
     * <tt>translationLanguage</tt> of the participant and update the count for
     * languages in the @link {@link TranslationManager}
     *
     * @param identifier the identifier of the participant
     * @param language the language tag to be updated for the participant
     */
    public void updateParticipantTargetLanguage(String identifier,
                                                String language)
    {
        Participant participant = getParticipant(identifier);

        if (participant != null)
        {
            String previousLanguage = participant.getTranslationLanguage();

            translationManager.addLanguage(language);
            translationManager.removeLanguage(previousLanguage);
            participant.setTranslationLanguage(language);
        }
    }

    /**
     * Remove a participant from the list of participants being transcribed
     *
     * @param identifier the identifier of the participant
     */
    public void participantLeft(String identifier)
    {
        Participant participant;
        synchronized (this.participants)
        {
            participant = this.participants.remove(identifier);
        }

        if (participant != null)
        {
            translationManager.removeLanguage(
                participant.getTranslationLanguage());
            participant.left();
            TranscriptEvent event = transcript.notifyLeft(participant);
            if (event != null)
            {
                fireTranscribeEvent(event);
            }

            if (logger.isDebugEnabled())
                logger.debug(getDebugName() + ": removed participant with identifier " + identifier);

            return;
        }

        logger.warn(
            getDebugName() + ": participant with identifier "
                + identifier +  " left while it did not exist");
    }

    /**
     * Start transcribing all participants added to the list
     */
    public void start()
    {
        if (State.NOT_STARTED.equals(this.state))
        {
            if (logger.isDebugEnabled())
                logger.debug(getDebugName() + ": transcriber is now transcribing");

            Statistics.incrementTotalTranscriberStarted();

            this.state = State.TRANSCRIBING;
            this.executorService = Executors.newSingleThreadExecutor();

            TranscriptEvent event
                = this.transcript.started(roomName, roomUrl, getParticipants());
            if (event != null)
            {
                fireTranscribeEvent(event);
            }
        }
        else
        {
            logger.warn(
                getDebugName() + ": trying to start Transcriber while it is"
                    + " already started");
        }
    }

    /**
     * Stop transcribing all participants added to the list
     * @param reason failure reason.
     */
    public void stop(TranscriptionListener.FailureReason reason)
    {
        if (State.TRANSCRIBING.equals(this.state))
        {
            if (logger.isDebugEnabled())
                logger.debug(getDebugName() + ": transcriber is now finishing up");

            this.state = reason == null ? State.FINISHING_UP : State.FINISHED;
            this.executorService.shutdown();

            TranscriptEvent event = this.transcript.ended();
            fireTranscribeEvent(event);
            ActionServicesHandler.getInstance()
                .notifyActionServices(this, event);

            if (reason == null)
            {
                Statistics.incrementTotalTranscriberSopped();

                checkIfFinishedUp();
            }
            else
            {
                Statistics.incrementTotalTranscriberFailed();

                for (TranscriptionListener listener : listeners)
                {
                    listener.failed(reason);
                }
            }
        }
        else
        {
            logger.warn(
                getDebugName() + ": trying to stop Transcriber while it is "
                    + " already stopped");
        }
    }

    /**
     * Transcribing will stop, last chance to post something to the room.
     */
    public void willStop()
    {
        if (State.TRANSCRIBING.equals(this.state))
        {
            TranscriptEvent event = this.transcript.willEnd();
            fireTranscribeEvent(event);
            ActionServicesHandler.getInstance()
                .notifyActionServices(this, event);
        }
        else
        {
            logger.warn(
                getDebugName() + ": trying to notify Transcriber for a while"
                    + " it is already stopped");
        }
    }

    /**
     * Get whether the transcriber has been started
     *
     * @return true when the transcriber has been started false when not yet
     * started or already stopped
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
    public boolean finishingUp()
    {
        return State.FINISHING_UP.equals(this.state);
    }

    /**
     * Provides the (ongoing) transcription of the conference this object
     * is transcribing
     *
     * @return the Transcript object which will be updated as long as this
     * object keeps transcribing
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
     * Add a TranslationResultListener which will be notified when the
     * a new TranslationResult comes.
     *
     * @param listener the listener which will be notified
     */
    public void addTranslationListener(TranslationResultListener listener)
    {
        translationManager.addListener(listener);
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
     * Add a TranscriptionEventListener which will be notified when
     * the TranscriptionEvent is created.
     *
     * @param listener the listener which will be notified
     */
    public void addTranscriptionEventListener(
        TranscriptionEventListener listener)
    {
        transcriptionEventListeners.add(listener);
    }

    /**
     * Remove a TranscriptionListener such that it will no longer be
     * notified of new results
     *
     * @param listener the listener to remove
     */
    public void removeTranscriptionEventListener(
        TranscriptionEventListener listener)
    {
        transcriptionEventListeners.remove(listener);
    }

    /**
     * The transcriber can be used as a {@link ReceiveStreamBufferListener}
     * to listen for new audio packets coming in through a MediaDevice. It will
     * try to filter them based on the SSRC of the packet. If the SSRC does not
     * match a participant added to the transcribed, an exception will be thrown
     * <p>
     * Note that this code is run in a Thread doing audio mixing and only
     * has 20 ms for each frame
     *
     * @param receiveStream the stream from which the audio was received
     * @param buffer the containing the audio as well as meta-data
     */
    @Override
    public void bufferReceived(ReceiveStream receiveStream, Buffer buffer)
    {
        if (!isTranscribing())
        {
            if (logger.isTraceEnabled())
                logger.trace(getDebugName() + ": receiving audio while not transcribing");

            return;
        }

        long ssrc = receiveStream.getSSRC() & 0xffffffffL;

        Participant p = findParticipant(ssrc);

        if (p != null)
        {
            if (p.hasValidSourceLanguage())
            {
                if (logger.isTraceEnabled())
                    logger.trace(getDebugName() + ": gave audio to buffer");

                p.giveBuffer(buffer);
            }
        }
        else
        {
            logger.warn(
                getDebugName() + ": reading from SSRC " + ssrc
                    + " while it is not known as a participant");
        }
    }

    /**
     * Find the participant with the given audio ssrc, if present, in
     * {@link this#participants}
     *
     * @param ssrc the ssrc to search for
     * @return the participant with the given ssrc, or null if not present
     */
    private Participant findParticipant(long ssrc)
    {
        synchronized (this.participants)
        {
            for (Participant p : this.participants.values())
            {
                if (p.getSSRC() == ssrc)
                {
                    return p;
                }
            }

            return null;
        }
    }

    /**
     * Get the {@link Participant} with the given identifier
     *
     * @param identifier the identifier of the Participant to get
     * @return the Participant
     */
    private Participant getParticipant(String identifier)
    {
        synchronized (this.participants)
        {
            return this.participants.get(identifier);
        }
    }

    /**
     * Check whether any {@link Participant} are requesting transcription
     *
     * @return true when at least one {@link Participant} is requesting
     * transcription, false otherwise
     */
    public boolean isAnyParticipantRequestingTranscription()
    {
        return getParticipants().stream().anyMatch(Participant::isRequestingTranscription);
    }

    /**
     * Returns the list of participants. A copy of it.
     *
     * @return the list of current participants.
     */
    public List<Participant> getParticipants()
    {
        List<Participant> participantsCopy;
        synchronized (this.participants)
        {
            participantsCopy = new ArrayList<>(this.participants.values());
        }

        return participantsCopy;
    }

    /**
     * Get the MediaDevice this transcriber is listening to for audio
     *
     * @return the AudioMixerMediaDevice which should receive all audio needed
     * to be transcribed
     */
    public AudioMixerMediaDevice getMediaDevice()
    {
        return this.transcriptionService.getMediaDevice(this);
    }

    /**
     * Check if all participants have been completely transcribed. When this
     * is the case, set the state from FINISHING_UP to FINISHED
     */
    void checkIfFinishedUp()
    {
        if (State.FINISHING_UP.equals(this.state))
        {
            synchronized (this.participants)
            {
                for (Participant participant : participants.values())
                {
                    if (!participant.isCompleted())
                    {
                        if (logger.isDebugEnabled())
                            logger.debug(participant.getDebugName() + " is still not finished");

                        return;
                    }
                }
            }

            if (logger.isDebugEnabled())
                logger.debug(getDebugName() + ": transcriber is now finished");

            this.state = State.FINISHED;
            for (TranscriptionListener listener : listeners)
            {
                listener.completed();
            }
        }
    }

    /**
     * @return the {@link TranscriptionService}.
     */
    public TranscriptionService getTranscriptionService()
    {
        return transcriptionService;
    }

    /**
     * Notifies all of the listeners of this {@link Transcriber} of a new
     * {@link TranscriptionResult} which was received.
     *
     * @param result the result.
     */
    void notify(TranscriptionResult result)
    {
        for (TranscriptionListener listener : listeners)
        {
            listener.notify(result);
        }
    }

    /**
     * Returns the name of the room of the conference which will be transcribed.
     * @return the room name.
     */
    public String getRoomName()
    {
        return roomName;
    }

    /**
     * Set the roomName of the conference being transcribed
     *
     * @param roomName the roomName
     */
    public void setRoomName(String roomName)
    {
        this.roomName = roomName;
    }

    /**
     * Get the room URL of the conference being transcribed
     *
     * @return the room URL
     */
    public String getRoomUrl()
    {
        return this.roomName;
    }

    /**
     * Set the roomUrl of the conference being transcribed
     *
     * @param roomUrl the room URL
     */
    public void setRoomUrl(String roomUrl)
    {
        this.roomUrl = roomUrl;
    }

    /**
     * Notifies all <tt>TranscriptionEventListener</tt>s for new
     * <tt>TranscriptEvent</tt>.
     * @param event the new event.
     */
    private void fireTranscribeEvent(TranscriptEvent event)
    {
        for (TranscriptionEventListener listener : transcriptionEventListeners)
        {
            listener.notify(this, event);
        }
    }

    /**
     * Get whether translation is enabled.
     *
     * @return true if enabled, otherwise returns false.
     */
    private boolean isTranslationEnabled()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ENABLE_TRANSLATION,
                    ENABLE_TRANSLATION_DEFAULT_VALUE);
    }

    /**
     * Get whether the {@link Participant} should filter out audio lacking
     * speech.
     *
     * @return true when silence audio should be filtered, false otherwise
     */
    private boolean shouldFilterSilence()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_FILTER_SILENCE, FILTER_SILENCE_DEFAULT_VALUE)
            && !this.transcriptionService.disableSilenceFilter();
    }
}

Jigasi
======

Jitsi Gateway to SIP : a server-side application that links allows regular SIP clients to join Jitsi Meet conferences hosted by Jitsi Videobridge.

Install and run
============

It is possible to install Jigasi along with Jitsi Meet using our [quick install instructions] or do this from sources using the instructions below.

[quick install instructions]: https://github.com/jitsi/jitsi-meet/blob/master/doc/quick-install.md

1. Checkout latest source:
 
 ```
 git clone https://github.com/jitsi/jigasi.git
 ```
2. Build:

 ```
 cd jigasi
 mvn install -Dassembly.skipAssembly=false
 ```

3. Extract - choose either `jigasi-linux-x64-{version}.zip`, `jigasi-linux-x86-{version}.zip` or `jigasi-macosx-{version}.zip` based on the system.

 ```
 cd target/
 unzip jigasi-{os-version}-{version}.zip
 ```

4. Configure external component in your XMPP server. If your server is Prosody: edit /etc/prosody/prosody.cfg.lua and append following lines to your config (assuming that subdomain is 'callcontrol' and domain 'meet.jit.si'):

 ```
 Component "callcontrol.meet.jit.si"
     component_secret = "topsecret"
 ```
5. Setup SIP account

 Go to jigasi/jigasi-home and edit sip-communicator.properties file. Replace ```<<JIGASI_SIPUSER>>``` tag with SIP username for example: "user1232@sipserver.net". Then put Base64 encoded password in place of ```<<JIGASI_SIPPWD>>```.

6. Start Jigasi
 
 ```
 cd jigasi/target/jigasi-{os-version}-{version}/
 ./jigasi.sh --domain=meet.jit.si --subdomain=callcontrol --secret=topsecret
 ```
After Jigasi is started it will register as XMPP component under the 'callcontrol' subdomain. In Jitsi Meet application -> config.js -> hosts.call_control must be set to 'callcontrol.meet.jit.si'. This will enable SIP calls in Jitsi Meet.

Supported arguments:
 * --domain: specifies the XMPP domain to use.
 * --host: the IP address or the name of the XMPP host to connect to (localhost by default).
 * --port: the port of the XMPP host to connect on (5347 by default).
 * --subdomain: subdomain name for SIP gateway component.
 * --secret: the secret key for the sub-domain of the Jabber component implemented by this application with which it is to authenticate to the XMPP server to connect to.
 * --min-port: the minimum port number that we'd like our RTP managers to bind upon.
 * --max-port: the maximum port number that we'd like our RTP managers to bind upon.

How it works
============

Jigasi registers as a SIP client and can be called or be used by Jitsi Meet to make outgoing calls. Jigasi is NOT a SIP server. It is just a connector that allows SIP servers and B2BUAs to connect to Jitsi Meet. It handles the XMPP signalling, ICE, DTLS/SRTP termination and multiple-SSRC handling for them.

Outgoing calls
==============

To call someone from Jitsi Meet application, Jigasi must be configured and started like described in the 'Install and run' section. This will cause the telephone icon to appear in the toolbar which will popup a call dialog on click.

Incoming calls
==============

Jigasi will register on your SIP server with some identity and it will accept calls. When Jigasi is called, it expects to find a 'Jitsi-Conference-Room' header in the invite with the name of the Jitsi Meet conference the call is to be patched through to. If no header is present it will join the room specified under 'org.jitsi.jigasi.DEFAULT_JVB_ROOM_NAME' config property. In order to change it, edit 'jigasi-home/sipcommunciator.properties' file.

Example:

Received SIP INVITE with room header 'Jitsi-Conference-Room': 'room1234' will cause Jigasi to join the conference 'https://meet.jit.si/room1234' (assuming that our domain is 'meet.jit.si').

Configuring SIP and Transcription
=======================================

It is possible to either enable or disable the functionality of SIP and 
transcription. By default, the properties 
`org.jitsi.jigasi.enableTranscription=false` 
and 
`org.jitsi.jigasi.enableSip=true` 
in
`jigasi-home/sip-communicator.properties` 
enable SIP and disable transcription. To change this, simple set the desired
property to `true` or `false`.


Using Jigasi to transcribe a Jitsi Meet conference
==================================================

It is also possible to use Jigasi as a provider of nearly real-time transcription
while a conference is ongoing as well as serving a complete transcription
after the conference is over. This can be done by using the SIP dial button and 
using the the URI `jitsi_meet_transcribe`. 
Currently Jigasi can send speech-to-text results to
the chat of a Jitsi Meet room as either plain text or JSON. If it's send in JSON,
Jitsi Meet will provide subtitles in the left corner of the video, while plain text
will just be posted in the chat. Jigasi will also provide a link to where the final, 
complete transcript will be served when it enters the room.

For jigasi to act as a transcriber, it sends the audio of all participants in the
room to an external speech-to-text service. Currently only the [Google Cloud speech-to-text API](https://cloud.google.com/speech/) is supported.
It is required to install the [Google Cloud SDK](https://cloud.google.com/sdk/docs/)
on the machine running Jigasi. To install on a regular debian/ubuntu environment:

```
export CLOUD_SDK_REPO="cloud-sdk-$(lsb_release -c -s)"
echo "deb http://packages.cloud.google.com/apt $CLOUD_SDK_REPO main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
sudo apt-get update && sudo apt-get install google-cloud-sdk google-cloud-sdk-app-engine-java
gcloud init
gcloud auth application-default login
```
There are several configuration options regarding transcription. These should
be placed in `~/jigasi/jigasi-home/sip-communicator.properties`. The default 
value will be used when the property is not set in the property file. A valid 
XMPP account must also be set to make Jigasi be able to join a conference room.
<table>
    <tr>
        <th>Property name</th>
        <th>Default value</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.DIRECTORY</td>
        <td>/var/lib/jigasi/transcripts</td>
        <td>The folder which will be used to store and serve the final 
            transcripts.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.BASE_URL</td>
        <td>http://localhost/</td>
        <td>The base URL which will be used to serve the final transcripts. 
            The URL used to serve a transcript will be this base appended by the
            filename of the transcript.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.jetty.port</td>
        <td>-1</td>
        <td>The port which will be used to serve the final transcripts. Its 
            default value is -1, which means the Jetty instance serving the 
            transcript files is turned off.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.ADVERTISE_URL</td>
        <td>false</td>
        <td>Whether or not to advertise the URL which will serve the final 
            transcript when Jigasi joins the room.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.SAVE_JSON</td>
        <td>false</td>
        <td>Whether or not to save the final transcript in JSON. Note that this
            format is not very human readable.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.SAVE_TXT</td>
        <td>true</td>
        <td>Whether or not to save the final transcript in plain text.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.SEND_JSON</td>
        <td>true</td>
        <td>Whether or not to send results, when they come in, to the chatroom 
            in JSON. Note that this will result in subtitles being shown.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.SEND_TXT</td>
        <td>false</td>
        <td>Whether or not to send results, when they come in, to the chatroom 
            in plain text. Note that this will result in the chat being somewhat
            spammed.</td>
    </tr>
</table>

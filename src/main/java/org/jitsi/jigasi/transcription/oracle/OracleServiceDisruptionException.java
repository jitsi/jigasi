package org.jitsi.jigasi.transcription.oracle;

public class OracleServiceDisruptionException extends Exception
{
    public OracleServiceDisruptionException(String message) {
        super(message);
    }
    public OracleServiceDisruptionException(Throwable e){
        super(e);
    }
}

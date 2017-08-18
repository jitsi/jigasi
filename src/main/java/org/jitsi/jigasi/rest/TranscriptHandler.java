package org.jitsi.jigasi.rest;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.jitsi.jigasi.transcription.*;
import org.jitsi.util.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.nio.file.*;

/**
 *
 */
public class TranscriptHandler
    extends AbstractHandler
{
    /**
     * The logger of this class
     */
    private static final Logger logger
        = Logger.getLogger(TranscriptHandler.class);

    /**
     * The directory wherein the files should be found
     */
    private static final String FILE_DIRECTORY
        = AbstractTranscriptPublisher.getLogDirPath();

    /**
     * The target which indicates this Handler should serve a file
     */
    private static final String TRANSCRIPT_TARGET = "/transcripts/";

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
        throws IOException, ServletException
    {
        if(!target.startsWith(TRANSCRIPT_TARGET) ||
            StringUtils.countMatches("/" , target) == 2)
        {
            sendError(HttpServletResponse.SC_BAD_REQUEST,
                "HTTP request target should be \"/transcripts/<file_name>\"",
                baseRequest, response);
            return;
        }

        String fileName = extractFileName(target);

        if(fileName.isEmpty())
        {
            sendError(HttpServletResponse.SC_BAD_REQUEST,
                "HTTP request target should be \"/transcripts/<file_name>\"",
                baseRequest, response);
            return;
        }

        File storageDir = Paths.get(FILE_DIRECTORY).toFile();
        File[] files = storageDir.listFiles();
        if(files == null)
        {
            sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error trying to look up files", baseRequest, response);
            return;
        }

        for(File file : files)
        {
            String name = file.getName();
            System.out.println(name);
            if(name.equals(fileName))
            {
                System.out.println("Serving " + name);
                response.setStatus(HttpServletResponse.SC_OK);

                String extension = "";

                int i = fileName.lastIndexOf('.');
                if(i > 0)
                {
                    extension = fileName.substring(i+1);
                }

                if(extension.equals("json"))
                {
                    response.setContentType("application/json");
                }
                else // assume txt
                {
                    response.setContentType("text/plain");
                }

                String content
                    = new String(Files.readAllBytes(
                        Paths.get(file.getAbsolutePath())));

                response.getWriter().write(content);
                response.getWriter().flush();
                return;
            }
        }

        sendError(HttpServletResponse.SC_BAD_REQUEST,
            "Could not locate file " + fileName,
            baseRequest, response);
    }

    /**
     * Given a target string like "/transcripts/<file_name>", extraxt the file
     * name from this string
     *
     * @param target the string
     * @return the fileName in the target string or empty when failed
     */
    private String extractFileName(String target)
    {
        String[] tokens = target.split("/");
        return tokens.length == 3 ? tokens[2] : "";
    }

    /**
     * Method to finish the request with an error
     *
     * @param status the http error status
     * @param message the message of the error
     * @param baseRequest the original request to reply to
     * @param response the HttpServletResponse object to use as an reply
     */
    private void sendError(int status, String message, Request baseRequest,
                           HttpServletResponse response)
    {
        response.setStatus(status);
        try
        {
            response.setContentType("text/plain");
            response.getWriter().println(message);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
        baseRequest.setHandled(true);
    }
}

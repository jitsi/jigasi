/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.utils.logging.Logger;
import java.io.IOException;


/**
 * Implements a {@link TranslationService} which will use LibreTranslate API to translate the given text from one
 * language to another.
 * <p>
 * <a href="https://github.com/LibreTranslate/LibreTranslate">LibreTranslate</a>
 * for more information about LibreTranslate
 * @author Pinglei He
 */
public class LibreTranslateTranslationService
        implements TranslationService
{
    /*
     * Class representing the json response body from LibreTranslate where response has status code 200.
     * Used for json-to-POJO conversion with Gson.
     */
    class LibreTranslateResponse
    {
        private String translatedText;

        public String getTranslatedText()
        {
            return translatedText;
        }
    }

    /*
     * The URL of the LibreTranslate API.
     */
    public final String API_URL = "org.jitsi.jigasi.transcription.libreTranslate.api_url";

    public final String DEFAULT_API_URL = "http://libretranslate:5000/translate";

    private final String apiUrl;

    private final Logger logger = Logger.getLogger(LibreTranslateTranslationService.class);

    public LibreTranslateTranslationService()
    {
        apiUrl = JigasiBundleActivator.getConfigurationService().getString(API_URL, DEFAULT_API_URL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String translate(String sourceText, String sourceLang, String targetLang)
    {
        String payload = "{"
                .concat("\"q\": \"" + sourceText + "\",")
                .concat("\"source\": \"" + sourceLang.substring(0, 2) + "\",")
                .concat("\"target\": \"" + targetLang.substring(0, 2) + "\",")
                .concat("\"format\": \"text\",")
                .concat("\"api_key\": \"\"")
                .concat("}");

        StringEntity entity = new StringEntity(payload, ContentType.APPLICATION_JSON);

        HttpResponse response;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build())
        {
            HttpPost request = new HttpPost(apiUrl);
            request.setEntity(entity);
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-type", "application/json");
            response = httpClient.execute(request);
            String jsonBody = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200)
            {
                logger.error("LibreTranslate responded with status code " + statusCode + ".");
                logger.error(jsonBody);
                return "";
            }
            Gson gson = new GsonBuilder().create();
            LibreTranslateResponse translateResponse = gson.fromJson(jsonBody, LibreTranslateResponse.class);
            return translateResponse.getTranslatedText();
        }
        catch (IOException e)
        {
            logger.error("Error during request to LibreTranslate service.");
            logger.error(e.toString());
            return "";
        }
    }
}

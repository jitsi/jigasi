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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.utils.logging.Logger;

import java.io.IOException;
import java.util.List;


/**
 * Implements a {@link TranslationService} which uses the Microsoft Translator
 * Text API (v3) to translate a given text from one language to another.
 * <p>
 * The service requires a valid Azure Cognitive Services subscription key,
 * passed through the configuration property
 * {@code org.jitsi.jigasi.transcription.bing.subscription_key}. For
 * multi-service or regional resources the region must additionally be set via
 * {@code org.jitsi.jigasi.transcription.bing.subscription_region}.
 * <p>
 * <a href="https://learn.microsoft.com/azure/ai-services/translator/reference/v3-0-translate">
 * Microsoft Translator v3 reference</a> for request / response details.
 */
public class BingTranslationService
        implements TranslationService
{
    /**
     * Property name for the Azure subscription key used to authenticate
     * against the Translator API. Required; without it every call returns
     * an empty string.
     */
    public final static String SUBSCRIPTION_KEY
        = "org.jitsi.jigasi.transcription.bing.subscription_key";

    /**
     * Property name for the Azure region of the Translator resource.
     * Required for multi-service and regional resources.
     */
    public final static String SUBSCRIPTION_REGION
        = "org.jitsi.jigasi.transcription.bing.subscription_region";

    /**
     * Property name for the Translator endpoint. Defaults to the global
     * endpoint; a custom-domain resource would use its own hostname.
     */
    public final static String ENDPOINT
        = "org.jitsi.jigasi.transcription.bing.endpoint";

    /**
     * Property name for the Translator API version. Defaults to {@code 3.0}.
     */
    public final static String API_VERSION
        = "org.jitsi.jigasi.transcription.bing.api_version";

    public final static String DEFAULT_ENDPOINT
        = "https://api.cognitive.microsofttranslator.com";

    public final static String DEFAULT_API_VERSION = "3.0";

    private final String subscriptionKey;

    private final String subscriptionRegion;

    private final String endpoint;

    private final String apiVersion;

    private final Logger logger
        = Logger.getLogger(BingTranslationService.class);

    public BingTranslationService()
    {
        subscriptionKey = JigasiBundleActivator.getConfigurationService()
            .getString(SUBSCRIPTION_KEY, "");
        subscriptionRegion = JigasiBundleActivator.getConfigurationService()
            .getString(SUBSCRIPTION_REGION, "");
        endpoint = JigasiBundleActivator.getConfigurationService()
            .getString(ENDPOINT, DEFAULT_ENDPOINT);
        apiVersion = JigasiBundleActivator.getConfigurationService()
            .getString(API_VERSION, DEFAULT_API_VERSION);
    }

    /**
     * Utility function to extract the primary language code like:
     * 'en-GB', 'en_GB', 'enGB', 'zh-CN', 'zh-TW'
     * <p>
     * Behaves equivalent to the function "_getPrimaryLanguageCode"
     * in jitsi-meet/blob/master/react/features/subtitles/middleware.ts,
     * matching {@link LibreTranslateTranslationService}.
     *
     * @param language The language to use for translation or user requested.
     * @return Primary language code
     */
    private static String getPrimaryLanguageCode(String language)
    {
        if (language == null)
        {
            return "auto";
        }

        return language.replaceAll("[-_A-Z].*", "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String translate(String sourceText, String sourceLang,
                            String targetLang)
    {
        if (subscriptionKey == null || subscriptionKey.isEmpty())
        {
            logger.error("Bing translation requested but "
                + SUBSCRIPTION_KEY + " is not set.");
            return "";
        }

        String from = getPrimaryLanguageCode(sourceLang);
        String to = getPrimaryLanguageCode(targetLang);

        StringBuilder url = new StringBuilder(endpoint);
        if (!endpoint.endsWith("/"))
        {
            url.append('/');
        }
        url.append("translate?api-version=").append(apiVersion);
        if (!"auto".equals(from))
        {
            url.append("&from=").append(from);
        }
        url.append("&to=").append(to);

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        JsonArray body = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("Text", sourceText);
        body.add(item);

        StringEntity entity = new StringEntity(
            gson.toJson(body), ContentType.APPLICATION_JSON);

        HttpResponse response;
        try (CloseableHttpClient httpClient
                = HttpClientBuilder.create().build())
        {
            HttpPost request = new HttpPost(url.toString());
            request.setEntity(entity);
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-type", "application/json");
            request.setHeader(
                "Ocp-Apim-Subscription-Key", subscriptionKey);
            if (subscriptionRegion != null && !subscriptionRegion.isEmpty())
            {
                request.setHeader(
                    "Ocp-Apim-Subscription-Region", subscriptionRegion);
            }

            response = httpClient.execute(request);
            String jsonBody = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200)
            {
                logger.error("Microsoft Translator responded with status code "
                    + statusCode + ".");
                logger.error(jsonBody);
                return "";
            }

            BingResponse[] parsed
                = gson.fromJson(jsonBody, BingResponse[].class);
            if (parsed == null || parsed.length == 0
                || parsed[0].translations == null
                || parsed[0].translations.isEmpty())
            {
                logger.error(
                    "Microsoft Translator returned an empty translation set: "
                        + jsonBody);
                return "";
            }
            String text = parsed[0].translations.get(0).text;
            return text == null ? "" : text;
        }
        catch (IOException e)
        {
            logger.error("Error during request to Microsoft Translator.");
            logger.error(e.toString());
            return "";
        }
    }

    /**
     * Class representing the top-level JSON response objects returned by the
     * Microsoft Translator v3 {@code /translate} endpoint. Used for
     * JSON-to-POJO conversion with Gson.
     */
    static class BingResponse
    {
        List<BingTranslation> translations;
    }

    /**
     * Class representing each element inside the {@code translations} array
     * of a Microsoft Translator v3 response.
     */
    static class BingTranslation
    {
        String text;
        String to;
    }
}

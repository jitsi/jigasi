/*
 * Copyright @ 2015 - present, 8x8 Inc
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
package org.jitsi.jigasi.rest;

import java.util.regex.*;

/**
 * Class gathers utility methods HTTP and REST related.
 *
 * @author Pawel Domas
 */
public class RESTUtil
{
    /**
     * The MIME type of HTTP content in JSON format.
     */
    public static final String JSON_CONTENT_TYPE = "application/json";

    /**
     * The MIME type of HTTP content in JSON format with a charset.
     */
    public static final String JSON_CONTENT_TYPE_WITH_CHARSET
        = JSON_CONTENT_TYPE + ";charset=UTF-8";

    /**
     * Pattern matcher used to detect JSON content type.
     */
    private static final Pattern JsonContentTypeMatcher
        = Pattern.compile(
        "^\\s?application/json((\\s?)|(;\\s?(charset=UTF-8\\s?)?$))?");

    /**
     * Determines whether a specific MIME type of HTTP content specifies a JSON
     * representation.
     *
     * @param contentType the MIME type of HTTP content to determine whether it
     * specifies a JSON representation
     * @return {@code true} if {@code contentType} stands for a MIME type of
     * HTTP content which specifies a JSON representation; otherwise,
     * {@code false}
     */
    static public boolean isJSONContentType(String contentType)
    {
        return contentType != null &&
            JsonContentTypeMatcher.matcher(contentType).matches();
    }
}

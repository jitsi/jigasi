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

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Some test for JSON pattern matcher.
 *
 * @author Pawel Domas
 */
public class RESTUtilTests
{
    @Test
    public void testJSONContentMatcher()
    {
        assertFalse(
            RESTUtil.isJSONContentType(""));
        assertFalse(
            RESTUtil.isJSONContentType(null));

        assertTrue(
            RESTUtil.isJSONContentType(
                RESTUtil.JSON_CONTENT_TYPE));

        assertTrue(
            RESTUtil.isJSONContentType(
                RESTUtil.JSON_CONTENT_TYPE_WITH_CHARSET));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json"));

        assertTrue(
            RESTUtil.isJSONContentType(
                " application/json"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json "));

        assertFalse(
            RESTUtil.isJSONContentType(
                "bapplication/json"));

        assertFalse(
            RESTUtil.isJSONContentType(
                "application/jsonx"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json;"));

        assertTrue(
            RESTUtil.isJSONContentType(
                " application/json;"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json; "));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json; "));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json;charset=UTF-8"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json; charset=UTF-8"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json; charset=UTF-8 "));

        assertFalse(
            RESTUtil.isJSONContentType(
                "application/json; charset=UTF-88"));
    }
}

/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.integration.authentication.providers;

import io.restassured.RestAssured;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.zowe.apiml.util.SecurityUtils;
import org.zowe.apiml.util.TestWithStartedInstances;
import org.zowe.apiml.util.categories.GeneralAuthenticationTest;
import org.zowe.apiml.util.categories.SAFAuthTest;
import org.zowe.apiml.util.categories.zOSMFAuthTest;
import org.zowe.apiml.util.config.ConfigReader;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.zowe.apiml.util.requests.Endpoints.*;

/**
 * Verify that querying of the token works properly. The tests needs to pass against every valid authentication provider.
 */
@GeneralAuthenticationTest
@SAFAuthTest
@zOSMFAuthTest
class QueryTest implements TestWithStartedInstances {
    private final static String SCHEME = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration().getScheme();
    private final static String HOST = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration().getHost();
    private final static int PORT = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration().getPort();
    private final static String PASSWORD = ConfigReader.environmentConfiguration().getCredentials().getPassword();
    private final static String USERNAME = ConfigReader.environmentConfiguration().getCredentials().getUser();
    private final static String COOKIE = "apimlAuthenticationToken";

    public static final String QUERY_ENDPOINT_URL = String.format("%s://%s:%d%s", SCHEME, HOST, PORT, ROUTED_QUERY);
    
    private String token;
    
    @BeforeEach
    void setUp() {
        RestAssured.port = PORT;
        RestAssured.useRelaxedHTTPSValidation();

        token = SecurityUtils.gatewayToken(USERNAME, PASSWORD);
    }

    @Nested
    class WhenQueryingToken {
        @Nested
        class ReturnInfo {
            //@formatter:off
            @Test
            void givenValidTokenInHeader() {
                given()
                    .header("Authorization", "Bearer " + token)
                .when()
                    .get(QUERY_ENDPOINT_URL)
                .then()
                    .statusCode(is(SC_OK))
                    .body("userId", equalTo(USERNAME));
            }

            @Test
            void givenValidTokenInCookie() {
                given()
                    .cookie(COOKIE, token)
                .when()
                    .get(QUERY_ENDPOINT_URL)
                .then()
                    .statusCode(is(SC_OK))
                    .body("userId", equalTo(USERNAME));
            }
        }

        @Nested
        class ReturnUnauthorized {
            @Test
            void givenInvalidTokenInBearerHeader() {
                String invalidToken = "1234";
                String queryUrl = QUERY_ENDPOINT_URL;
                String queryPath = queryUrl.substring(StringUtils.ordinalIndexOf(queryUrl,"/",3));
                String expectedMessage = "Token is not valid for URL '" + queryPath + "'";

                given()
                    .header("Authorization", "Bearer " + invalidToken)
                    .contentType(JSON)
                .when()
                    .get(queryUrl)
                .then()
                    .statusCode(is(SC_UNAUTHORIZED))
                    .body(
                        "messages.find { it.messageNumber == 'ZWEAG130E' }.messageContent", equalTo(expectedMessage)
                    );
            }

            @Test
            void givenInvalidTokenInCookie() {
                String invalidToken = "1234";
                String queryUrl = QUERY_ENDPOINT_URL;
                String queryPath = queryUrl.substring(StringUtils.ordinalIndexOf(queryUrl,"/",3));
                String expectedMessage = "Token is not valid for URL '" + queryPath + "'";

                given()
                    .cookie(COOKIE, invalidToken)
                .when()
                    .get(queryUrl)
                .then()
                    .statusCode(is(SC_UNAUTHORIZED))
                    .body(
                        "messages.find { it.messageNumber == 'ZWEAG130E' }.messageContent", equalTo(expectedMessage)
                    );
            }

            @Test
            void givenNoToken() {
                String queryUrl = QUERY_ENDPOINT_URL;
                String queryPath = queryUrl.substring(StringUtils.ordinalIndexOf(queryUrl,"/",3));
                String expectedMessage = "No authorization token provided for URL '" + queryPath + "'";

                given()
                .when()
                    .get(queryUrl)
                .then()
                    .statusCode(is(SC_UNAUTHORIZED))
                    .body(
                        "messages.find { it.messageNumber == 'ZWEAG131E' }.messageContent", equalTo(expectedMessage)
                    );
            }

            @Test
            void givenValidTokenInWrongCookie() {
                String invalidCookie = "badCookie";
                String queryUrl = QUERY_ENDPOINT_URL;
                String queryPath = queryUrl.substring(StringUtils.ordinalIndexOf(queryUrl,"/",3));
                String expectedMessage = "No authorization token provided for URL '" + queryPath + "'";

                given()
                    .cookie(invalidCookie, token)
                .when()
                    .get(queryUrl)
                .then()
                    .statusCode(is(SC_UNAUTHORIZED))
                    .body(
                        "messages.find { it.messageNumber == 'ZWEAG131E' }.messageContent", equalTo(expectedMessage)
                    );
            }
        }
    }

    @Nested
    class WhenUserQueriesViaPostMethod {
        @Nested
        class ReturnMethodNotAllowed {
            @Test
            void givenValidToken() {
                String queryUrl = QUERY_ENDPOINT_URL;
                String queryPath = queryUrl.substring(StringUtils.ordinalIndexOf(queryUrl,"/",3));
                String expectedMessage = "Authentication method 'POST' is not supported for URL '" + queryPath + "'";

                given()
                    .header("Authorization", "Bearer " + token)
                .when()
                    .post(queryUrl)
                .then()
                    .body(
                        "messages.find { it.messageNumber == 'ZWEAG101E' }.messageContent", equalTo(expectedMessage)
                    );
            }
        }
    }
    //@formatter:on
}

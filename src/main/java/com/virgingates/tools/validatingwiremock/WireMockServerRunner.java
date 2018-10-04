/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.virgingates.tools.validatingwiremock;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.JsonValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FatalStartupException;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.standalone.MappingsLoader;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.stubbing.StubMappings;

import javax.annotation.Nonnull;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;

import java.net.URI;
import java.util.Optional;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockApp.FILES_ROOT;
import static com.github.tomakehurst.wiremock.core.WireMockApp.MAPPINGS_ROOT;
import static com.github.tomakehurst.wiremock.http.RequestMethod.ANY;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static java.lang.System.out;

public class WireMockServerRunner {

    private static final String BANNER = " /$$      /$$ /$$                     /$$      /$$                     /$$      \n" +
            "| $$  /$ | $$|__/                    | $$$    /$$$                    | $$      \n" +
            "| $$ /$$$| $$ /$$  /$$$$$$   /$$$$$$ | $$$$  /$$$$  /$$$$$$   /$$$$$$$| $$   /$$\n" +
            "| $$/$$ $$ $$| $$ /$$__  $$ /$$__  $$| $$ $$/$$ $$ /$$__  $$ /$$_____/| $$  /$$/\n" +
            "| $$$$_  $$$$| $$| $$  \\__/| $$$$$$$$| $$  $$$| $$| $$  \\ $$| $$      | $$$$$$/ \n" +
            "| $$$/ \\  $$$| $$| $$      | $$_____/| $$\\  $ | $$| $$  | $$| $$      | $$_  $$ \n" +
            "| $$/   \\  $$| $$| $$      |  $$$$$$$| $$ \\/  | $$|  $$$$$$/|  $$$$$$$| $$ \\  $$\n" +
            "|__/     \\__/|__/|__/       \\_______/|__/     |__/ \\______/  \\_______/|__/  \\__/";

    static {
        System.setProperty("org.mortbay.log.class", "com.github.tomakehurst.wiremock.jetty.LoggerAdapter");
    }

    private WireMockServer wireMockServer;

    public void run(String... args) {
        CommandLineOptions options = new com.virgingates.tools.validatingwiremock.CommandLineOptions(args);
        if (options.help()) {
            out.println(options.helpText());
            return;
        }

        FileSource fileSource = options.filesRoot();
        fileSource.createIfNecessary();
        FileSource filesFileSource = fileSource.child(FILES_ROOT);
        filesFileSource.createIfNecessary();
        FileSource mappingsFileSource = fileSource.child(MAPPINGS_ROOT);
        mappingsFileSource.createIfNecessary();

        wireMockServer = new WireMockServer(options);

        if (options.openAPIFile().length()!=0) wireMockServer.addMockServiceRequestListener(new OpenApiValidationListener(options.openAPIFile()));

        if (options.recordMappingsEnabled()) {
            wireMockServer.enableRecordMappings(mappingsFileSource, filesFileSource);
        }

        if (options.specifiesProxyUrl()) {
            addProxyMapping(options.proxyUrl());
        }

        try {
            wireMockServer.start();
            options.setResultingPort(wireMockServer.port());
            if (!options.bannerDisabled()){
                out.println(BANNER);
                out.println();
            } else  {
                out.println();
                out.println("The WireMock server is started .....");
            }
            out.println(options);
        } catch (FatalStartupException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private void addProxyMapping(final String baseUrl) {
        wireMockServer.loadMappingsUsing(new MappingsLoader() {
            @Override
            public void loadMappingsInto(StubMappings stubMappings) {
                RequestPattern requestPattern = newRequestPattern(ANY, anyUrl()).build();
                ResponseDefinition responseDef = responseDefinition()
                        .proxiedFrom(baseUrl)
                        .build();

                StubMapping proxyBasedMapping = new StubMapping(requestPattern, responseDef);
                proxyBasedMapping.setPriority(10); // Make it low priority so that existing stubs will take precedence
                stubMappings.addMapping(proxyBasedMapping);
            }
        });
    }

    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    public boolean isRunning() {
        if (wireMockServer == null) {
            return false;
        } else {
            return wireMockServer.isRunning();
        }
    }

    public int port() { return wireMockServer.port(); }

    public static void main(String... args) {
        new WireMockServerRunner().run(args);
    }
}

class OpenApiValidationListener implements RequestListener {
    private final OpenApiInteractionValidator validator;
    private ValidationReport report = ValidationReport.empty();

    public OpenApiValidationListener(final String specUrlOrDefinition) {
        validator = OpenApiInteractionValidator.createFor(specUrlOrDefinition).build();
    }


    @Override
    public void requestReceived(final Request request, final Response response) {
        try {
            report = report.merge(validator.validate(WireMockRequest.of(request), WireMockResponse.of(response)));
            assertValidationPassed();
        } catch (final Exception e) {
            //log.error("Exception occurred while validating request", e);
            throw e;
        }
        finally {
            reset();
        }
    }

    /**
     * Access the current validation report. This will contain all messages since the last call to {@link #reset()}.
     * <p>
     * Most often clients will simply want to invoke {@link #assertValidationPassed()} rather than access
     * the report directly.
     *
     * @return the current validation report.
     */
    public ValidationReport getReport() {
        return report;
    }

    /**
     * Reset this listener instance and remove validation messages from the validation report.
     * <p>
     * This method should be invoked between tests to ensure validation messages don't carry over between test runs
     * e.g.
     * <pre>
     *     &#64;After
     *     public void tearDown() {
     *          validationListener.reset();
     *     }
     * </pre>
     */
    public void reset() {
        report = ValidationReport.empty();
    }

    /**
     * Assert that the current validation report contains no errors and fail if it does.
     *
     * @throws OpenApiValidationException if the current validation report contains any errors.
     */
    public void assertValidationPassed() {
        if (report.hasErrors()) {
            throw new OpenApiValidationException(report);
        }
    }

    public static class OpenApiValidationException extends RuntimeException {

        private final ValidationReport report;

        public OpenApiValidationException(final ValidationReport report) {
            super(JsonValidationReportFormat.getInstance().apply(report));
            this.report = report;
        }

        public ValidationReport getValidationReport() {
            return report;
        }
    }

}

class WireMockRequest implements com.atlassian.oai.validator.model.Request {

    private final com.atlassian.oai.validator.model.Request delegate;

    /**
     * @deprecated Use: {@link WireMockRequest#of(com.github.tomakehurst.wiremock.http.Request)}
     */
    @Deprecated
    public WireMockRequest(@Nonnull final com.github.tomakehurst.wiremock.http.Request originalRequest) {
        delegate = WireMockRequest.of(originalRequest);
    }

    @Nonnull
    @Override
    public String getPath() {
        return delegate.getPath();
    }

    @Nonnull
    @Override
    public com.atlassian.oai.validator.model.Request.Method getMethod() {
        return delegate.getMethod();
    }

    @Nonnull
    @Override
    public Optional<String> getBody() {
        return delegate.getBody();
    }

    @Nonnull
    @Override
    public Collection<String> getQueryParameters() {
        return delegate.getQueryParameters();
    }

    @Nonnull
    @Override
    public Collection<String> getQueryParameterValues(final String name) {
        return delegate.getQueryParameterValues(name);
    }

    @Nonnull
    @Override
    public Map<String, Collection<String>> getHeaders() {
        return delegate.getHeaders();
    }

    @Nonnull
    @Override
    public Collection<String> getHeaderValues(final String name) {
        return delegate.getHeaderValues(name);
    }

    /**
     * Builds a {@link Request} for the OpenAPI validator out of the
     * original {@link com.github.tomakehurst.wiremock.http.Request}.
     *
     * @param originalRequest the original {@link com.github.tomakehurst.wiremock.http.Request}
     */
    @Nonnull
    public static com.atlassian.oai.validator.model.Request of(@Nonnull final com.github.tomakehurst.wiremock.http.Request originalRequest) {
        requireNonNull(originalRequest, "An original request is required");

        final URI uri = URI.create(originalRequest.getUrl());
        final Map<String, QueryParameter> queryParameterMap = Urls.splitQuery(uri);

        final SimpleRequest.Builder builder =
                new SimpleRequest.Builder(originalRequest.getMethod().getName(), uri.getPath())
                        .withBody(originalRequest.getBodyAsString());
        originalRequest.getHeaders().all().forEach(header -> builder.withHeader(header.key(), header.values()));
        queryParameterMap.forEach((key, value) -> builder.withQueryParam(key, value.values()));
        return builder.build();
    }
}

class WireMockResponse implements com.atlassian.oai.validator.model.Response {

    private final com.atlassian.oai.validator.model.Response delegate;

    /**
     * @deprecated Use: {@link WireMockResponse#of(com.github.tomakehurst.wiremock.http.Response)}
     */
    @Deprecated
    public WireMockResponse(@Nonnull final com.github.tomakehurst.wiremock.http.Response originalResponse) {
        delegate = WireMockResponse.of(originalResponse);
    }

    @Override
    public int getStatus() {
        return delegate.getStatus();
    }

    @Nonnull
    @Override
    public Optional<String> getBody() {
        return delegate.getBody();
    }

    @Nonnull
    @Override
    public Collection<String> getHeaderValues(final String name) {
        return delegate.getHeaderValues(name);
    }

    /**
     * Builds a {@link Response} for the OpenAPI validator out of the
     * original {@link com.github.tomakehurst.wiremock.http.Response}.
     *
     * @param originalResponse the original {@link com.github.tomakehurst.wiremock.http.Response}
     */
    @Nonnull
    public static com.atlassian.oai.validator.model.Response of(@Nonnull final com.github.tomakehurst.wiremock.http.Response originalResponse) {
        requireNonNull(originalResponse, "An original response is required");
        final SimpleResponse.Builder builder = new SimpleResponse.Builder(originalResponse.getStatus())
                .withBody(originalResponse.getBodyAsString());
        originalResponse.getHeaders().all().forEach(header -> builder.withHeader(header.key(), header.values()));
        return builder.build();
    }
}

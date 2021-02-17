package com.virgingates.tools.validatingwiremock;
import com.github.tomakehurst.wiremock.common.*;
import com.github.tomakehurst.wiremock.core.MappingsSaver;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockApp;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionLoader;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.http.CaseInsensitiveKey;
import com.github.tomakehurst.wiremock.http.HttpServerFactory;
import com.github.tomakehurst.wiremock.http.ThreadPoolFactory;
import com.github.tomakehurst.wiremock.http.trafficlistener.ConsoleNotifyingWiremockNetworkTrafficListener;
import com.github.tomakehurst.wiremock.http.trafficlistener.DoNothingWiremockNetworkTrafficListener;
import com.github.tomakehurst.wiremock.http.trafficlistener.WiremockNetworkTrafficListener;
import com.github.tomakehurst.wiremock.jetty9.QueuedThreadPoolFactory;
import com.github.tomakehurst.wiremock.security.Authenticator;
import com.github.tomakehurst.wiremock.security.BasicAuthenticator;
import com.github.tomakehurst.wiremock.security.NoAuthenticator;
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource;
import com.github.tomakehurst.wiremock.standalone.MappingsLoader;
import com.github.tomakehurst.wiremock.standalone.MappingsSource;
import com.github.tomakehurst.wiremock.verification.notmatched.NotMatchedRenderer;
import com.github.tomakehurst.wiremock.verification.notmatched.PlainTextStubNotMatchedRenderer;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.io.Resources;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.common.Exceptions.throwUnchecked;
import static com.github.tomakehurst.wiremock.common.ProxySettings.NO_PROXY;
import static com.github.tomakehurst.wiremock.core.WireMockApp.MAPPINGS_ROOT;
import static com.github.tomakehurst.wiremock.extension.ExtensionLoader.valueAssignableFrom;
import static com.github.tomakehurst.wiremock.http.CaseInsensitiveKey.TO_CASE_INSENSITIVE_KEYS;
import static java.lang.System.out;

public class CommandLineOptions extends com.github.tomakehurst.wiremock.standalone.CommandLineOptions {

    private static final String HELP = "help";
    private static final String RECORD_MAPPINGS = "record-mappings";
    private static final String MATCH_HEADERS = "match-headers";
    private static final String PROXY_ALL = "proxy-all";
    private static final String PRESERVE_HOST_HEADER = "preserve-host-header";
    private static final String PROXY_VIA = "proxy-via";
    private static final String PORT = "port";
    private static final String BIND_ADDRESS = "bind-address";
    private static final String HTTPS_PORT = "https-port";
    private static final String HTTPS_KEYSTORE = "https-keystore";
    private static final String HTTPS_KEYSTORE_PASSWORD = "keystore-password";
    private static final String HTTPS_TRUSTSTORE = "https-truststore";
    private static final String HTTPS_TRUSTSTORE_PASSWORD = "truststore-password";
    private static final String REQUIRE_CLIENT_CERT = "https-require-client-cert";
    private static final String VERBOSE = "verbose";
    private static final String ENABLE_BROWSER_PROXYING = "enable-browser-proxying";
    private static final String DISABLE_BANNER = "disable-banner";
    private static final String DISABLE_REQUEST_JOURNAL = "no-request-journal";
    private static final String EXTENSIONS = "extensions";
    private static final String MAX_ENTRIES_REQUEST_JOURNAL = "max-request-journal-entries";
    private static final String JETTY_ACCEPTOR_THREAD_COUNT = "jetty-acceptor-threads";
    private static final String PRINT_ALL_NETWORK_TRAFFIC = "print-all-network-traffic";
    private static final String JETTY_ACCEPT_QUEUE_SIZE = "jetty-accept-queue-size";
    private static final String JETTY_HEADER_BUFFER_SIZE = "jetty-header-buffer-size";
    private static final String JETTY_STOP_TIMEOUT = "jetty-stop-timeout";
    private static final String ROOT_DIR = "root-dir";
    private static final String CONTAINER_THREADS = "container-threads";
    private static final String GLOBAL_RESPONSE_TEMPLATING = "global-response-templating";
    private static final String LOCAL_RESPONSE_TEMPLATING = "local-response-templating";
    private static final String ADMIN_API_BASIC_AUTH = "admin-api-basic-auth";
    private static final String ADMIN_API_REQUIRE_HTTPS = "admin-api-require-https";
    private static final String ASYNCHRONOUS_RESPONSE_ENABLED = "async-response-enabled";
    private static final String ASYNCHRONOUS_RESPONSE_THREADS = "async-response-threads";

    private static final String OPENAPI_FILE = "openapi-file";


    private final OptionSet optionSet;
    private final FileSource fileSource;
    private final MappingsSource mappingsSource;

    private String helpText;
    private Optional<Integer> resultingPort;

    public CommandLineOptions(String... args) {
        OptionParser optionParser = new OptionParser();
        optionParser.accepts(PORT, "The port number for the server to listen on (default: 8080). 0 for dynamic port selection.").withRequiredArg();
        optionParser.accepts(HTTPS_PORT, "If this option is present WireMock will enable HTTPS on the specified port").withRequiredArg();
        optionParser.accepts(BIND_ADDRESS, "The IP to listen connections").withRequiredArg();
        optionParser.accepts(CONTAINER_THREADS, "The number of container threads").withRequiredArg();
        optionParser.accepts(REQUIRE_CLIENT_CERT, "Make the server require a trusted client certificate to enable a connection");
        optionParser.accepts(HTTPS_TRUSTSTORE_PASSWORD, "Password for the trust store").withRequiredArg();
        optionParser.accepts(HTTPS_TRUSTSTORE, "Path to an alternative truststore for HTTPS client certificates. Must have a password of \"password\".").requiredIf(REQUIRE_CLIENT_CERT).withRequiredArg();
        optionParser.accepts(HTTPS_KEYSTORE_PASSWORD, "Password for the alternative keystore.").withRequiredArg().defaultsTo("password");
        optionParser.accepts(HTTPS_KEYSTORE, "Path to an alternative keystore for HTTPS. Password is assumed to be \"password\" if not specified.").requiredIf(HTTPS_TRUSTSTORE).requiredIf(HTTPS_KEYSTORE_PASSWORD).withRequiredArg().defaultsTo(Resources.getResource("keystore").toString());
        optionParser.accepts(PROXY_ALL, "Will create a proxy mapping for /* to the specified URL").withRequiredArg();
        optionParser.accepts(PRESERVE_HOST_HEADER, "Will transfer the original host header from the client to the proxied service");
        optionParser.accepts(PROXY_VIA, "Specifies a proxy server to use when routing proxy mapped requests").withRequiredArg();
        optionParser.accepts(RECORD_MAPPINGS, "Enable recording of all (non-admin) requests as mapping files");
        optionParser.accepts(MATCH_HEADERS, "Enable request header matching when recording through a proxy").withRequiredArg();
        optionParser.accepts(ROOT_DIR, "Specifies path for storing recordings (parent for " + MAPPINGS_ROOT + " and " + WireMockApp.FILES_ROOT + " folders)").withRequiredArg().defaultsTo(".");
        optionParser.accepts(VERBOSE, "Enable verbose logging to stdout");
        optionParser.accepts(ENABLE_BROWSER_PROXYING, "Allow wiremock to be set as a browser's proxy server");
        optionParser.accepts(DISABLE_REQUEST_JOURNAL, "Disable the request journal (to avoid heap growth when running wiremock for long periods without reset)");
        optionParser.accepts(DISABLE_BANNER, "Disable print banner logo");
        optionParser.accepts(EXTENSIONS, "Matching and/or response transformer extension class names, comma separated.").withRequiredArg();
        optionParser.accepts(MAX_ENTRIES_REQUEST_JOURNAL, "Set maximum number of entries in request journal (if enabled) to discard old entries if the log becomes too large. Default: no discard").withRequiredArg();
        optionParser.accepts(JETTY_ACCEPTOR_THREAD_COUNT, "Number of Jetty acceptor threads").withRequiredArg();
        optionParser.accepts(JETTY_ACCEPT_QUEUE_SIZE, "The size of Jetty's accept queue size").withRequiredArg();
        optionParser.accepts(JETTY_HEADER_BUFFER_SIZE, "The size of Jetty's buffer for request headers").withRequiredArg();
        optionParser.accepts(JETTY_STOP_TIMEOUT, "Timeout in milliseconds for Jetty to stop").withRequiredArg();
        optionParser.accepts(PRINT_ALL_NETWORK_TRAFFIC, "Print all raw incoming and outgoing network traffic to console");
        optionParser.accepts(GLOBAL_RESPONSE_TEMPLATING, "Preprocess all responses with Handlebars templates");
        optionParser.accepts(LOCAL_RESPONSE_TEMPLATING, "Preprocess selected responses with Handlebars templates");
        optionParser.accepts(ADMIN_API_BASIC_AUTH, "Require HTTP Basic authentication for admin API calls with the supplied credentials in username:password format").withRequiredArg();
        optionParser.accepts(ADMIN_API_REQUIRE_HTTPS, "Require HTTPS to be used to access the admin API");
        optionParser.accepts(ASYNCHRONOUS_RESPONSE_ENABLED, "Enable asynchronous response").withRequiredArg().defaultsTo("false");
        optionParser.accepts(ASYNCHRONOUS_RESPONSE_THREADS, "Number of asynchronous response threads").withRequiredArg().defaultsTo("10");

        optionParser.accepts(OPENAPI_FILE, "Specifies path/url to OpenApi3 or Swagger/OpenApi2 definition file").withRequiredArg().defaultsTo("");

        optionParser.accepts(HELP, "Print this message");

        optionSet = optionParser.parse(args);
        validate();
        captureHelpTextIfRequested(optionParser);

        fileSource = new SingleRootFileSource((String) optionSet.valueOf(ROOT_DIR));
        mappingsSource = new JsonFileMappingsSource(fileSource.child(MAPPINGS_ROOT));

        resultingPort = Optional.absent();
    }

    private void validate() {
        if (optionSet.has(HTTPS_KEYSTORE) && !optionSet.has(HTTPS_PORT)) {
            throw new IllegalArgumentException("HTTPS port number must be specified if specifying the keystore path");
        }

        if (optionSet.has(RECORD_MAPPINGS) && optionSet.has(DISABLE_REQUEST_JOURNAL)) {
            throw new IllegalArgumentException("Request journal must be enabled to record stubs");
        }
    }

    private void captureHelpTextIfRequested(OptionParser optionParser) {
        if (optionSet.has(HELP)) {
            StringWriter out = new StringWriter();
            try {
                optionParser.printHelpOn(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            helpText = out.toString();
        }
    }

    @Override
    public String toString() {
        String o=super.toString();
        String fileName="<<None>>";
        String label="OpenApi File";

        if (openAPIFile().length()!=0) fileName=openAPIFile();
        int paddingLength = 29 - label.length();
        o+=label+":"+Strings.repeat(" ", paddingLength)+fileName+"\n";

        return o;
    }

    public String openAPIFile() {
        return (String) optionSet.valueOf(OPENAPI_FILE);
    }

    @Override
    public FileSource filesRoot() {
        return fileSource;
    }

    @Override
    public MappingsLoader mappingsLoader() {
        return mappingsSource;
    }

    @Override
    public MappingsSaver mappingsSaver() {
        return mappingsSource;
    }
}

package kr.budget.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("proxy")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.datasource.url=jdbc:h2:mem:proxy;MODE=Oracle", "spring.datasource.username=sa",
        "spring.datasource.password="})
class ProxyTransportTest {
    @LocalServerPort int port;

    @Test
    void publicDemoDoesNotProvisionDefaultPasswordAccount(@Autowired ApplicationContext context) {
        assertTrue(context.getBeansOfType(InMemoryUserDetailsManager.class).isEmpty());
    }

    @Test
    void emitsSecureLandingHeadersForLoopbackTlsTermination() throws Exception {
        var client = HttpClient.newHttpClient();
        var landing = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"));
        var plain = client.send(landing.build(), HttpResponse.BodyHandlers.discarding());
        assertTrue(plain.headers().firstValue("strict-transport-security").isEmpty());
        var forwarded = client.send(landing.header("X-Forwarded-Proto", "https").build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, forwarded.statusCode());
        assertTrue(forwarded.headers().firstValue("strict-transport-security").orElseThrow().contains("max-age="));
        assertTrue(forwarded.headers().firstValue("cache-control").orElseThrow().contains("no-store"));
        var session = forwarded.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        for (String attribute : new String[]{"HttpOnly", "Secure", "SameSite=Lax"}) assertTrue(session.contains(attribute));
    }
}

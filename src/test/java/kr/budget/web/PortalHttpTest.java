package kr.budget.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.datasource.url=jdbc:h2:mem:portal;MODE=Oracle","spring.datasource.username=sa",
        "spring.datasource.password=","spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/V001__budget.sql,classpath:db/V002__submission_receipt.sql",
        "server.address=127.0.0.1","server.servlet.session.cookie.secure=false"})
class PortalHttpTest {
    @LocalServerPort int port;
    private final ObjectMapper json = new ObjectMapper();

    private final class Visitor {
        final HttpClient browser = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String token;

        Visitor() throws Exception {
            var landing = request("/", null, false);
            assertEquals(200, landing.statusCode());
            var csrf = Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(landing.body());
            assertTrue(csrf.find());
            token = csrf.group(1);
            assertEquals(200, post("/api/demo", "").statusCode());
        }

        HttpResponse<String> request(String path, String form, boolean useToken) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (form != null) builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
            if (useToken) builder.header("X-CSRF-TOKEN", token);
            return browser.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String form) throws Exception {
            return request(path, form, true);
        }

        JsonNode state() throws Exception {
            var response = request("/api/workspace", null, false);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("cache-control").orElse("").contains("no-store"));
            return json.readTree(response.body());
        }

        void role(String name) throws Exception {
            assertEquals(200, post("/api/demo/role", "role=" + name).statusCode());
        }
    }

    @Test
    void twoVisitorsKeepIndependentBalancesAcrossSubmissionApprovalSettlementAndLogout() throws Exception {
        Visitor first = new Visitor();
        Visitor second = new Visitor();
        String form = "title=Conference&category=EDUCATION&estimated=300000&days=1&requestKey=" + UUID.randomUUID();
        assertEquals(403, first.request("/api/claims", form, false).statusCode());
        var created = first.post("/api/claims", form);
        assertEquals(200, created.statusCode());
        String id = json.readTree(created.body()).get("claimId").asText();
        assertEquals(id, json.readTree(first.post("/api/claims", form).body()).get("claimId").asText());
        String path = "/api/claims/" + id;
        assertEquals(403, first.post(path + "/approve", "").statusCode());
        second.role("MANAGER");
        assertEquals(404, second.post(path + "/approve", "").statusCode());
        first.role("MANAGER");
        assertEquals(200, first.post(path + "/approve", "").statusCode());
        assertEquals(200000, first.state().at("/balance/committed").longValue());
        first.role("EMPLOYEE");
        assertEquals(200, first.post(path + "/review", "actual=225000").statusCode());
        first.role("FINANCE");
        assertEquals(200, first.post(path + "/settle", "").statusCode());
        assertEquals(200, first.post(path + "/settle", "").statusCode());
        JsonNode state = first.state();
        assertEquals(0, state.at("/balance/committed").longValue());
        assertEquals(180000, state.at("/balance/spent").longValue());
        assertEquals(820000, state.at("/balance/available").longValue());
        assertEquals(1, state.get("claims").size());
        assertEquals(302, first.post("/api/demo/end", "").statusCode());
        assertEquals(401, first.request("/api/workspace", null, false).statusCode());
        assertEquals(1000000, second.state().at("/balance/available").longValue());
        assertTrue(second.state().get("claims").isEmpty());
    }
}

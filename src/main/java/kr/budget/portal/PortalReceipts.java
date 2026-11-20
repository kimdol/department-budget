package kr.budget.portal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty("budget.portal.url")
public class PortalReceipts {
    public record Receipt(int sequence,String event,long amount) {}
    private final URI endpoint;
    private final String token;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public PortalReceipts(@Value("${budget.portal.url}") URI receiver,@Value("${budget.portal.token}") String token,ObjectMapper json) {
        if (!"https".equalsIgnoreCase(receiver.getScheme()) || token.isBlank()) throw new IllegalArgumentException("HTTPS 포털 설정이 필요합니다.");
        this.endpoint = receiver.resolve("status.php");
        this.token = token;
        this.json = json;
    }

    public Receipt find(UUID workspace,UUID claim) {
        URI url = URI.create(endpoint + "?workspace=" + workspace + "&claim=" + claim);
        var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(6)).header("Authorization","Bearer " + token).GET().build();
        try {
            var response = client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() == 200) return json.readValue(response.body(),Receipt.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
    }
}

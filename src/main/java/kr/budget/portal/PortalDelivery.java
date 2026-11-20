package kr.budget.portal;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty("budget.portal.url")
public class PortalDelivery {
    private final PortalRelay relay;

    public PortalDelivery(PortalOutbox outbox, ObjectMapper json, @Value("${budget.portal.url}") URI url,
            @Value("${budget.portal.token}") String token) {
        if (!"https".equalsIgnoreCase(url.getScheme()) || token.isBlank())
            throw new IllegalArgumentException("HTTPS 포털 주소와 연계 인증 설정이 필요합니다.");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        relay = new PortalRelay(outbox, event -> {
            try {
                var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(6))
                        .header("Authorization","Bearer " + token)
                        .header("Content-Type","application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(event))).build();
                var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() != 204) throw new PortalRelay.DeliveryFailure("포털 HTTP " + response.statusCode());
            } catch (IOException e) {
                throw new PortalRelay.DeliveryFailure("포털 연결 오류");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PortalRelay.DeliveryFailure("전송 중단");
            }
        });
    }

    @Scheduled(fixedDelayString="${budget.portal.delay-ms:15000}")
    void deliver() {
        relay.deliverPending();
    }

    public boolean retry(PortalOutbox.Event event) {
        return relay.deliver(event);
    }
}

package kr.budget.portal;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortalRelay {
    private static final Logger log = LoggerFactory.getLogger(PortalRelay.class);

    static final class DeliveryFailure extends RuntimeException {
        DeliveryFailure(String reason) { super(reason); }
    }
    private final PortalOutbox outbox;
    private final Consumer<PortalOutbox.Event> receiver;

    public PortalRelay(PortalOutbox outbox, Consumer<PortalOutbox.Event> receiver) {
        this.outbox = outbox;
        this.receiver = receiver;
    }

    public void deliverPending() {
        for (var event : outbox.pending()) deliver(event);
    }

    public boolean deliver(PortalOutbox.Event event) {
        try {
            receiver.accept(event);
        } catch (RuntimeException failure) {
            String reason = failure instanceof DeliveryFailure ? failure.getMessage() : failure.getClass().getSimpleName();
            if (outbox.failed(event.eventId(), reason) == 1) {
                log.warn("Portal delivery failed eventId={} claimId={} sequence={}", event.eventId(), event.claimId(), event.sequenceNo());
            }
            return false;
        }
        if (outbox.acknowledged(event.eventId()) == 1) {
            log.info("Portal delivery acknowledged eventId={} claimId={} sequence={}", event.eventId(), event.claimId(), event.sequenceNo());
        }
        return true;
    }
}

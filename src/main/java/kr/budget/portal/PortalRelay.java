package kr.budget.portal;

import java.util.function.Consumer;

public class PortalRelay {
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
            outbox.failed(event.eventId(), failure instanceof DeliveryFailure ? failure.getMessage() : failure.getClass().getSimpleName());
            return false;
        }
        outbox.acknowledged(event.eventId());
        return true;
    }
}

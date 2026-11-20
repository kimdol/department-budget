package kr.budget.portal;

import java.util.function.Consumer;

public class PortalRelay {
    private final PortalOutbox outbox;
    private final Consumer<PortalOutbox.Event> receiver;

    public PortalRelay(PortalOutbox outbox, Consumer<PortalOutbox.Event> receiver) {
        this.outbox = outbox;
        this.receiver = receiver;
    }

    public void deliverPending() {
        for (var event : outbox.pending()) {
            try {
                receiver.accept(event);
            } catch (RuntimeException failure) {
                outbox.failed(event.eventId(), failure.getClass().getSimpleName());
                continue;
            }
            outbox.acknowledged(event.eventId());
        }
    }
}

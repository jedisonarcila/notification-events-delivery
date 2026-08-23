package com.cobre.notifications.delivery.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationIdFactoryTest {

    @Test
    void sameEventAndSubscriptionProduceSameId() {
        String first = NotificationIdFactory.create("evt-1", "sub-1");
        String second = NotificationIdFactory.create("evt-1", "sub-1");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentSubscriptionsProduceDifferentIds() {
        String forSub1 = NotificationIdFactory.create("evt-1", "sub-1");
        String forSub2 = NotificationIdFactory.create("evt-1", "sub-2");

        assertThat(forSub1).isNotEqualTo(forSub2);
    }

    @Test
    void differentEventsProduceDifferentIds() {
        String forEvt1 = NotificationIdFactory.create("evt-1", "sub-1");
        String forEvt2 = NotificationIdFactory.create("evt-2", "sub-1");

        assertThat(forEvt1).isNotEqualTo(forEvt2);
    }

    @Test
    void rejectsNullEventId() {
        assertThatThrownBy(() -> NotificationIdFactory.create(null, "sub-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSubscriptionId() {
        assertThatThrownBy(() -> NotificationIdFactory.create("evt-1", null))
                .isInstanceOf(NullPointerException.class);
    }
}

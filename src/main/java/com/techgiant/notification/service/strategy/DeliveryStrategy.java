package com.techgiant.notification.service.strategy;

import com.techgiant.notification.model.Notification;

public interface DeliveryStrategy {

    String getChannelType();

    void deliver(Notification notification);
}

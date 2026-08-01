package com.vdmytriv.carsharing.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

record TelegramMessageRequest(
        @JsonProperty("chat_id") String chatId,
        String text
) {
}

package com.scansettle.api.openbanking.model;

import java.util.Map;

/**
 * A raw inbound webhook, prior to signature verification. {@code headers} carries
 * the provider's signature header(s) so each adapter can verify against its own
 * scheme; the payload is passed through unparsed so the adapter — not the generic
 * webhook ingress endpoint — owns interpreting it.
 */
public record WebhookRequest(Map<String, String> headers, String rawBody) {
}

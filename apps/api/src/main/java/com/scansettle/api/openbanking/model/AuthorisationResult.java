package com.scansettle.api.openbanking.model;

/** Where to send the customer's browser to authenticate with their bank. */
public record AuthorisationResult(String providerReference, String redirectUrl) {
}

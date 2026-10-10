package com.hanbay.hotel.authentication.service;

import java.util.concurrent.CompletableFuture;

public interface EmailService {

    CompletableFuture<Void> sendPasswordResetEmail(String email, String fullName, String rawToken);
}

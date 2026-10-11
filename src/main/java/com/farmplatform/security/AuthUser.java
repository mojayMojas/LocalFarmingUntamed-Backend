package com.farmplatform.security;

/** The authenticated principal placed in the Spring Security context by {@link JwtAuthFilter}. */
public record AuthUser(Long userId, String email, String role) {}

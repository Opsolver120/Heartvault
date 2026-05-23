package com.heartvault.api;

public final class HeartVaultProvider {
    private static volatile HeartVaultApi api;

    private HeartVaultProvider() {
    }

    public static HeartVaultApi get() {
        HeartVaultApi a = api;
        if (a == null) {
            throw new IllegalStateException("HeartVault API not available");
        }
        return a;
    }

    public static void set(HeartVaultApi api) {
        HeartVaultProvider.api = api;
    }
}


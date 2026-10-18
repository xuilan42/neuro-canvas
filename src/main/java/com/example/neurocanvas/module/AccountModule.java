package com.example.neurocanvas.module;

import com.example.neurocanvas.client.AccountClient;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;

/** The Neuro Canvas account in the menu: sign in or register in the browser, open the account page, sign out. */
public final class AccountModule extends Module {
    private final Setting signIn = add(Setting.button("Sign in / register"));
    private final Setting open = add(Setting.button("Open my account page"));
    private final Setting signOut = add(Setting.button("Sign out"));

    public AccountModule() {
        super("Account", Category.MISC, "Your Neuro Canvas account: friends, online features and sharing in the Market", true);
        aiControllable = false;
        alwaysOn = true;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (signIn.on()) {
            signIn.set(0);
            if (AccountClient.state() == AccountClient.State.LINKING) {
                AccountClient.cancelLink();
            } else if (!AccountClient.signedIn()) {
                AccountClient.startLink();
            }
        }
        if (open.on()) {
            open.set(0);
            try {
                net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(AccountClient.siteUrl()));
            } catch (IOException | RuntimeException ignored) {
                // This build has no server address.
            }
        }
        if (signOut.on()) {
            signOut.set(0);
            AccountClient.signOut();
        }
    }
}

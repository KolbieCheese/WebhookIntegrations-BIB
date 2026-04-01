package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.LightweightClansApi;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class LightweightClansServiceResolver {
    private final Server server;

    public LightweightClansServiceResolver(JavaPlugin plugin) {
        this(plugin.getServer());
    }

    LightweightClansServiceResolver(Server server) {
        this.server = server;
    }

    public Optional<LightweightClansApi> resolve() {
        RegisteredServiceProvider<LightweightClansApi> registration = server.getServicesManager()
                .getRegistration(LightweightClansApi.class);

        if (registration == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(registration.getProvider());
    }
}

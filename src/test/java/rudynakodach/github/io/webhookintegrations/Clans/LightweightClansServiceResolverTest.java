package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.LightweightClansApi;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LightweightClansServiceResolverTest {

    @Test
    void resolveReturnsProviderFromServicesManager() {
        Server server = mock(Server.class);
        ServicesManager servicesManager = mock(ServicesManager.class);
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<LightweightClansApi> registration = mock(RegisteredServiceProvider.class);
        LightweightClansApi api = mock(LightweightClansApi.class);

        when(server.getServicesManager()).thenReturn(servicesManager);
        when(servicesManager.getRegistration(LightweightClansApi.class)).thenReturn(registration);
        when(registration.getProvider()).thenReturn(api);

        Optional<LightweightClansApi> resolved = new LightweightClansServiceResolver(server).resolve();

        assertTrue(resolved.isPresent());
        assertEquals(api, resolved.get());
    }

    @Test
    void resolveReturnsEmptyWhenServiceIsMissing() {
        Server server = mock(Server.class);
        ServicesManager servicesManager = mock(ServicesManager.class);

        when(server.getServicesManager()).thenReturn(servicesManager);
        when(servicesManager.getRegistration(LightweightClansApi.class)).thenReturn(null);

        assertTrue(new LightweightClansServiceResolver(server).resolve().isEmpty());
    }
}

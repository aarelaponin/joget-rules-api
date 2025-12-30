package global.govstack.rulesapi;

import global.govstack.rulesapi.lib.RulesApiProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * OSGi Bundle Activator for Joget Rules API Plugin.
 *
 * Registers the Rules API provider for field dictionary, validation,
 * compilation, and ruleset management.
 */
public class Activator implements BundleActivator {

    protected List<ServiceRegistration> registrations = new ArrayList<>();

    @Override
    public void start(BundleContext context) throws Exception {
        // Register Rules API Provider
        registrations.add(context.registerService(
            RulesApiProvider.class.getName(),
            new RulesApiProvider(),
            null
        ));
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        for (ServiceRegistration registration : registrations) {
            registration.unregister();
        }
    }
}

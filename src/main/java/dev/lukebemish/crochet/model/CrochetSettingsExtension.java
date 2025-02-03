package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.initialization.Settings;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;

import javax.inject.Inject;

public abstract class CrochetSettingsExtension implements ExtensionAware {
    private final ExtensiblePolymorphicDomainObjectContainer<SettingsMinecraftInstallation<?, ?>> installations;

    @Inject
    protected abstract ObjectFactory getObjects();

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject
    public CrochetSettingsExtension(Settings settings) {
        this.installations = getObjects().polymorphicDomainObjectContainer(
            (Class<SettingsMinecraftInstallation<?, ?>>) (Class) SettingsMinecraftInstallation.class
        );
        this.installations.registerFactory(
            SettingsMinecraftInstallation.Vanilla.class,
            name -> getObjects().newInstance(SettingsMinecraftInstallation.Vanilla.class, name, name, settings)
        );
        this.installations.registerFactory(
            SettingsMinecraftInstallation.Fabric.class,
            name -> getObjects().newInstance(SettingsMinecraftInstallation.Fabric.class, name, name, settings)
        );
        this.installations.registerFactory(
            SettingsMinecraftInstallation.NeoForm.class,
            name -> getObjects().newInstance(SettingsMinecraftInstallation.NeoForm.class, name, name, settings)
        );

        this.getExtensions().add("installations", this.installations);
    }

    public ExtensiblePolymorphicDomainObjectContainer<SettingsMinecraftInstallation<?, ?>> getInstallations() {
        return installations;
    }

    public SettingsMinecraftInstallation.Fabric fabricInstallation(String name, Action<? super SettingsMinecraftInstallation.Fabric> action) {
        return installations.create(name, SettingsMinecraftInstallation.Fabric.class, action);
    }

    public SettingsMinecraftInstallation.Vanilla vanillaInstallation(String name, Action<? super SettingsMinecraftInstallation.Vanilla> action) {
        return installations.create(name, SettingsMinecraftInstallation.Vanilla.class, action);
    }

    public SettingsMinecraftInstallation.NeoForm neoFormInstallation(String name, Action<? super SettingsMinecraftInstallation.NeoForm> action) {
        return installations.create(name, SettingsMinecraftInstallation.NeoForm.class, action);
    }
}

package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.model.CrochetExtension;

import javax.inject.Inject;

abstract public class ExtensionHolder {
    final CrochetExtension extension;

    @Inject
    public ExtensionHolder(CrochetExtension extension) {
        this.extension = extension;
    }
}

// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model;

public class Mandant {
    public final String ident;
    public final String label;
    public final String salt;
    public final int priority;

    public Mandant(final String aIdent, final String aLabel, final String aSalt, final int aPriority) {
        ident = aIdent;
        label = aLabel;
        salt = aSalt;
        priority = aPriority;
    }
}

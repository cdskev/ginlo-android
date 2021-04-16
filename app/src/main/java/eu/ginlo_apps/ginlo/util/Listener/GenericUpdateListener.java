// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util.Listener;

public interface GenericUpdateListener<T> {
    void onSuccess(final T object);

    void onUpdate(final String updateMessage);

    void onFail(final String message, final String errorIdent);
}

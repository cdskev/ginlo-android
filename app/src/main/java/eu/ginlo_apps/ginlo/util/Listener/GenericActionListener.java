// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util.Listener;

/**
 * generischer Listrener fuer Serverkommunikation
 *
 * @param <T>
 */
public interface GenericActionListener<T> {
    void onSuccess(final T object);

    void onFail(final String message, final String errorIdent);
}

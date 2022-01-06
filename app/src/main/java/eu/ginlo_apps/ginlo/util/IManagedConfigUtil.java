// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import eu.ginlo_apps.ginlo.exception.LocalizedException;

/**
 * Created by yves1 on 19.09.16.
 */
public interface IManagedConfigUtil {
    /**
     * Wurde der Export von Chats erlaubt
     *
     * @return
     */
    boolean canExportChat();

    /**
     * Wurde das Öffnen in von Dateien erlaubt (verlassen der Sandbox)
     *
     * @return
     */
    boolean isOpenInAllowed();

    /**
     * Wenn False, dann dürfen nur noch texte versandt werden
     *
     * @return
     */
    boolean canSendMedia();

    /**
     * Wurde die Einstellung für Speichern in die Gallerie festgelegt ?!
     *
     * @return
     */
    boolean isSaveMediaToGalleryManaged();

    /**
     * Wie wurde die Einstellung für Speichern in die Gallerie festgelegt
     *
     * @return
     */
    boolean isSaveMediaToGalleryManagedValue();

    boolean isSaveMediaToCameraRollDisabled();

    /**
     * Wurde festgelegt, nach wieviel Minuten im Hintergrund die App gesperrt werden soll
     *
     * @return
     */
    boolean isLockApplicationDelayManaged();

    /**
     * Nach wieviel Minuten im Hintergrund soll die App gesperrt werden
     *
     * @return
     */
    int getLockApplicationDelayManagedValue();

    /**
     * Wird zum Start der Anwendung ein Passwort abgefragt
     *
     * @return
     */
    boolean isPasswordOnStartRequired();

    /**
     * Wurde festgelegt, ob die Daten nach Passwortfehleinfgaben gelöscht werden sollen
     *
     * @return
     */
    boolean isDeleteDataAfterTriesManaged();

    /**
     * nach wieviel fehlerhaften Passworteingaben wird der Account zurückgesetzt ?
     *
     * @return
     */
    int getDeleteDataAfterTriesManagedValue();

    /**
     * Prüft das Passwort auf die Passwortanforderungen und gibt ggf. einen Fehlertext zurück
     *
     * @param password
     * @return
     */
    String checkPassword(String password, boolean isPwChange);

    /**
     * Hasht die Passwörter um zu prüfen, ob das Passwort bereits verwendet wurde.
     *
     * @param password
     * @return
     */
    void onPasswordChanged(String password) throws LocalizedException;

    /**
     * muss der Nutzer das Passwort ändern
     *
     * @param password
     * @return
     */
    boolean needToChangePassword(String password, boolean isPwChange) throws LocalizedException;

    /**
     * Kann der Nutzer ein einfaches Passwort nutzen
     *
     * @return
     */
    boolean canUseSimplePassword();

    /**
     * Wurde der Zugriff auf die Kamera gesperrt
     *
     * @return
     */
    boolean isCameraDisabled();

    /**
     * Wurde der Zugriff auf die Kamera gesperrt
     *
     * @return
     */
    boolean isMicrophoneDisabled();

    /**
     * Wurde das Senden von Kontakten gesperrt
     *
     * @return
     */
    boolean isSendContactsDisabled();

    /**
     * Wurde das Senden von Standortdaten gesperrt
     *
     * @return
     */
    boolean isLocationDisabled();

    /**
     * Wurde das Kopieren von Textnachrichten in die Zwischenablage gesperrt
     *
     * @return
     */
    boolean isCopyPasteDisabled();

    /**
     * Wurde der RecoveryCode vom Admin grunsaetzlich gesperrt
     *
     * @return
     */
    boolean isRecoveryDisabled();

    /**
     * Gibt zurueck, ob der Admin den Recovery-Code verschickt (Company-Recovery) oder der Nutzer selbst (Per Mail, SMS)
     *
     * @return
     */
    boolean getRecoveryByAdmin();

    /**
     * Gibt zurueck, ob das Preview der Notifications vom MC gamaged wird, oder nicht
     *
     * @return
     */
    boolean isNotificationPreviewDisabled();

    /**
     * Gibt zurueck, ob keys für das automatische Anmelden per MDM vorhanden sind
     *
     * @return
     */
    boolean hasAutomaticMdmRegistrationKeys();

    /**
     * Gibt vorausgfüllten Vornamen zurück
     *
     * @return
     */
    String getFirstName();

    /**
     * Gibt vorausgfüllten Nachnamen zurück
     *
     * @return
     */
    String getLastName();

    /**
     * Gibt vorausgfüllte E-Mail-Adresse zurück
     *
     * @return
     */
    String getEmailAddress();

    /**
     * Gibt LoginCode zurück
     *
     * @return
     */
    String getLoginCode();

    /**
     * Eindeutige ID fuer die Konfiguration
     *
     * @return
     */
    String getVersionKey();

    /**
     * Fingerabdruck, Gesichtserkennung und co deaktiviert?
     *
     * @return
     */
    boolean getDisableBiometricLogin();
}

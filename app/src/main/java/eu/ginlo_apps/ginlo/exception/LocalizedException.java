// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.exception;

public class LocalizedException
        extends Exception {

    public static final String ACCOUNT_UNKNOWN = "ERR-0007";

    public static final String JSON_OBJECT_INVALID = "ERR-0011";

    public static final String FILE_NOT_FOUND = "ERR-0014";

    public static final String STREAMING_ERROR = "ERR-0023";

    public static final String UNSUPPORTED_ENCODING_EXCEPTION = "ERR-0087";

    public static final String ROOM_UNKNOWN = "ERR-0101";

    public static final String ACCOUNT_DURATION_EXPIRED = "ERR-0119";

    public static final String BLACKLISTED_EMAIL_DOMAIN = "ERR-0124";

    public static final String TOO_MANY_TRIES_FOR_REQUEST_COUPLING = "ERR-0162";

    public static final String KEY_NOT_AVAILABLE = "AND-0001";

    public static final String NO_INIT_CALLED = "AND-0002";

    public static final String DECRYPT_DATA_FAILED = "AND-0003";

    public static final String ENCRYPT_DATA_FAILED = "AND-0004";

    public static final String GENERATE_CHIPHER_FAILED = "AND-0005";

    public static final String GENERATE_CHECKSUM_FAILED = "AND-0006";

    public static final String GENERATE_IV_FAILED = "AND-0007";

    public static final String GENERATE_AES_KEY_FAILED = "AND-0008";

    public static final String GENERATE_CERTIFICATE_FAILED = "AND-0009";

    public static final String GENERATE_KEY_PAIR_FAILED = "AND-0010";

    public static final String SIGN_DATA_FAILED = "AND-0013";

    public static final String VERIFY_DATA_FAILED = "AND-0014";

    public static final String SAVE_KEYS_FAILED = "AND-0015";

    public static final String CHARSET_NOT_SUPPORTED = "AND-0016";

    public static final String CLIPBOARD_PUT_FAILED = "AND-0017";

    public static final String CLIPBOARD_GET_FAILED = "AND-0018";

    public static final String GET_AES_KEY_DATA_FAILED = "AND-0020";

    public static final String CHECK_SIGNATURE_FAILED = "AND-0021";

    public static final String SAVE_IMAGE_FAILED = "AND-0022";

    public static final String LOAD_BACKGROUND_FAILED = "AND-0023";

    public static final String SAVE_BACKGROUND_FAILED = "AND-0024";

    public static final String LOAD_IMAGE_FAILED = "AND-0025";

    public static final String LOAD_AUDIO_PLAYER_FAILED = "AND-0026";

    public static final String START_AUDIO_RECORDER_FAILED = "AND-0027";

    public static final String SSL_HANDSHAKE_FAILED = "AND-0028";

    public static final String PARSE_JSON_FAILED = "AND-0031";

    public static final String PARSE_XML_FAILED = "AND-0032";

    public static final String LOAD_FILE_FAILED = "AND-0033";

    public static final String CHAT_NOT_FOUND = "AND-0034";

    public static final String NO_DATA_FOUND = "AND-0035";

    public static final String NO_FULL_ACCOUNT = "AND-0036";

    public static final String NO_ACTION_SEND = "AND-0038";

    public static final String CHECK_ACTION_SEND_INTENT_FAILED = "AND-0039";

    public static final String FILE_TO_BIG_AFTER_COMPRESSION = "AND-0040";

    public static final String BACKUP_WRITE_FILE_FAILED = "AND-0041";

    public static final String BACKUP_JSON_OBJECT_NULL = "AND-0043";

    public static final String BACKUP_CREATE_BACKUP_FAILED = "AND-0044";

    public static final String BACKUP_DELETE_FILE_FAILED = "AND-0045";

    public static final String FILE_ZIPPING_FAILED = "AND-0046";

    public static final String FILE_UNZIPPING_FAILED = "AND-0047";

    public static final String BACKUP_FOLDER_FAILED = "AND-0048";

    public static final String BACKUP_RESTORE_BACKUP_FAILED = "AND-0049";

    public static final String BACKUP_RESTORE_SALTS_NOT_EQUAL = "AND-0050";

    public static final String BACKUP_RESTORE_SERVER_CONNECTION_FAILED = "AND-0052";

    public static final String BACKUP_RESTORE_WRONG_PW = "AND-0053";

    public static final String BACKUP_RESTORE_ACCOUNT_SERVER_CONNECTION_FAILED = "AND-0054";

    public static final String NO_ACCOUNT_ON_SERVER = "AND-0055";

    public static final String CREATE_FILE_FAILED = "AND-0056";

    public static final String PUBLIC_KEY_IS_NULL = "AND-0057";

    public static final String MESSAGE_MODEL_IS_NULL = "AND-0058";

    public static final String PREFERENCE_IS_NULL = "AND-0059";

    public static final String UNDEFINED_ARGUMENT = "AND-0063";

    public static final String DEVICE_MODEL_UNKNOWN = "AND-0064";

    public static final String GENERATED_TRANS_ID_FAILED = "AND-0065";

    public static final String COUPLE_DEViCE_FAILED = "AND-0066";

    public static final String COUPLE_DEVICE_TIME_OUT = "AND-0067";

    public static final String BACKEND_REQUEST_FAILED = "AND-0068";

    public static final String DERIVE_KEY_FAILED = "AND-0069";

    public static final String NOT_OWN_CONTACT = "AND-0070";

    public static final String NO_IV_DATA = "AND-0071";

    public static final String BASE64_FAILED = "AND-0072";

    public static final String MIGRATION_FAILED = "AND-0073";

    public static final String NO_CONTEXT_AVAILABLE = "AND-0074";

    public static final String UNKNOWN_ERROR = "AND-0075";

    public static final String JSON_OBJECT_NULL = "AND-0076";

    public static final String NO_FCM_TOKEN = "AND-0077";

    public static final String OBJECT_NULL = "AND-0078";

    public static final String ANDROID_KEY_STORE_ACTION_FAILED = "AND-0079";

    public static final String SAVE_DATA_FAILED = "AND-0081";

    public static final String NO_UNIQUE_RESULT = "AND-0082";

    public static final String DB_IS_NOT_READY = "AND-0083";

    public static final String DB_ALREADY_EXISTS = "AND-0084";

    public static final String DB_SQL_STATEMENT_FAILED = "AND-0085";

    public static final String ANDROID_BIOMETRIC_KEY_INVALIDATED = "AND-0086";

    private final String mIdentifier;

    public LocalizedException(final String identifier,
                              final Throwable throwable) {
        super(throwable);
        mIdentifier = identifier;
    }

    public LocalizedException(final String identifier,
                              final String string,
                              final Throwable throwable) {
        super(string, throwable);
        mIdentifier = identifier;
    }

    public LocalizedException(final String identifier,
                              final String string) {
        super(string);
        mIdentifier = identifier;
    }

    public LocalizedException(final String identifier) {
        super();
        mIdentifier = identifier;
    }

    public String getIdentifier() {
        return mIdentifier;
    }
}

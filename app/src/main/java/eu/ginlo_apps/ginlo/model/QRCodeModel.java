package eu.ginlo_apps.ginlo.model;

import android.graphics.Bitmap;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import eu.ginlo_apps.ginlo.util.ChecksumUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.ImageUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

/**
 * Hold all currently used QR code versions
 *
 * Any QR-code:
 * Generic: Take the payload and build the code.
 *
 * User/Contact QR-codes:
 * V1: The deprecated SIMSme QR code (not implemented anymore!)
 * V2: The regular ginlo QR code until Sep 2021 (app release 4.2)
 * V3: The ginlo now QR code from Sep 2021 (app release 4.3)
 *
 * Device coupling QR-codes:
 * TAN: For parsing only. Use type Generic for encoding purposes
 *      (Format: ginloID|TAN)
 * ---------------------------------------------------------------
 *
 * Generic - Format:
 * String "..."
 *
 * Request a String object with the payload data.
 *
 * V2 - Format:
 * V2\rGINLOID\raccount.publicKey.sha256sum().base64()
 *
 * Request a user's ginloID and public key as parameters.
 *
 * V3 - Format:
 * https://join.ginlo.net/invite?p=...&q=...
 *
 * Request the URL arguments described below:
 *
 * There are two main parameters that are passed using the QR-Code:
 * p - contains all the information; the format is a base64-string. The base64-String converts to utf8-formatted string.
 *     Once you decode the base64, you should get something like:
 *     p=1&c=0&i=AF9ZC8QT&s=z7l1dM6WcM//fTL8zSO3d/Wf9fnUzOzpXzhhdnEfhOM=
 * q - signature of the p-parameter's content after it has been converted back to utf8 from base64.
 *
 *
 * The "main" p and q will be calculated out of the parameters given as follows:
 *
 * p - (required, isP2PInvitation)
 * Indicates whether this is a Person-to-Person-Invitation.
 * Set to 1 for Yes, this is a person-to-person.
 * Set to 0 for No, this is a business-to-person invitation
 *
 * c - (required, mustCreateChat)
 * Indicates whether a new chat should be created with the user-id in question.
 * Set to 1 for Yes, create a new chat
 * Set to 0 for No, don't do that
 *
 * i (ginloID)
 * If p==1: required; The ginloID of the user in question
 * If p==0: optional; The ginloID of the user with whom a new chat should be started (if c==1)
 *
 * s (will be calculated out of the user's publicKey)
 * If p==1: required; The SHA256-signature of the public key of i as base64-encoded string
 * If p==0 && c==1: required; The SHA256-signtature of the public-key of i as base64-encoded string
 *
 * b (invitationID)
 * If p==1: ignored
 * If p==0: The invitation-ID that is stored on the server that contains additional data
 *
 */
public class QRCodeModel {
    private static final String TAG = QRCodeModel.class.getSimpleName();

    // - deprecated -
    //public static final String TYPE_V1 = "V1";
    //public static final String TYPE_V1_PREFIX = "";
    public static final String TYPE_V2 = "V2";
    public static final String TYPE_V2_PREFIX = "V2" + '\r';
    public static final String TYPE_V3 = "V3";
    public static final String TYPE_V3_PREFIX = "https://join.ginlo.net/invite?p=";
    public static final String TYPE_COUPLING_TAN = "TAN";
    public static final String TYPE_GENERIC = "Generic";
    public static final String TYPE_UNKNOWN = "Unknown";

    public static final int FOR_ENCODING = 0;
    public static final int FOR_DECODING = 1;

    private final static String V3_SALT = "GiesingerBraeu";

    final private int mMode;
    private String mGinloID = "";
    private String mPublicKeySHA256Base64 = "";
    private String mVersion = TYPE_UNKNOWN;
    private String mPayload = "";
    private String mInvitationID = "";
    private String mTAN = "";
    private boolean mIsP2PInvitation = false;
    private boolean mMustCreateChat = false;

    /**
     * Constructor for undefined generic QR code creation
     * @param genericData
     */
    public QRCodeModel(String genericData) {
        this(genericData, FOR_ENCODING);
    }

    /**
     * Constructor for undefined generic QR code creation
     * or for decoding and analyzing of a given payload.
     * @param genericData
     * @param mode
     */
    public QRCodeModel(String genericData, int mode) {
        mMode = mode;
        mPayload = (genericData != null ? genericData : "");
        switch (mMode) {
            case FOR_ENCODING:
                mVersion = TYPE_GENERIC;
                mPayload = buildPayload();
                break;
            case FOR_DECODING:
            default:
                mVersion = TYPE_UNKNOWN;
        }
    }

    /**
     * Constructor for "V2" QR codes, based on given ginloID and public key
     * @param ginloID
     * @param publicKeySHA256Base64
     */
    public QRCodeModel(String ginloID,
                       String publicKeySHA256Base64) {
        mMode = FOR_ENCODING;
        mVersion = TYPE_V2;
        mGinloID = (ginloID != null ? ginloID : "");
        mPublicKeySHA256Base64 = (publicKeySHA256Base64 != null ? publicKeySHA256Base64 : "");
        mPayload = buildPayload();
    }

    /**
     * Constructor for "V3" QR codes, used for ginlo now with several attributes
     * @param isP2PInvitation
     * @param mustCreateChat
     * @param ginloID
     * @param publicKeySHA256Base64
     * @param invitationID
     */
    public QRCodeModel(boolean isP2PInvitation,
                       boolean mustCreateChat,
                       String ginloID,
                       String publicKeySHA256Base64,
                       String invitationID) {
        mMode = FOR_ENCODING;
        mVersion = TYPE_V3;
        mIsP2PInvitation = isP2PInvitation;
        mMustCreateChat = mustCreateChat;
        mGinloID = (ginloID != null ? ginloID : "");
        mPublicKeySHA256Base64 = (publicKeySHA256Base64 != null ? publicKeySHA256Base64 : "");
        mInvitationID = (invitationID != null ? invitationID : "");
        mPayload = buildPayload();
    }

    public String getVersion() {
        return mVersion;
    }

    public String getGinloID() {
        return mGinloID;
    }

    public String getPublicKeySHA256Base64() {
        return mPublicKeySHA256Base64;
    }

    public String getTAN() {
        return mTAN;
    }

    public String getPayload() {
        return mPayload;
    }

    /**
     * Parse a generic data string to construct a new QRCodeModel instance
     * with correct filled-in member information.
     * Supports
     * @param genericData
     * @return new QRCodeModel with mType set accordingly.
     */
    public static QRCodeModel parseQRString(String genericData) {
        QRCodeModel qrm = new QRCodeModel(genericData, FOR_DECODING);
        boolean malformedData = false;
        String parseError = "";

        if(genericData.startsWith(TYPE_V2_PREFIX)) {
            // We may (!) have a TYPE_V2 QR code
            qrm.mVersion = TYPE_V2;
            int valStart = TYPE_V2_PREFIX.length();
            String value = genericData.substring(valStart);
            if(!StringUtil.isNullOrEmpty(value)) {
                int valEnd = value.indexOf('\r');
                qrm.mGinloID = value.substring(0, valEnd);
                LogUtil.d(TAG, "parseQRString: V2 mGinloID = " + qrm.mGinloID);
                valStart = valEnd + 1;
                value = value.substring(valStart);
                if(!StringUtil.isNullOrEmpty(value)) {
                    qrm.mPublicKeySHA256Base64 = value;
                    LogUtil.d(TAG, "parseQRString: V2 mPublicKeySHA256Base64 = " + qrm.mPublicKeySHA256Base64);
                } else {
                    parseError = "QR code seems to be V2, but found no key info";
                    malformedData = true;
                }
            } else {
                parseError = "QR code seems to be V2, but found no ginloID";
                malformedData = true;
            }
        } else if (genericData.startsWith(TYPE_V3_PREFIX)) {
            // We may (!) have a TYPE_V3 QR code
            // First step: Unpack p
            qrm.mVersion = TYPE_V3;
            if(genericData.indexOf("&q=") < TYPE_V3_PREFIX.length()) {
                // Wrong checksum in data!
                parseError = "QR code seems to be of TYPE_V3 but cannot parse q parameter";
                malformedData = true;
            } else {
                final String p = new String(Base64.decode(genericData.substring(TYPE_V3_PREFIX.length(), genericData.indexOf("&q=")), Base64.DEFAULT));
                final String q = genericData.substring(genericData.indexOf("&q=") + 3);

                // Verify integrity of the given data
                String pSha1 = ChecksumUtil.getSHA1ChecksumForString(p + V3_SALT);
                if (!pSha1.equals(q)) {
                    // Wrong checksum in data!
                    parseError = "QR code seems to be of TYPE_V3 but wrong checksum";
                    malformedData = true;
                } else {
                    // Second step: Parse and save URL arguments
                    final int pLength = p.length();
                    int valStart, valLen;
                    // Parameter p
                    valStart = p.indexOf("p=") + 2;
                    valLen = 1;
                    if (valStart != 1 && (valStart + valLen) < pLength) {
                        qrm.mIsP2PInvitation = p.substring(valStart, valStart + valLen).equals("1");
                        LogUtil.d(TAG, "parseQRString: V3 mIsP2PInvitation = " + qrm.mIsP2PInvitation);
                    } else {
                        malformedData = true;
                        parseError = "Missing mandatory parameter p";
                    }
                    // Parameter c
                    valStart = p.indexOf("&c=") + 3;
                    if (valStart != 2 && (valStart + valLen) < pLength) {
                        qrm.mMustCreateChat = p.substring(valStart, valStart + valLen).equals("1");
                        LogUtil.d(TAG, "parseQRString: V3 mMustCreateChat = " + qrm.mMustCreateChat);
                    } else {
                        malformedData = true;
                        parseError = "Missing mandatory parameter c";
                    }
                    // Parameter i
                    valStart = p.indexOf("&i=") + 3;
                    valLen = 8;
                    if (valStart != 2 && (valStart + valLen) < pLength) {
                        qrm.mGinloID = p.substring(valStart, valStart + valLen);
                        LogUtil.d(TAG, "parseQRString: V3 mGinloID = " + qrm.mGinloID);
                    } else {
                        malformedData = true;
                        parseError = "Missing mandatory parameter i";
                    }
                    // Parameter s
                    valStart = p.indexOf("&s=") + 3;
                    if (valStart != 2) {
                        final int valEnd = p.substring(valStart).indexOf('&');
                        if (valEnd == -1) {
                            // s ist the last parameter
                            qrm.mPublicKeySHA256Base64 = p.substring(valStart);
                        } else {
                            qrm.mPublicKeySHA256Base64 = p.substring(valStart, valEnd);
                        }
                        LogUtil.d(TAG, "parseQRString: V3 mPublicKeySHA256Base64 = " + qrm.mPublicKeySHA256Base64);
                    } else {
                        malformedData = true;
                        parseError = "Missing mandatory parameter s";
                    }
                    // Parameter b (optional)
                    valStart = p.indexOf("&b=") + 3;
                    if (valStart != 2) {
                        final int valEnd = p.substring(valStart).indexOf('&');
                        if (valEnd == -1) {
                            // b ist the last parameter
                            qrm.mInvitationID = p.substring(valStart);
                        } else {
                            // This is undefined! There should be no more parameters!
                            qrm.mInvitationID = p.substring(valStart, valEnd);
                            parseError = "QR code seems to be V3, but found unexpected data";
                            malformedData = true;
                        }
                    }
                }
            }
        } else if (genericData.length() == 18 && genericData.indexOf('|') == 8){
            // This looks like a coupling TAN QR code
            qrm.mVersion = TYPE_COUPLING_TAN;
            qrm.mGinloID = genericData.substring(0, 8);
            LogUtil.d(TAG, "parseQRString: Coupling TAN mGinloID = " + qrm.mGinloID);
            qrm.mTAN = genericData.substring(9);
            LogUtil.d(TAG, "parseQRString: Coupling TAN mTAN = " + qrm.mTAN);
        } else {
            // Cannot identify QR code type
            qrm.mVersion = TYPE_UNKNOWN;
            LogUtil.i(TAG, "parseQRString: Could not identify QR code type for " + genericData);
        }

        if(malformedData) {
            qrm.mVersion = TYPE_UNKNOWN;
            LogUtil.e(TAG, "parseQRString: " + parseError + " in " + genericData);
        }
        return qrm;
    }

    /**
     * Assemble the correct payload string for the requested QR code version
     * @return
     */
    private String buildPayload() {
        final StringBuilder payload = new StringBuilder();
        switch(mVersion) {
            case TYPE_GENERIC:
                payload.append(mPayload);
                break;
            case TYPE_V2:
                payload.append(TYPE_V2_PREFIX)
                        .append(mGinloID).append('\r')
                        .append(mPublicKeySHA256Base64);
                break;
            case TYPE_V3:
                // First step: Build the argument string
                final StringBuilder arguments = new StringBuilder();
                arguments.append("p=").append(mIsP2PInvitation ? "1" : "0")
                        .append("&c=").append(mMustCreateChat ? "1" : "0")
                        .append("&i=").append(mGinloID)
                        .append("&s=").append(mPublicKeySHA256Base64);

                if(!StringUtil.isNullOrEmpty(mInvitationID)) {
                    arguments.append("&i=").append(mInvitationID);
                }

                // Second step: Build the final URL
                payload.append(TYPE_V3_PREFIX);
                final String s = arguments.toString();
                byte[] ba = new byte[s.length()];
                for (int i = 0; i < s.length(); i++) {
                    ba[i] = (byte) s.charAt(i);
                }
                payload.append(Base64.encodeToString(ba, Base64.NO_WRAP))
                        .append("&q=")
                        .append(ChecksumUtil.getSHA1ChecksumForString(s + V3_SALT));
                break;
            case TYPE_COUPLING_TAN:
                payload.append(mGinloID).append('|').append(mTAN);
                break;
            case TYPE_UNKNOWN:
            default:
                LogUtil.e(TAG, "buildPayload: Unknown QR code version: " + mVersion);
                return "";
        }

        return payload.toString();
    }

    /**
     * Encode a QR code as BitMatrix from the instance's attributes.
     * @param size
     * @return QR code BitMatrix or null if no payload is available
     */
    public BitMatrix createQRcodeMatrix(int size) {
        BitMatrix bm = null;
        if(StringUtil.isNullOrEmpty(mPayload)) {
            LogUtil.w(TAG, "createQRcode: No payload for QR code! Do nothing.");
        } else {
            try {
                bm = new QRCodeWriter().encode(mPayload, BarcodeFormat.QR_CODE, size, size);
            } catch (WriterException e) {
                LogUtil.e(TAG, "createQRcode: Could not build QR code: " + e.getMessage());
            }
        }
        return bm;
    }

    /**
     * Encode a QR code from the instance's attributes and build a corresponding bitmap.
     * @param widthPixel
     * @return
     */
    public Bitmap createQRCodeBitmap(int widthPixel) {
        Bitmap qrCodeBitmap = null;
        BitMatrix bitMatrix = createQRcodeMatrix(widthPixel);
        if(bitMatrix != null) {
            qrCodeBitmap = ImageUtil.decodeBitMatrix(bitMatrix);
        } else {
            LogUtil.w(TAG, "createQRCodeBitmap: Failed to create QR code for: " + mPayload);
        }
        return qrCodeBitmap;
    }
}

// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.util.Base64;
import android.util.Xml;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class XMLUtil {

    public static String getXMLFromPublicKey(PublicKey key) {
        String keyXML = null;

        try {
            RSAPublicKeySpec keySpec = SecurityUtil.getKeyFactoryInstance("RSA").getKeySpec(key,
                    RSAPublicKeySpec.class);

            BigInteger modulus = keySpec.getModulus();
            String modulusString = Base64.encodeToString(modulus.toByteArray(), Base64.NO_WRAP);
            BigInteger exponent = keySpec.getPublicExponent();
            String exponentString = Base64.encodeToString(exponent.toByteArray(), Base64.NO_WRAP);

            keyXML = "";
            keyXML += "<RSAKeyValue>";
            keyXML += "<Modulus>" + modulusString + "</Modulus>";
            keyXML += "<Exponent>" + exponentString + "</Exponent>";
            keyXML += "</RSAKeyValue>";
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            LogUtil.e(XMLUtil.class.getName(), e.getMessage(), e);
        }
        return keyXML;
    }

    public static String getXMLFromPrivateKey(PrivateKey key) {
        String keyXML = null;

        try {
            RSAPrivateCrtKeySpec keySpec = SecurityUtil.getKeyFactoryInstance("RSA").getKeySpec(key,
                    RSAPrivateCrtKeySpec.class);

            BigInteger modulus = keySpec.getModulus();
            String modulusString = Base64.encodeToString(modulus.toByteArray(), Base64.NO_WRAP);
            BigInteger exponent = keySpec.getPublicExponent();
            String exponentString = Base64.encodeToString(exponent.toByteArray(), Base64.NO_WRAP);
            BigInteger p = keySpec.getPrimeP();
            String pString = Base64.encodeToString(p.toByteArray(), Base64.NO_WRAP);
            BigInteger q = keySpec.getPrimeQ();
            String qString = Base64.encodeToString(q.toByteArray(), Base64.NO_WRAP);
            BigInteger dp = keySpec.getPrimeExponentP();
            String dpString = Base64.encodeToString(dp.toByteArray(), Base64.NO_WRAP);
            BigInteger dq = keySpec.getPrimeExponentQ();
            String dqString = Base64.encodeToString(dq.toByteArray(), Base64.NO_WRAP);
            BigInteger inverseQ = keySpec.getCrtCoefficient();
            String inverseQString = Base64.encodeToString(inverseQ.toByteArray(), Base64.NO_WRAP);
            BigInteger d = keySpec.getPrivateExponent();
            String dString = Base64.encodeToString(d.toByteArray(), Base64.NO_WRAP);

            keyXML = "";
            keyXML += "<RSAKeyValue>";
            keyXML += "<Modulus>" + modulusString + "</Modulus>";
            keyXML += "<Exponent>" + exponentString + "</Exponent>";
            keyXML += "<P>" + pString + "</P>";
            keyXML += "<Q>" + qString + "</Q>";
            keyXML += "<DP>" + dpString + "</DP>";
            keyXML += "<DQ>" + dqString + "</DQ>";
            keyXML += "<InverseQ>" + inverseQString + "</InverseQ>";
            keyXML += "<D>" + dString + "</D>";
            keyXML += "</RSAKeyValue>";
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            LogUtil.e(XMLUtil.class.getName(), e.getMessage(), e);
        }
        return keyXML;
    }

    public static PublicKey getPublicKeyFromXML(String xml)
            throws LocalizedException {
        try {
            if (xml == null) {
                return null;
            }

            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

            Map<String, String> pkMap;

            pkMap = parse(is);

            if ((pkMap == null) || (pkMap.size() != 2)) {
                // TODO: throw LocalizeException
                return null;
            }

            BigInteger modulus = null;
            BigInteger exponent = null;

            Set<String> keys = pkMap.keySet();

            for (String key : keys) {
                String value = pkMap.get(key);

                byte[] valueDecode = Base64.decode(value, Base64.NO_WRAP);

                if (key.equals("Modulus")) {
                    modulus = new BigInteger(1, valueDecode);
                } else if (key.equals("Exponent")) {
                    exponent = new BigInteger(1, valueDecode);
                }
            }

            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);

            return SecurityUtil.getKeyFactoryInstance("RSA").generatePublic(keySpec);
        } catch (XmlPullParserException | NullPointerException | NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            LogUtil.w(XMLUtil.class.getName(), "Parse XML failed", e);
            throw new LocalizedException(LocalizedException.PARSE_XML_FAILED, e);
        }
    }

    public static PrivateKey getPrivateKeyFromXML(String xml) {
        try {
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

            Map<String, String> pkMap = parse(is);

            if ((pkMap == null) || (pkMap.size() != 8)) {
                // TODO: throw LocalizeException
                return null;
            }

            BigInteger modulus = null;
            BigInteger exponent = null;
            BigInteger p = null;
            BigInteger q = null;
            BigInteger dp = null;
            BigInteger dq = null;
            BigInteger inverseQ = null;
            BigInteger d = null;

            Set<String> keys = pkMap.keySet();

            for (String key : keys) {
                String value = pkMap.get(key);

                byte[] valueDecode = Base64.decode(value, Base64.NO_WRAP);

                switch (key) {
                    case "Modulus":
                        modulus = new BigInteger(1, valueDecode);
                        break;
                    case "Exponent":
                        exponent = new BigInteger(1, valueDecode);
                        break;
                    case "P":
                        p = new BigInteger(1, valueDecode);
                        break;
                    case "Q":
                        q = new BigInteger(1, valueDecode);
                        break;
                    case "DP":
                        dp = new BigInteger(1, valueDecode);
                        break;
                    case "DQ":
                        dq = new BigInteger(1, valueDecode);
                        break;
                    case "InverseQ":
                        inverseQ = new BigInteger(1, valueDecode);
                        break;
                    case "D":
                        d = new BigInteger(1, valueDecode);
                        break;
                    default:
                        break;
                }
            }

            RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(modulus, exponent, d, p, q, dp, dq, inverseQ);

            return SecurityUtil.getKeyFactoryInstance("RSA").generatePrivate(keySpec);
        } catch (IOException | XmlPullParserException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            LogUtil.e(XMLUtil.class.getName(), e.getMessage(), e);
        }
        return null;
    }

    public static Map<String, String> parse(InputStream in)
            throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();

            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            return parseXML(parser);
        } finally {
            in.close();
        }
    }

    private static Map<String, String> parseXML(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Map<String, String> returnValue = null;

        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT: {
                    returnValue = new HashMap<>();
                    break;
                }
                case XmlPullParser.START_TAG: {
                    String name = parser.getName();

                    if (name.equalsIgnoreCase("data")) {
                        String attrName = parser.getAttributeValue(null, "name");

                        if (attrName != null) {
                            String text = parser.nextText();

                            returnValue.put(attrName, text);
                        }
                    } else if (!name.equalsIgnoreCase("RSAKeyValue")) {
                        String text = parser.nextText();

                        returnValue.put(name, text);
                    }
                    break;
                }
                case XmlPullParser.END_TAG:
                    break;
                default:
                    break;
            }
            eventType = parser.next();
        }

        return returnValue;
    }
}

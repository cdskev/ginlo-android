// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StreamUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChecksumUtil {
    
    private static final String TAG = ChecksumUtil.class.getSimpleName();

    private ChecksumUtil() {
    }

    public static String getSHA1ChecksumForString(String str) {
        String hash = null;
        byte[] data;

        try {
            data = str.getBytes(Encoding.UTF8);
            hash = getSHA1ChecksumForData(data);
        } catch (UnsupportedEncodingException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        return hash;
    }

    public static String getSHA256ChecksumForString(String str) {
        String hash = null;
        byte[] data;

        try {
            data = str.getBytes(Encoding.UTF8);
            hash = getSHA256ChecksumForData(data);
        } catch (UnsupportedEncodingException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        return hash;
    }

    private static byte[] getSHA1ChecksumBytesForData(byte[] data) {
        try {
            MessageDigest digester = SecurityUtil.getMessageDigestInstance("SHA-1");

            digester.update(data, 0, data.length);
            return digester.digest();
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        return null; //NOSONAR
    }

    private static byte[] getSHA256ChecksumBytesForData(byte[] data) {
        try {
            MessageDigest digester = SecurityUtil.getMessageDigestInstance("SHA-256");

            digester.update(data, 0, data.length);
            return digester.digest();
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        return null; //NOSONAR
    }

    public static String getSHA1ChecksumForData(byte[] data) {
        final byte[] array = getSHA1ChecksumBytesForData(data);

        if (array == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();

        for (int j = 0; j < array.length; ++j) {
            final int b = array[j] & 0xFF;

            if (b < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(b));
        }

        return sb.toString();
    }

    private static String getSHA256ChecksumForData(byte[] data) {
        final byte[] array = getSHA256ChecksumBytesForData(data);

        if (array == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();

        for (int j = 0; j < array.length; ++j) {
            final int b = array[j] & 0xFF;

            if (b < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(b));
        }

        return sb.toString();
    }

    public static byte[] getSHA256ChecksumAsBytesForString(String str) {
        byte[] hash = null;
        byte[] data;

        try {
            data = str.getBytes(Encoding.UTF8);
            hash = getSHA256ChecksumBytesForData(data);
        } catch (UnsupportedEncodingException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        return hash;
    }

    /**
     * getSHA1ChecksumFromFile
     *
     * @param file
     * @return
     * @throws LocalizedException
     */
    public static String getSHA1ChecksumFromFile(@NonNull File file)
            throws LocalizedException {
        if (!file.exists()) {
            return null;
        }

        FileInputStream fis = null;
        FileChannel inChannel = null;
        try {
            fis = new FileInputStream(file);

            inChannel = fis.getChannel();

            MappedByteBuffer byteBuffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());

            MessageDigest digester = SecurityUtil.getMessageDigestInstance("SHA-1");
            digester.update(byteBuffer);

            byte[] array = digester.digest();

            final StringBuilder sb = new StringBuilder();

            for (int j = 0; j < array.length; ++j) {
                final int b = array[j] & 0xFF;

                if (b < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            throw new LocalizedException(LocalizedException.GENERATE_CHECKSUM_FAILED, e);
        } finally {
            if (inChannel != null) {
                try {
                    inChannel.close();
                } catch (IOException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
            eu.ginlo_apps.ginlo.util.StreamUtil.closeStream(fis);
        }
    }

    public static String getSHA256ChecksumFromFile(@NonNull File file)
            throws LocalizedException {
        if (!file.exists()) {
            return null;
        }

        FileInputStream fis = null;
        FileChannel inChannel = null;
        try {
            fis = new FileInputStream(file);

            inChannel = fis.getChannel();

            MappedByteBuffer byteBuffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());

            MessageDigest digester = SecurityUtil.getMessageDigestInstance("SHA-256");
            digester.update(byteBuffer);

            byte[] array = digester.digest();

            final StringBuilder sb = new StringBuilder();

            for (int j = 0; j < array.length; ++j) {
                final int b = array[j] & 0xFF;

                if (b < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            throw new LocalizedException(LocalizedException.GENERATE_CHECKSUM_FAILED, e);
        } finally {
            if (inChannel != null) {
                try {
                    inChannel.close();
                } catch (IOException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
            StreamUtil.closeStream(fis);
        }
    }

    /**
     *
     * @param file
     * @return
     */
    public static String getMD5HashFromFile(@NonNull File file) {
        if (!file.exists()) {
            LogUtil.e(TAG, "getMD5HashFromFile: No such file!" + file.getPath());
            return "";
        }

        FileInputStream fis = null;
        FileChannel inChannel = null;
        try {
            fis = new FileInputStream(file);
            inChannel = fis.getChannel();

            MappedByteBuffer byteBuffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());

            MessageDigest md5 = SecurityUtil.getMessageDigestInstance("MD5");
            md5.update(byteBuffer);
            byte[] array = md5.digest();

            final StringBuilder sb = new StringBuilder();
            for (byte value : array) {
                final int b = value & 0xFF;

                if (b < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            return "";
            //throw new LocalizedException(LocalizedException.GENERATE_CHECKSUM_FAILED, e);
        } finally {
            if (inChannel != null) {
                try {
                    inChannel.close();
                } catch (IOException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
            StreamUtil.closeStream(fis);
        }
    }

    public static String getMD5ChecksumForString(String string) {
        try {
            return getMD5Hash(string);
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        return "";
    }

    /**
     * @throws IOException [!EXC_DESCRIPTION!]
     */
    private static String getMD5Hash(final String inputData)
            throws IOException {
        MessageDigest md5 = null;

        try {
            md5 = MessageDigest.getInstance("MD5"); //NOSONAR MD5 wird verwendet, um zu preufen, ob aenderungen vorliegen. Im Falle iner Kollision wuerde der Nutzer die entsprechen Daten gar nicht laden
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 unbekannt", e);
        }

        md5.update(inputData.getBytes(Encoding.UTF8));

        final byte[] array = md5.digest();
        final StringBuilder sb = new StringBuilder();

        for (int j = 0; j < array.length; ++j) {
            final int b = array[j] & 0xFF; //NOSONAR

            if (b < 0x10) //NOSONAR
            {
                sb.append('0');
            }
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }
}

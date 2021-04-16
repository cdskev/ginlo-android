// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;

import eu.ginlo_apps.ginlo.log.LogUtil;
import java.io.*;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class StreamUtil {

    public static void closeStream(Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            LogUtil.e(StreamUtil.class.getName(), e.getMessage(), e);
        }
    }

    /**
     * Definierte Menge von Bytes aus InputStream lesen.
     *
     * @param is             zu lesender InputStream
     * @param data           Array in das gelesene Bytes abgespeichert werden
     * @param expectedLength Anzahl der Bytes welche ausgelesen werden
     * @return Anzahl gelesener Bytes
     * @throws IOException  Falls is nicht gelesen werden kann.
     * @throws EOFException Falls in is weniger Bytes vorhanden waren als durch
     *                      expectedLength angegeben.
     */
    public static int safeRead(final InputStream is,
                               final byte[] data,
                               final int expectedLength)
            throws IOException {
        int n = 0;

        while (n < expectedLength) {
            int count = is.read(data, n, expectedLength - n);

            if (count <= 0) {
                throw new EOFException();
            }
            n += count;
        }
        return n;
    }

    /**
     * Stream kopieren
     *
     * @param bis Stream welcher kopiert wird
     * @param bos Kopie von bis
     * @throws IOException Falls bis nicht gelesen oder bos nicht geschrieben
     *                     werden kann.
     */
    public static void copyStreams(final InputStream bis,
                                   final OutputStream bos)
            throws IOException {
        int read = 0;
        byte[] data = new byte[32768];

        while ((read = bis.read(data, 0, 32768)) != -1) {
            bos.write(data, 0, read);
        }
        bos.flush();
    }

    public static void copyStreams(final InputStream bis,
                                   final ByteArrayOutputStream bos,
                                   HttpBaseTask.OnConnectionDataUpdatedListener listener)
            throws IOException {
        int read = 0;
        byte[] data = new byte[32768];
        while ((read = bis.read(data, 0, 32768)) != -1) {
            bos.write(data, 0, read);
            if (listener != null) {
                listener.onConnectionDataUpdated(bos.size());
            }
        }
        bos.flush();
    }
}

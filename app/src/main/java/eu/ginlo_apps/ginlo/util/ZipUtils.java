// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.net.Uri;

import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Florian on 25.05.16.
 */
public class ZipUtils {
    private final static String TAG = ZipUtils.class.getSimpleName();
    private final List<String> fileList;
    private final Object mInputObject;
    private final Object mOutputObject;
    private final Context context;

    /**
     * @param inputObject Must be of type String or Uri depending on use case
     * @param outputObject Must be of type String or Uri depending on use case
     */
    public ZipUtils(Context context, Object inputObject, Object outputObject) {
        mInputObject = inputObject;
        mOutputObject = outputObject;
        fileList = new ArrayList<>();
        this.context = context;
    }

    public void startZip() throws LocalizedException {
        if(!(mInputObject instanceof String)) {
            throw new LocalizedException(LocalizedException.FILE_ZIPPING_FAILED, "Incompatible input object: " + mInputObject.getClass().getName());
        }

        generateFileList(new File((String) mInputObject));

        if(mOutputObject instanceof String) {
            zipIt((String) mOutputObject);
        } else if (mOutputObject instanceof Uri) {
            try {
                FileOutputStream fos = (FileOutputStream) context.getContentResolver().openOutputStream((Uri) mOutputObject);
                zipFilesToStream(this.fileList, fos);
                fos.close();
            } catch (Exception e) {
                throw new LocalizedException(LocalizedException.FILE_ZIPPING_FAILED, "Cannot open backup destination: " + e.getMessage());
            }
        } else {
            throw new LocalizedException(LocalizedException.FILE_ZIPPING_FAILED, "Cannot handle object type " + mOutputObject.getClass().getName());
        }
    }

    public void startUnzip() throws LocalizedException {
        if(!(mOutputObject instanceof String)) {
            throw new LocalizedException(LocalizedException.FILE_UNZIPPING_FAILED, "Incompatible output object: " + mOutputObject.getClass().getName());
        }

        if(mInputObject instanceof String) {
            unzipIt((String) mInputObject, (String) mOutputObject);
        } else if(mInputObject instanceof Uri) {
            try {
                FileInputStream fis = (FileInputStream) context.getContentResolver().openInputStream((Uri) mInputObject);
                unzipFilesFromStream(fis, (String) mOutputObject);
                fis.close();
            } catch (Exception e) {
                throw new LocalizedException(LocalizedException.FILE_UNZIPPING_FAILED, "Cannot open restore source: " + e.getMessage());
            }
        } else {
            throw new LocalizedException(LocalizedException.FILE_UNZIPPING_FAILED, "Cannot handle object type " + mInputObject.getClass().getName());
        }
    }

    private void unzipIt(String zipFile, String outputFolder) throws LocalizedException {
        try {
            final FileInputStream fis = new FileInputStream(zipFile);
            unzipFilesFromStream(fis, outputFolder);
            fis.close();
        } catch (IOException ex) {
            throw new LocalizedException(LocalizedException.FILE_UNZIPPING_FAILED, ex.getMessage(), ex);
        }
    }

    private void unzipFilesFromStream(FileInputStream fis, String outputFolder) throws LocalizedException {
        byte[] buffer = new byte[1024];
        ZipInputStream zis = null;
        FileOutputStream fos = null;

        try {
            //create output directory if not exists
            File folder = new File(outputFolder);
            if (!folder.exists()) {
                if(!folder.mkdir()) {
                    throw new LocalizedException(LocalizedException.CREATE_FILE_FAILED);
                }
            }

            // For check on possible Zip Path Traversal attack
            final String canonicalOutputFolder = folder.getCanonicalPath();

            //get the zip file content
            zis = new ZipInputStream(fis);
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                final String fileName = ze.getName();
                final File newFile = new File(outputFolder, fileName);
                final String canonicalPath = newFile.getCanonicalPath();
                LogUtil.i(TAG, "unzipFilesFromStream: Processing " + fileName);
                if (!canonicalPath.startsWith(canonicalOutputFolder)) {
                    throw new SecurityException("Possible Zip Path Traversal attack");
                }

                final String parent = newFile.getParent();
                if(parent == null) {
                    throw new LocalizedException(LocalizedException.OBJECT_NULL);
                }

                //noinspection ResultOfMethodCallIgnored
                new File(parent).mkdirs();

                fos = new FileOutputStream(newFile);

                StreamUtil.copyStreams(zis, fos);

                try {
                    ze = zis.getNextEntry();
                } catch (Exception zipex) {
                    // Older Android zip lib may be broken. This may be the end of the archive. Ignore exception.
                    LogUtil.w(TAG, "unzipFilesFromStream: Caught exception on getNextEntry(): " + zipex.getMessage());
                    break;
                }
            }

            zis.closeEntry();
        } catch (Exception ex) {
            throw new LocalizedException(LocalizedException.FILE_UNZIPPING_FAILED, ex.getMessage(), ex);
        } finally {
            StreamUtil.closeStream(fos);
            StreamUtil.closeStream(zis);
        }
    }


    private void zipIt(String zipFile) throws LocalizedException {
        try {
            FileOutputStream fos = new FileOutputStream(zipFile);
            zipFilesToStream(this.fileList, fos);
            fos.close();
        } catch (IOException ex) {
            throw new LocalizedException(LocalizedException.FILE_ZIPPING_FAILED, ex.getMessage(), ex);
        }
    }

    private void zipFilesToStream(List<String> files, FileOutputStream fos) throws LocalizedException {
        byte[] buffer = new byte[1024];

        try {
            ZipOutputStream zos = new ZipOutputStream(fos);
            for (String file : files) {
                ZipEntry ze = new ZipEntry(/*source + File.separator +*/ file);
                zos.putNextEntry(ze);
                try (FileInputStream in = new FileInputStream(mInputObject + File.separator + file)) {

                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
            }

            zos.closeEntry();
        } catch (Exception ex) {
            throw new LocalizedException(LocalizedException.FILE_ZIPPING_FAILED, ex.getMessage(), ex);
        }
    }

    private void generateFileList(File node) {

        // add file only
        if (node.isFile()) {
            fileList.add(relativeFilePath(node.toString()));
        }

        if (node.isDirectory()) {
            String[] subNode = node.list();
            for (String filename : subNode) {
                generateFileList(new File(node, filename));
            }
        }
    }

    private String relativeFilePath(String file) {
        return file.substring(mInputObject.toString().length() + 1);
    }
}

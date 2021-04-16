// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.util.StreamUtil;

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
    private final List<String> fileList;
    private final String mSourcePath;
    private final String mOutputPath;

    /**
     * @param sourcePath
     * @param outputFilePath
     */
    public ZipUtils(String sourcePath, String outputFilePath) {
        mSourcePath = sourcePath;
        mOutputPath = outputFilePath;

        fileList = new ArrayList<>();
    }

    public void startZip() throws LocalizedException {
        generateFileList(new File(mSourcePath));
        zipIt(mOutputPath);
    }

    public void startUnzip() throws LocalizedException {
        unZipIt(mSourcePath, mOutputPath);
    }

    private void unZipIt(String zipFile, String outputFolder) throws LocalizedException {
        byte[] buffer = new byte[1024];
        ZipInputStream zis = null;

        try {
            //create output directory is not exists
            File folder = new File(outputFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }

            //get the zip file content
            zis = new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(newFile);

                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                } finally {
                    StreamUtil.closeStream(fos);
                }

                ze = zis.getNextEntry();
            }

            zis.closeEntry();
        } catch (IOException ex) {
            throw new LocalizedException(LocalizedException.FILE_UNZIPPING_FAILED, ex.getMessage(), ex);
        } finally {
            StreamUtil.closeStream(zis);
        }
    }

    private void zipIt(String zipFile) throws LocalizedException {
        byte[] buffer = new byte[1024];
//         String source = "";

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
//            try
//            {
//               source = mSourcePath.substring(mSourcePath.lastIndexOf("\\") + 1, mSourcePath.length());
//            }
//            catch (Exception e)
//            {
//               source = mSourcePath;
//            }
            for (String file : this.fileList) {
                ZipEntry ze = new ZipEntry(/*source + File.separator +*/ file);
                zos.putNextEntry(ze);
                try (FileInputStream in = new FileInputStream(mSourcePath + File.separator + file)) {

                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
            }

            zos.closeEntry();
        } catch (IOException ex) {
            throw new LocalizedException(LocalizedException.FILE_ZIPPING_FAILED, ex.getMessage(), ex);
        }
    }

    private void generateFileList(File node) {

        // add file only
        if (node.isFile()) {
            fileList.add(generateZipEntry(node.toString()));
        }

        if (node.isDirectory()) {
            String[] subNote = node.list();
            for (String filename : subNote) {
                generateFileList(new File(node, filename));
            }
        }
    }

    private String generateZipEntry(String file) {
        return file.substring(mSourcePath.length() + 1, file.length());
    }
}

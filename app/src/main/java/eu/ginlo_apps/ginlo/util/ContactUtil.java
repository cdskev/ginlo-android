// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.CompanyContactUtil;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class ContactUtil {
    public static final int SORT_ASCENDING = 1;

    private ContactUtil() {
    }

    public static Comparator<CompanyContact> getCompanyContactListSortComparator(final SimsMeApplication application) {

        return new Comparator<CompanyContact>() {
            @Override
            public int compare(final CompanyContact contact1,
                               final CompanyContact contact2) {
                try {
                    String firstName1 = CompanyContactUtil.getInstance(application).getAttributeFromContact(contact1, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
                    firstName1 = (firstName1 != null) ? firstName1 : "";

                    String firstName2 = CompanyContactUtil.getInstance(application).getAttributeFromContact(contact2, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
                    firstName2 = (firstName2 != null) ? firstName2 : "";

                    String lastName1 = CompanyContactUtil.getInstance(application).getAttributeFromContact(contact1, CompanyContact.COMPANY_CONTACT_LASTNAME);
                    lastName1 = (lastName1 != null) ? lastName1 : firstName1;

                    String lastName2 = CompanyContactUtil.getInstance(application).getAttributeFromContact(contact2, CompanyContact.COMPANY_CONTACT_LASTNAME);
                    lastName2 = (lastName2 != null) ? lastName2 : firstName2;

                    return lastName1.compareTo(lastName2);
                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                    return 0;
                }
            }
        };
    }

    public static Comparator<Contact> getSortComparator(final int sortOrder) {

        return new Comparator<Contact>() {
            @Override
            public int compare(final Contact c1,
                               final Contact c2) {
                try {
                    String name1 = c1.getIdentName();
                    String name2 = c2.getIdentName();

                    if ((name1 == null) && (name2 == null)) {
                        return 0;
                    } else if (name1 == null) {
                        return sortOrder;
                    } else if (name2 == null) {
                        return -1 * sortOrder;
                    }

                    name1 = name1.toLowerCase(Locale.US);
                    name2 = name2.toLowerCase(Locale.US);

                    return name1.compareTo(name2) * sortOrder;
                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                    return 0;
                }
            }
        };
    }

    public static String getDisplayText(final Contact contact)
            throws LocalizedException {
        String returnValue = "";
        final String displayName = contact.getName();
        final String lastName = contact.getLastName();

        if ((lastName == null) || (lastName.length() < 1)) {
            return displayName;
        }

        final int indexLastName = displayName.indexOf(lastName);

        if (indexLastName == -1) {
            return displayName;
        }

        returnValue = displayName.substring(0, indexLastName) + "<b>" + lastName + "</b>"
                + displayName.substring(indexLastName + lastName.length());

        returnValue = returnValue.replace(" ", "&nbsp;");

        return returnValue;
    }

    public static int getColorForName(final String name) {
        final int[] colors = new int[]
                {
                        0xFFAD439E,
                        0xFFA543AC,
                        0xFF9143AD,
                        0xFF7D43AD,
                        0xFF6843AD,
                        0xFF5343AD,
                        0xFF464AAE,
                        0xFF435DAD,
                        0xFF4372AD,
                        0xFF4386AD,
                        0xFF439BAD,
                        0xFF42ABA7,
                        0xFF44AD94,
                        0xFF43AD7F,
                        0xFF43AD6A,
                        0xFF43AD56,
                        0xFF48AE46,
                        0xFF5AAD43,
                        0xFF6FAD44,
                        0xFF84AD44,
                        0xFF98AD44,
                        0xFFA8A741,
                        0xFFAD9743,
                        0xFFAD8243,
                        0xFFAD6E43,
                        0xFFAD5943
                };

        if (StringUtil.isNullOrEmpty(name)) {
            return colors[0];
        }

        char ch = name.charAt(0);

        if (name.matches("\\+[0-9 ]+")) {
            String lastNameNumbers = name.substring(1).replaceAll(" ", "");

            if (lastNameNumbers.length() > 1) {
                ch = lastNameNumbers.substring(lastNameNumbers.length() - 2).charAt(0);
            }
        }

        if ((ch >= '0') && (ch <= '9')) {
            return colors[ch - '0'];
        }
        if ((ch >= 'a') && (ch <= 'z')) {
            return colors[ch - 'a'];
        }
        if ((ch >= 'A') && (ch <= 'Z')) {
            return colors[ch - 'A'];
        }

        return colors[0];
    }

    /**
     * Decodes and scales a contact's image from a file pointed to by a Uri in
     * the contact's data, and returns the result as a Bitmap. The column that
     * contains the Uri varies according to the platform version.
     *
     * @param photoData the Contact.PHOTO_THUMBNAIL_URI value
     * @param imageSize The desired target width and height of the output image
     *                  in pixels.
     * @return A Bitmap containing the contact's image, resized to fit the
     * provided image size. If no thumbnail exists, returns null.
     */
    public static Bitmap loadContactPhotoThumbnail(final String photoData,
                                                   final int imageSize,
                                                   final Context context) {
        if ((context == null) || (photoData == null) || (imageSize < 1)) {
            return null;
        }

        // Instantiates an AssetFileDescriptor. Given a content Uri pointing to an image file, the
        // ContentResolver can return an AssetFileDescriptor for the file.
        AssetFileDescriptor afd = null;

        // This "try" block catches an Exception if the file descriptor returned from the Contacts
        // Provider doesn't point to an existing file.
        try {
            final Uri thumbUri;

            // converts the Uri passed as a string to a Uri object.
            thumbUri = Uri.parse(photoData);

            // Retrieves a file descriptor from the Contacts Provider. To learn more about this
            // feature, read the reference documentation for
            // ContentResolver#openAssetFileDescriptor.
            afd = context.getContentResolver().openAssetFileDescriptor(thumbUri, "r");

            if (afd == null) {
                return null;
            }

            // Gets a FileDescriptor from the AssetFileDescriptor. A BitmapFactory object can
            // decode the contents of a file pointed to by a FileDescriptor into a Bitmap.
            final FileDescriptor fileDescriptor = afd.getFileDescriptor();

            if (fileDescriptor != null) {
                // Decodes a Bitmap from the image pointed to by the FileDescriptor, and scales it
                // to the specified width and height
                return ImageLoader.decodeSampledBitmapFromDescriptor(fileDescriptor, imageSize, imageSize);
            }
        } catch (FileNotFoundException e) {
            // If the file pointed to by the thumbnail URI doesn't exist, or the file can't be
            // opened in "read" mMode, ContentResolver.openAssetFileDescriptor throws a
            // FileNotFoundException.
            LogUtil.e(Contact.class.getName(), e.getMessage(), e);
        } catch (SecurityException e) {
            LogUtil.w(BitmapUtil.class.getSimpleName(), "SecurityException uri:" + photoData, e);
        } finally {
            // If an AssetFileDescriptor was returned, try to close it
            if (afd != null) {
                try {
                    afd.close();
                } catch (IOException e) {
                    LogUtil.e(ContactUtil.class.getName(), e.getMessage(), e);
                    // NO_PMD
                    // Closing a file descriptor might cause an IOException if the file is
                    // already closed. Nothing extra is needed to handle this.
                }
            }
        }

        // If the decoding failed, returns null
        return null;
    }

    public static List<Contact> sortContactsByMandantPriority(final List<Contact> contacts, final PreferencesController preferencesController) {
        try {
            List<Mandant> mandantList = preferencesController.getMandantenList();

            if (mandantList == null || mandantList.size() == 0) {
                return contacts;
            }

            int mandantSize = mandantList.size() + 1;

            final TreeMap<Integer, ArrayList<Contact>> map = new TreeMap<>();
            for (final Contact contact : contacts) {
                if (contact == null) {
                    // Sanity Check --> Ticket 4806
                    continue;
                }
                final Mandant mandant = preferencesController.getMandantFromIdent(contact.getMandant());
                if (mandant == null) {
                    int key = mandantSize++;
                    ArrayList<Contact> value = map.get(key);
                    if (value == null) {
                        value = new ArrayList<>();
                        value.add(contact);
                        map.put(key, value);
                    } else {
                        value.add(contact);
                    }
                } else {
                    final int priority = mandant.priority;
                    ArrayList<Contact> value = map.get(priority);
                    if (value == null) {
                        value = new ArrayList<>();
                        value.add(contact);
                        map.put(priority, value);
                    } else {
                        value.add(contact);
                    }
                }
            }

            final ArrayList<Contact> retVal = new ArrayList<>();

            for (final ArrayList<Contact> list : map.values()) {
                retVal.addAll(list);
            }
            return retVal;
        } catch (LocalizedException e) {
            LogUtil.e(ContactUtil.class.getSimpleName(), e.getMessage(), e);
            return contacts;
        }
    }

    public enum SearchType {
        PHONE,
        EMAIL,
        SIMSME_ID
    }
}

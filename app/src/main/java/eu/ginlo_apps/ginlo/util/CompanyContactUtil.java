// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.util.StringUtil;

import javax.crypto.SecretKey;

/**
 * Created by Florian on 19.02.18.
 */

public class CompanyContactUtil {
    private static CompanyContactUtil mInstance;
    private final SimsMeApplication mApplication;

    private CompanyContactUtil(SimsMeApplication application) {
        mApplication = application;
    }

    public static CompanyContactUtil getInstance(@NonNull SimsMeApplication application) {
        synchronized (CompanyContactUtil.class) {
            if (mInstance == null) {
                mInstance = new CompanyContactUtil(application);
            }

            return mInstance;
        }
    }

    public String getAttributeFromContact(@NonNull CompanyContact companyContact, @NonNull final String attribute)
            throws LocalizedException {
        SecretKey key = null;
        if (eu.ginlo_apps.ginlo.util.StringUtil.isEqual(companyContact.getClassType(), Contact.CLASS_DOMAIN_ENTRY)) {
            key = mApplication.getAccountController().getDomainAesKey();
        } else if (StringUtil.isEqual(companyContact.getClassType(), Contact.CLASS_COMPANY_ENTRY)) {
            key = mApplication.getAccountController().getCompanyUserAesKey();
        }

        if (key == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Key is null");
        }

        return companyContact.getEncryptedAttribute(key, attribute);
    }
}

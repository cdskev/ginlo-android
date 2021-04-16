// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * Created by Florian on 01.06.17.
 * <p>
 * Action fuer {@link eu.ginlo_apps.ginlo.model.backend.action.Action#ACTION_COMPANY_ENCRYPT_INFO}
 */

public class CompanyEncryptInfoAction extends Action {
    public String companyEncryptionSeed;

    public String companyEncryptionSalt;

    public String companyEncryptionDiff;

    public String companyEncryptionPart;
}

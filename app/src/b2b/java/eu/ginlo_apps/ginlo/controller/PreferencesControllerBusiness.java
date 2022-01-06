// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.util.ConfigUtil;

/**
 * Created by Florian on 08.06.17.
 *
 */

public class PreferencesControllerBusiness extends PreferencesController
{
   public PreferencesControllerBusiness(SimsMeApplication application)
   {
      super(application);
   }

   @Override
   public void onDeleteAllCompanyContacts()
   {
      super.onDeleteAllCompanyContacts();
      serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_LIST_COMPANY_INDEX, "");
   }

}

// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util.notobfuscate;

import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.AbsenceActivity;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.ChatsOverviewActivityBusiness;
import eu.ginlo_apps.ginlo.CompanyContactDetailActivity;
import eu.ginlo_apps.ginlo.ContactsActivity;
import eu.ginlo_apps.ginlo.ContactsActivityBusiness;
import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.LoginActivityBusiness;
import eu.ginlo_apps.ginlo.RestoreBackupActivity;
import eu.ginlo_apps.ginlo.RestoreBackupActivityBusiness;
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsOverviewActivity;
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity;
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivityBusiness;
import eu.ginlo_apps.ginlo.activity.register.InitProfileActivity;
import eu.ginlo_apps.ginlo.activity.register.InitProfileActivityBusiness;
import eu.ginlo_apps.ginlo.activity.register.MdmRegisterActivity;
import eu.ginlo_apps.ginlo.activity.register.PasswordActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.util.BaManagedConfigUtil;
import eu.ginlo_apps.ginlo.util.DialogHelperBusiness;
import eu.ginlo_apps.ginlo.util.IDialogHelper;
import eu.ginlo_apps.ginlo.util.IManagedConfigUtil;

public class ClassUtilBusiness implements IClassUtil {

    @Override
    public @NonNull
    Class<?> getActivityAfterIntro(@NonNull SimsMeApplication application) {
        if (getManagedConfigUtil(application).hasAutomaticMdmRegistrationKeys()) {
            return MdmRegisterActivity.class;
        }
        return PasswordActivity.class;
    }

    @NonNull
    @Override
    public Class<?> getStartActivityClass(@NonNull SimsMeApplication application) {
        return ChatsOverviewActivityBusiness.class;
    }

    @NonNull
    @Override
    public Class<? extends ChatsOverviewActivity> getChatOverviewActivityClass() {
        return ChatsOverviewActivityBusiness.class;
    }

    @Override
    public IManagedConfigUtil getManagedConfigUtil(@NonNull SimsMeApplication application) {
        return BaManagedConfigUtil.getInstance(application);
    }

    @NonNull
    @Override
    public Class<? extends InitProfileActivity> getInitProfileActivityClass() {
        return InitProfileActivityBusiness.class;
    }

    @NonNull
    @Override
    public Class<? extends ContactsActivity> getContactsActivityClass() {
        return ContactsActivityBusiness.class;
    }

    @NonNull
    @Override
    public Class<? extends IdentConfirmActivity> getIdentConfirmActivityClass() {
        return IdentConfirmActivityBusiness.class;
    }

    @Override
    public IDialogHelper getDialogHelper(@NonNull SimsMeApplication application) {
        return DialogHelperBusiness.getInstance(application);
    }

    @NonNull
    @Override
    public Class<? extends LoginActivity> getLoginActivityClass() {
        return LoginActivityBusiness.class;
    }

    @NonNull
    @Override
    public Class<? extends RestoreBackupActivity> getRestoreBackupActivityClass() {
        return RestoreBackupActivityBusiness.class;
    }

    //quickfix, um die PK zum Laufen zu bringen
    @Override
    public Class<? extends BaseActivity> getAbsenceActivityClass() {
        return AbsenceActivity.class;
    }

    @NonNull
    @Override
    public Class<? extends BaseActivity> getCompanyContactDetailActivity() {
        return CompanyContactDetailActivity.class;
    }

}

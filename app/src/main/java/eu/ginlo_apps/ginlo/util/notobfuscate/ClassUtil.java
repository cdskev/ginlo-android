// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util.notobfuscate;

import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.ContactDetailActivity;
import eu.ginlo_apps.ginlo.ContactsActivity;
import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.RestoreBackupActivity;
import eu.ginlo_apps.ginlo.StatusTextActivity;
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsOverviewActivity;
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity;
import eu.ginlo_apps.ginlo.activity.register.InitProfileActivity;
import eu.ginlo_apps.ginlo.activity.register.PasswordActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.util.DialogHelper;
import eu.ginlo_apps.ginlo.util.IDialogHelper;
import eu.ginlo_apps.ginlo.util.IManagedConfigUtil;
import eu.ginlo_apps.ginlo.util.notobfuscate.IClassUtil;

public class ClassUtil implements IClassUtil {

    @Override
    public @NonNull
    Class<?> getActivityAfterIntro(@NonNull SimsMeApplication application) {
        return PasswordActivity.class;
    }

    @Override
    public @NonNull
    Class<?> getStartActivityClass(@NonNull SimsMeApplication application) {
        return ChatsOverviewActivity.class;
    }

    @Override
    public IManagedConfigUtil getManagedConfigUtil(@NonNull SimsMeApplication application) {
        return null;
    }

    @NonNull
    @Override
    public Class<? extends ChatsOverviewActivity> getChatOverviewActivityClass() {
        return ChatsOverviewActivity.class;
    }

    @Override
    public IDialogHelper getDialogHelper(@NonNull SimsMeApplication application) {
        return DialogHelper.getInstance(application);
    }

    @NonNull
    public Class<? extends InitProfileActivity> getInitProfileActivityClass() {
        return InitProfileActivity.class;
    }

    @NonNull
    @Override
    public Class<? extends ContactsActivity> getContactsActivityClass() {
        return ContactsActivity.class;
    }

    @NonNull
    @Override
    public Class<? extends IdentConfirmActivity> getIdentConfirmActivityClass() {
        return IdentConfirmActivity.class;
    }

    @NonNull
    @Override
    public Class<? extends LoginActivity> getLoginActivityClass() {
        return LoginActivity.class;
    }

    @NonNull
    @Override
    public Class<? extends RestoreBackupActivity> getRestoreBackupActivityClass() {
        return RestoreBackupActivity.class;
    }

    //quickfix, um die PK zum Laufen zu bringen
    @Override
    public Class<? extends BaseActivity> getAbsenceActivityClass() {
        return StatusTextActivity.class;
    }

    @NonNull
    @Override
    public Class<? extends BaseActivity> getCompanyContactDetailActivity() {
        return ContactDetailActivity.class;
    }
}


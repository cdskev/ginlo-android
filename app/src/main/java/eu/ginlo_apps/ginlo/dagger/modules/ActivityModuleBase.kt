// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.dagger.modules

import dagger.Module
import dagger.android.ContributesAndroidInjector
import eu.ginlo_apps.ginlo.*
import eu.ginlo_apps.ginlo.AVCActivity
import eu.ginlo_apps.ginlo.activity.chat.*
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsOverviewActivity
import eu.ginlo_apps.ginlo.activity.device.DeviceCoupleConfirmActivity
import eu.ginlo_apps.ginlo.activity.device.DeviceCoupleFinishActivity
import eu.ginlo_apps.ginlo.activity.device.DeviceCoupleNewActivity
import eu.ginlo_apps.ginlo.activity.device.DeviceDetailActivity
import eu.ginlo_apps.ginlo.activity.device.DevicesOverviewActivity
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesChatsActivity
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesInformationActivity
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesMediaDownloadActivity
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesNotificationsActivity
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesAppearanceActivity
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesOverviewActivity
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesPasswordActivity
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesPrivacyActivity
import eu.ginlo_apps.ginlo.activity.preferences.information.AboutActivity
import eu.ginlo_apps.ginlo.activity.preferences.information.SupportActivity
import eu.ginlo_apps.ginlo.activity.preferences.information.license.LicensesActivity
import eu.ginlo_apps.ginlo.activity.profile.ProfileActivity
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity
import eu.ginlo_apps.ginlo.activity.register.IdentRequestActivity
import eu.ginlo_apps.ginlo.activity.register.InitProfileActivity
import eu.ginlo_apps.ginlo.activity.register.IntroActivity
import eu.ginlo_apps.ginlo.activity.register.PasswordActivity
import eu.ginlo_apps.ginlo.activity.register.ShowSimsmeIdActivity
import eu.ginlo_apps.ginlo.activity.register.device.DeviceLoginActivity
import eu.ginlo_apps.ginlo.activity.register.device.DeviceRequestTanActivity
import eu.ginlo_apps.ginlo.activity.register.device.DeviceVerifyActivity
import eu.ginlo_apps.ginlo.activity.register.device.WelcomeBackActivity
import eu.ginlo_apps.ginlo.activity.reregister.ChangePhoneActivity
import eu.ginlo_apps.ginlo.activity.reregister.ConfirmPhoneActivity
import eu.ginlo_apps.ginlo.view.cropimage.CropImageActivity

@Suppress("unused")
@Module
abstract class ActivityModuleBase {

    @ContributesAndroidInjector
    abstract fun contributeSupportActivity(): SupportActivity

    @ContributesAndroidInjector
    abstract fun contributeProfileActivity(): ProfileActivity

    @ContributesAndroidInjector
    abstract fun contributeChatRoomInfoActivity(): ChatRoomInfoActivity

    @ContributesAndroidInjector
    abstract fun contributeContactDetailActivity(): ContactDetailActivity

    @ContributesAndroidInjector
    abstract fun contributeInitProfileActivity(): InitProfileActivity

    @ContributesAndroidInjector
    abstract fun contributeChatInputActivity(): ChatInputActivity

    @ContributesAndroidInjector
    abstract fun contributeMessageDetailsActivity(): MessageDetailsActivity

    @ContributesAndroidInjector
    abstract fun contributePreferencesChatsActivity(): PreferencesChatsActivity

    @ContributesAndroidInjector
    abstract fun contributeChatsOverviewActivity(): ChatsOverviewActivity

    @ContributesAndroidInjector
    abstract fun contributeNoContactFoundActivity(): NoContactFoundActivity

    @ContributesAndroidInjector
    abstract fun contributeSearchContactActivity(): SearchContactActivity

    @ContributesAndroidInjector
    abstract fun contributeMuteChatActivity(): MuteChatActivity

    @ContributesAndroidInjector
    abstract fun contributeChannelListActivity(): ChannelListActivity

    @ContributesAndroidInjector
    abstract fun contributeChannelDetailActivity(): ChannelDetailActivity

    @ContributesAndroidInjector
    abstract fun contributeDeleteAccountActivity(): DeleteAccountActivity

    @ContributesAndroidInjector
    abstract fun contributeRestoreBackupActivity(): RestoreBackupActivity

    @ContributesAndroidInjector
    abstract fun contributePreferencesPasswordActivity(): PreferencesPasswordActivity

    @ContributesAndroidInjector
    abstract fun contributePreferencesPrivacyActivity(): PreferencesPrivacyActivity

    @ContributesAndroidInjector
    abstract fun contributePreferencesNotificationsActivity(): PreferencesNotificationsActivity

    @ContributesAndroidInjector
    abstract fun PreferencesAppearanceActivity(): PreferencesAppearanceActivity

    @ContributesAndroidInjector
    abstract fun contributeIdentRequestActivity(): IdentRequestActivity

    @ContributesAndroidInjector
    abstract fun contributeChangePhoneActivity(): ChangePhoneActivity

    @ContributesAndroidInjector
    abstract fun contributeDevicesOverviewActivity(): DevicesOverviewActivity

    @ContributesAndroidInjector
    abstract fun contributeRecoverPasswordActivity(): RecoverPasswordActivity

    @ContributesAndroidInjector
    abstract fun contributeSingleChatActivity(): SingleChatActivity

    @ContributesAndroidInjector
    abstract fun contributeGroupChatActivity(): GroupChatActivity

    @ContributesAndroidInjector
    abstract fun contributeChannelChatActivity(): ChannelChatActivity

    @ContributesAndroidInjector
    abstract fun contributeIntroActivity(): IntroActivity

    @ContributesAndroidInjector
    abstract fun contributeWelcomeBackActivity(): WelcomeBackActivity

    @ContributesAndroidInjector
    abstract fun contributeDeviceLoginActivity(): DeviceLoginActivity

    @ContributesAndroidInjector
    abstract fun contributeDeviceRequestTanActivity(): DeviceRequestTanActivity

    @ContributesAndroidInjector
    abstract fun contributeDeviceVerifyActivity(): DeviceVerifyActivity

    @ContributesAndroidInjector
    abstract fun contributePasswordActivity(): PasswordActivity

    @ContributesAndroidInjector
    abstract fun contributeSetPasswordActivity(): SetPasswordActivity

    @ContributesAndroidInjector
    abstract fun contributeContactsActivity(): ContactsActivity

    @ContributesAndroidInjector
    abstract fun contributePreferencesOverviewActivity(): PreferencesOverviewActivity

    @ContributesAndroidInjector
    abstract fun contributePreferencesInformationActivity(): PreferencesInformationActivity

    @ContributesAndroidInjector
    abstract fun contributePreferencesMediaDownloadActivity(): PreferencesMediaDownloadActivity

    @ContributesAndroidInjector
    abstract fun contributeAboutActivity(): AboutActivity

    @ContributesAndroidInjector
    abstract fun contributeDistributorChatActivity(): DistributorChatActivity

    @ContributesAndroidInjector
    abstract fun contributeIdentConfirmActivity(): IdentConfirmActivity

    @ContributesAndroidInjector
    abstract fun contributeSystemChatActivity(): SystemChatActivity

    @ContributesAndroidInjector
    abstract fun contributeConfirmPhoneActivity(): ConfirmPhoneActivity

    @ContributesAndroidInjector
    abstract fun contributeLoginActivity(): LoginActivity

    @ContributesAndroidInjector
    abstract fun contributeShowSimsmeIdActivity(): ShowSimsmeIdActivity

    @ContributesAndroidInjector
    abstract fun contributeLicensesActivity(): LicensesActivity

    @ContributesAndroidInjector
    abstract fun contributeCompanyDetailsActivity(): CompanyDetailsActivity

    @ContributesAndroidInjector
    abstract fun contributeLocationActivity(): LocationActivity

    @ContributesAndroidInjector
    abstract fun contributeDestructionActivity(): DestructionActivity

    @ContributesAndroidInjector
    abstract fun contributeStatusTextActivity(): StatusTextActivity

    @ContributesAndroidInjector
    abstract fun contributeBlockedContactsActivity(): BlockedContactsActivity

    @ContributesAndroidInjector
    abstract fun contributePreviewActivity(): PreviewActivity

    @ContributesAndroidInjector
    abstract fun contributeViewAttachmentActivity(): ViewAttachmentActivity

    @ContributesAndroidInjector
    abstract fun contributeCameraActivity(): CameraActivity

    @ContributesAndroidInjector
    abstract fun contributeAVCActivity(): AVCActivity

    @ContributesAndroidInjector
    abstract fun contributeChatBackgroundActivity(): ChatBackgroundActivity

    @ContributesAndroidInjector
    abstract fun contributeConfigureBackupActivity(): ConfigureBackupActivity

    @ContributesAndroidInjector
    abstract fun contributeForwardActivity(): ForwardActivity

    @ContributesAndroidInjector
    abstract fun contributeErrorActivity(): ErrorActivity

    @ContributesAndroidInjector
    abstract fun contributeCropImageActivity(): CropImageActivity

    @ContributesAndroidInjector
    abstract fun contributeDeviceDetailActivity(): DeviceDetailActivity

    @ContributesAndroidInjector
    abstract fun contributeDeviceCoupleNewActivity(): DeviceCoupleNewActivity

    @ContributesAndroidInjector
    abstract fun contributeDeviceCoupleConfirmActivity(): DeviceCoupleConfirmActivity

    @ContributesAndroidInjector
    abstract fun contributeDeviceCoupleFinishActivity(): DeviceCoupleFinishActivity

    @ContributesAndroidInjector
    abstract fun contributeChatRoomMemberActivity(): ChatRoomMemberActivity
}
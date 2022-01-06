// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.dagger.modules

import dagger.Module
import dagger.android.ContributesAndroidInjector
import eu.ginlo_apps.ginlo.fragment.backup.BackupConfigFragment

@Suppress("unused")
@Module
abstract class FragmentModule {
    @ContributesAndroidInjector
    abstract fun contributeBackupConfigFragment(): BackupConfigFragment
}
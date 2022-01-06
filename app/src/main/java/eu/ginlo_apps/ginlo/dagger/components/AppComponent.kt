// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.dagger.components

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.dagger.modules.ActivityModule
import eu.ginlo_apps.ginlo.dagger.modules.ApplicationModule
import eu.ginlo_apps.ginlo.dagger.modules.ExceptionHandlerModule
import eu.ginlo_apps.ginlo.dagger.modules.FragmentModule
import eu.ginlo_apps.ginlo.dagger.modules.ViewModelModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        ActivityModule::class,
        FragmentModule::class,
        ApplicationModule::class,
        ExceptionHandlerModule::class,
        AndroidInjectionModule::class,
        ViewModelModule::class]
)
interface AppComponent {
    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: SimsMeApplication): Builder

        fun build(): AppComponent
    }

    fun inject(simsMeApplication: SimsMeApplication)
}
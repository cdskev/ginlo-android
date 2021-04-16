// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.device

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.adapter.DeviceListAdapter
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.backend.DeviceModel
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import kotlinx.android.synthetic.main.activity_devices_overview.devices_overview_list
import kotlinx.android.synthetic.main.activity_devices_overview.devices_overview_new_btn
import javax.inject.Inject

class DevicesOverviewActivity : NewBaseActivity(), DeviceListAdapter.OnDeviceItemClickListener {
    private var adapter: DeviceListAdapter? = null
    private val devices = mutableListOf<DeviceModel>()

    @Inject
    internal lateinit var appConnectivity: AppConnectivity

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        devices_overview_list.setHasFixedSize(true)
        devices_overview_list.layoutManager = LinearLayoutManager(this)

        if (simsMeApplication.deviceController.ownDevice == null) {
            LogUtil.w(javaClass.simpleName, "No own device")
            finish()
            return
        }

        val ownDeviceGuid = simsMeApplication.deviceController.ownDevice?.guid.orEmpty()
        adapter = DeviceListAdapter(devices, ownDeviceGuid, this)
        devices_overview_list.adapter = adapter
    }

    private fun updateNewDeviceButtonBehavior() {
        try {
            devices_overview_new_btn.isEnabled =
                devices.size != simsMeApplication.preferencesController.getDeviceMaxClients()
        } catch (le: LocalizedException) {
            LogUtil.e(this.javaClass.name, le.identifier, le)
        }
    }

    override fun onStart() {
        super.onStart()

        if (!appConnectivity.isConnected()) {
            Toast.makeText(
                this, R.string.backendservice_internet_connectionFailed,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            showIdleDialog()
            simsMeApplication.deviceController.loadDevicesFromBackend(object :
                GenericActionListener<List<DeviceModel>> {
                override fun onSuccess(result: List<DeviceModel>?) {
                    dismissIdleDialog()
                    if (result == null) return
                    devices.clear()
                    devices.addAll(result)
                    adapter?.notifyDataSetChanged()

                    updateNewDeviceButtonBehavior()
                }

                override fun onFail(message: String?, errorIdent: String?) {
                    dismissIdleDialog()
                    DialogBuilderUtil.buildErrorDialog(
                        this@DevicesOverviewActivity,
                        getString(R.string.devices_overview_load_devices_error)
                    ).show()
                }
            })
        } catch (e: LocalizedException) {
            dismissIdleDialog()
            DialogBuilderUtil.buildErrorDialog(this, getString(R.string.devices_overview_load_devices_error)).show()
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_devices_overview
    }

    override fun onResumeActivity() {
        updateNewDeviceButtonBehavior()
    }

    override fun onDeviceItemClick(position: Int) {
        if (position >= devices.size) return

        val intent = Intent(this, DeviceDetailActivity::class.java)
        intent.putExtra(DeviceDetailActivity.DEVICE_MODEL, devices[position])
        startActivity(intent)
    }

    fun handleNewDeviceClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, DeviceCoupleNewActivity::class.java))
    }
}

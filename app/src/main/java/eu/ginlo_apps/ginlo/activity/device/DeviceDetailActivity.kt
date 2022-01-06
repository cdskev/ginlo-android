// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.device

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import eu.ginlo_apps.ginlo.LoginActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.controller.DeviceController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.model.backend.DeviceModel
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import kotlinx.android.synthetic.main.activity_device_detail.device_detail_apply_changes_btn
import kotlinx.android.synthetic.main.activity_device_detail.device_detail_device_icon_iv
import kotlinx.android.synthetic.main.activity_device_detail.device_detail_device_name_tv
import kotlinx.android.synthetic.main.activity_device_detail.device_detail_info_tv
import kotlinx.android.synthetic.main.activity_device_detail.device_detail_version_tv

class DeviceDetailActivity : NewBaseActivity() {
    companion object {
        const val DEVICE_MODEL = "device_model"
        private const val REQUEST_DELETE_DEVICE = 0x1f
    }

    private val deviceController: DeviceController by lazy { simsMeApplication.deviceController }
    private var deviceGuid: String? = null
    private var deleteDeviceListener: GenericActionListener<Void>? = null

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        val model = intent.getSerializableExtra(DEVICE_MODEL) as? DeviceModel

        if (model == null || deviceController.ownDevice == null) {
            finish()
            return
        }
        val name = model.name ?: getString(R.string.device_unknown_device_name)
        title = name
        device_detail_device_name_tv.setText(name)

        deviceGuid = model.guid

        if (model.guid == deviceController.ownDevice?.guid) {
            device_detail_info_tv.setText(R.string.device_own_device)
            device_detail_info_tv.setTextColor(ColorUtil.getInstance().getMediumColor(simsMeApplication))
            val deleteBtn = findViewById<View>(R.id.device_detail_delete_btn)
            deleteBtn.visibility = View.GONE
        } else {
            device_detail_info_tv.text = model.lastOnlineDateString
            device_detail_info_tv.setTextColor(ColorUtil.getInstance().getNamedColor("actionSecondary", simsMeApplication))
        }

        device_detail_version_tv.text = model.versionString
        device_detail_device_icon_iv.setImageResource(model.deviceImageRessource)

        val t = findViewById<TextView>(R.id.device_detail_device_type)
        t.setText(R.string.device_couple_perm_device)

        val desc = findViewById<TextView>(R.id.device_detail_device_type_descr)
        desc.setText(R.string.device_couple_perm_descr)

        device_detail_device_name_tv.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                val currentText = s.toString()
                if (currentText.isBlank()) {
                    device_detail_apply_changes_btn.isEnabled = false
                }

                device_detail_apply_changes_btn.isEnabled = currentText != name
            }
        })
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_device_detail
    }

    override fun onResumeActivity() {
    }

    fun handleDeleteDevice(@Suppress("UNUSED_PARAMETER") v: View?) {
        if (deviceGuid.isNullOrBlank()) {
            return
        }
        deleteDeviceListener = object : GenericActionListener<Void> {
            override fun onSuccess(nothing: Void?) {
                this@DeviceDetailActivity.finish()
            }

            override fun onFail(message: String?, errorIdent: String?) {
                dismissIdleDialog()
                DialogBuilderUtil.buildErrorDialog(
                    this@DeviceDetailActivity,
                    getString(R.string.device_delete_device_error)
                ).show()
            }
        }

        val deleteListener = DialogInterface.OnClickListener { _, _ ->
            val intent = Intent(this@DeviceDetailActivity, RuntimeConfig.getClassUtil().loginActivityClass)
            intent.putExtra(LoginActivity.EXTRA_MODE, LoginActivity.EXTRA_MODE_CHECK_PW)
            startActivityForResult(intent, REQUEST_DELETE_DEVICE)
        }

        val cancelListener = DialogInterface.OnClickListener { _, _ ->
        }

        DialogBuilderUtil.buildResponseDialog(
            this,
            getString(R.string.device_delete_device),
            getString(R.string.std_warning),
            getString(R.string.media_delete_item),
            getString(R.string.std_cancel),
            deleteListener,
            cancelListener
        ).show()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (REQUEST_DELETE_DEVICE == requestCode && resultCode == Activity.RESULT_OK) {
            deleteDevice()
        }
    }

    private fun deleteDevice() {
        try {
            showIdleDialog()
            deviceController.deleteDeviceFromBackend(deviceGuid, deleteDeviceListener)
        } catch (e: LocalizedException) {
            dismissIdleDialog()
            DialogBuilderUtil.buildErrorDialog(
                this@DeviceDetailActivity,
                getString(R.string.device_delete_device_error)
            ).show()
        }
    }

    fun handleApplyChanges(@Suppress("UNUSED_PARAMETER") v: View?) {
        val deviceName = device_detail_device_name_tv.text?.toString() ?: return

        try {
            showIdleDialog()
            deviceController.changeDeviceNameAtBackend(
                deviceGuid,
                deviceName,
                object : GenericActionListener<Void> {
                    override fun onSuccess(nothing: Void?) {
                        val ownDevice = simsMeApplication.deviceController.ownDevice
                        if (ownDevice != null && deviceGuid == ownDevice.guid) {
                            simsMeApplication.preferencesController.setHasSetOwnDeviceName()
                        }
                        this@DeviceDetailActivity.finish()
                    }

                    override fun onFail(message: String?, errorIdent: String?) {
                        dismissIdleDialog()
                        DialogBuilderUtil.buildErrorDialog(
                            this@DeviceDetailActivity,
                            getString(R.string.device_apply_changes_error)
                        ).show()
                    }
                })
        } catch (e: LocalizedException) {
            dismissIdleDialog()
            DialogBuilderUtil.buildErrorDialog(
                this@DeviceDetailActivity,
                getString(R.string.device_apply_changes_error)
            ).show()
        }
    }
}

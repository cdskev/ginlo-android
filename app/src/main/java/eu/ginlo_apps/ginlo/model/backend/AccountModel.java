// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import com.google.gson.JsonObject;

import eu.ginlo_apps.ginlo.model.backend.BaseModel;

public class AccountModel
        extends BaseModel {

    public String publicKey;
    public String phone;
    public String email;
    //Backup
    public String privateKey;
    public String nickname;
    public String accountID;

    public String profileKey;

    public String backupPasstoken;

    public JsonObject accountBackupJO;

    public AccountModel() {
        guid = "";
        publicKey = "";
        phone = "";
        email = "";
        privateKey = "";
        nickname = "";
        accountID = "";
    }
}
